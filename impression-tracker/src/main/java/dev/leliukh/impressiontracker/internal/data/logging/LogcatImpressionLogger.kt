package dev.leliukh.impressiontracker.internal.data.logging

import android.util.Log
import dev.leliukh.impressiontracker.internal.TrackerLog
import dev.leliukh.impressiontracker.internal.domain.model.ImpressionEvent
import dev.leliukh.impressiontracker.internal.domain.model.toLogMessage
import dev.leliukh.impressiontracker.internal.domain.port.ImpressionLogger

/** Writes impressions to logcat. */
internal class LogcatImpressionLogger : ImpressionLogger {

    override fun log(event: ImpressionEvent) {
        Log.i(TrackerLog.TAG, event.toLogMessage())
    }
}
