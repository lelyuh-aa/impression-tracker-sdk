package dev.leliukh.impressiontracker.internal

import android.util.Log

/**
 * Diagnostic logging for the SDK itself.
 *
 * Deliberately separate from impression logging: this is troubleshooting output
 * for whoever integrates the SDK, while impression events are a product feature
 * with a fixed, specified format.
 */
internal object TrackerLog {

    /** Tag every line written by the SDK carries. */
    const val TAG: String = "ImpressionTracker"

    /**
     * Writes a message useful while integrating or debugging the SDK.
     *
     * @param message Text to write.
     */
    fun debug(message: String) {
        Log.d(TAG, message)
    }

    /**
     * Writes a message about a condition the SDK recovered from.
     *
     * @param message Text to write.
     * @param error Cause to append, when one is available.
     */
    fun warn(message: String, error: Throwable? = null) {
        Log.w(TAG, message, error)
    }
}
