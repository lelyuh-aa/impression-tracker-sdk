package dev.leliukh.impressiontracker.internal.domain.port

import dev.leliukh.impressiontracker.internal.domain.model.ImpressionEvent
import dev.leliukh.impressiontracker.internal.domain.model.QueuedImpression

/**
 * Durable store of impressions that still have to reach the backend.
 *
 * Events outlive the process: an impression recorded while the device is
 * offline must still be sent after the next launch.
 */
internal interface ImpressionQueue {

    /**
     * Stores one impression.
     *
     * @param event Impression to keep until it has been delivered.
     */
    fun enqueue(event: ImpressionEvent)

    /**
     * Reads the oldest entries without removing them.
     *
     * @param limit Largest number of entries to return.
     * @return Entries in the order they were recorded.
     */
    fun peek(limit: Int): List<QueuedImpression>

    /**
     * Deletes entries that have been delivered.
     *
     * @param ids Identifiers taken from [QueuedImpression.id].
     */
    fun remove(ids: List<Long>)
}
