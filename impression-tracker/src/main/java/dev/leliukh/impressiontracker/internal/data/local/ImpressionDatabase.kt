package dev.leliukh.impressiontracker.internal.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

internal const val TABLE_IMPRESSIONS = "impressions"
internal const val COLUMN_ID = "_id"
internal const val COLUMN_AD_ID = "ad_id"
internal const val COLUMN_BIDDER_NAME = "bidder_name"
internal const val COLUMN_SOURCE = "source"
internal const val COLUMN_OCCURRED_AT = "occurred_at"

private const val DATABASE_NAME = "impression_tracker.db"
private const val DATABASE_VERSION = 1

private const val CREATE_TABLE = """
    CREATE TABLE $TABLE_IMPRESSIONS (
        $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
        $COLUMN_AD_ID TEXT NOT NULL,
        $COLUMN_BIDDER_NAME TEXT NOT NULL,
        $COLUMN_SOURCE TEXT NOT NULL,
        $COLUMN_OCCURRED_AT INTEGER NOT NULL
    )
"""

/**
 * Database holding impressions that have not reached the backend yet.
 *
 * The file lives in the host application's private storage, so nothing else on
 * the device can read it.
 *
 * @param context Used to locate that private storage.
 */
internal class ImpressionDatabase(
    context: Context,
) : SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_TABLE)
    }

    /**
     * Recreates the table instead of migrating it. Queued impressions are
     * disposable: at most a day of telemetry is lost, which is cheaper and far
     * less risky than writing and maintaining a migration for them.
     *
     * @param db Database being upgraded.
     * @param oldVersion Version currently on disk.
     * @param newVersion Version this build expects.
     */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_IMPRESSIONS")
        onCreate(db)
    }
}
