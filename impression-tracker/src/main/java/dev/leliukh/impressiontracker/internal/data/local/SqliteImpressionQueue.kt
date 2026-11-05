package dev.leliukh.impressiontracker.internal.data.local

import android.content.ContentValues
import android.database.Cursor
import dev.leliukh.impressiontracker.internal.TrackerLog
import dev.leliukh.impressiontracker.internal.domain.model.ImpressionEvent
import dev.leliukh.impressiontracker.internal.domain.model.ImpressionSource
import dev.leliukh.impressiontracker.internal.domain.model.QueuedImpression
import dev.leliukh.impressiontracker.internal.domain.port.ImpressionQueue

/**
 * Largest number of impressions kept on disk.
 *
 * Without a ceiling a device that stays offline would grow the database without
 * end. When the queue is full the oldest entries are dropped, because recent
 * impressions are the more useful ones.
 */
private const val MAX_QUEUED_IMPRESSIONS = 1_000

private const val TRIM_TO_MAX_SIZE = """
    DELETE FROM $TABLE_IMPRESSIONS
    WHERE $COLUMN_ID NOT IN (
        SELECT $COLUMN_ID FROM $TABLE_IMPRESSIONS ORDER BY $COLUMN_ID DESC LIMIT ?
    )
"""

/**
 * [ImpressionQueue] backed by SQLite.
 *
 * @param database Supplies the readable and writable connections.
 */
internal class SqliteImpressionQueue(
    private val database: ImpressionDatabase,
) : ImpressionQueue {

    override fun enqueue(event: ImpressionEvent): Unit = guarded("store an impression", Unit) {
        val values = ContentValues().apply {
            put(COLUMN_AD_ID, event.id)
            put(COLUMN_BIDDER_NAME, event.bidderName)
            put(COLUMN_SOURCE, event.source.name)
            put(COLUMN_OCCURRED_AT, event.occurredAt)
        }
        database.writableDatabase.apply {
            insert(TABLE_IMPRESSIONS, null, values)
            execSQL(TRIM_TO_MAX_SIZE, arrayOf<Any>(MAX_QUEUED_IMPRESSIONS))
        }
    }

    override fun peek(limit: Int): List<QueuedImpression> =
        guarded("read queued impressions", emptyList()) {
            database.readableDatabase.query(
                TABLE_IMPRESSIONS,
                null,
                null,
                null,
                null,
                null,
                "$COLUMN_ID ASC",
                limit.toString(),
            ).use { cursor -> cursor.readAll() }
        }

    override fun remove(ids: List<Long>): Unit = guarded("remove delivered impressions", Unit) {
        if (ids.isEmpty()) return@guarded
        val placeholders = ids.joinToString(separator = ",") { "?" }
        database.writableDatabase.delete(
            TABLE_IMPRESSIONS,
            "$COLUMN_ID IN ($placeholders)",
            ids.map(Long::toString).toTypedArray(),
        )
    }

    /**
     * Runs a database operation, turning any failure into a logged warning.
     *
     * SQLite reports a full disk, a corrupt file and an unusable cursor as
     * different unchecked exceptions, and all of this runs on the SDK's own
     * thread, where an escaping exception would take the host application down.
     * Losing telemetry is an acceptable outcome, crashing the host is not.
     *
     * @param description What the operation was trying to do, for the log.
     * @param fallback Value to return when the operation failed.
     * @param operation The database work.
     * @return The operation's result, or [fallback].
     */
    private fun <T> guarded(description: String, fallback: T, operation: () -> T): T =
        try {
            operation()
        } catch (failure: RuntimeException) {
            TrackerLog.warn("Could not $description", failure)
            fallback
        }

    private fun Cursor.readAll(): List<QueuedImpression> {
        val idColumn = getColumnIndexOrThrow(COLUMN_ID)
        val adIdColumn = getColumnIndexOrThrow(COLUMN_AD_ID)
        val bidderNameColumn = getColumnIndexOrThrow(COLUMN_BIDDER_NAME)
        val sourceColumn = getColumnIndexOrThrow(COLUMN_SOURCE)
        val occurredAtColumn = getColumnIndexOrThrow(COLUMN_OCCURRED_AT)

        val impressions = mutableListOf<QueuedImpression>()
        while (moveToNext()) {
            val source = sourceOf(getString(sourceColumn)) ?: continue
            impressions += QueuedImpression(
                queueId = getLong(idColumn),
                event = ImpressionEvent(
                    id = getString(adIdColumn),
                    bidderName = getString(bidderNameColumn),
                    source = source,
                    occurredAt = getLong(occurredAtColumn),
                ),
            )
        }
        return impressions
    }

    /**
     * Reads a stored network name.
     *
     * @param name Value written by an earlier version of the SDK.
     * @return The matching source, or `null` if this build no longer knows it.
     */
    private fun sourceOf(name: String): ImpressionSource? =
        ImpressionSource.entries.firstOrNull { it.name == name }
}
