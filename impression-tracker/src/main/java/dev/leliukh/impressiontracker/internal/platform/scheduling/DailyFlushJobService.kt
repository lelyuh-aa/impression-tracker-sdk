package dev.leliukh.impressiontracker.internal.platform.scheduling

import android.app.job.JobParameters
import android.app.job.JobService
import dev.leliukh.impressiontracker.internal.TrackerLog
import dev.leliukh.impressiontracker.internal.platform.di.TrackerDiGraphHolder

/**
 * Delivers whatever the queue still holds, once a day.
 *
 * Serves both dispatch policies: it is the only delivery path under
 * `DAILY_BATCH`, and the safety net under `IMMEDIATE` for impressions recorded
 * while the device had no network.
 */
internal class DailyFlushJobService : JobService() {

    /**
     * Callbacks arrive on the main thread, so the delivery is handed to the
     * SDK's own thread and the job is only declared finished once that work is
     * done.
     *
     * @param params Parameters of the job being started.
     * @return `true` while the delivery continues on another thread.
     */
    override fun onStartJob(params: JobParameters?): Boolean {
        val diGraph = TrackerDiGraphHolder.current()
        if (diGraph == null) {
            TrackerLog.warn("Daily flush ran without an initialised SDK, skipping")
            return false
        }

        diGraph.backgroundExecutor.execute {
            diGraph.flushPendingImpressions()
            jobFinished(params, false)
        }
        return true
    }

    /**
     * Called when the system takes the job away, for instance because its
     * network requirement stopped being met.
     *
     * @param params Parameters of the job being stopped.
     * @return `true` so the job is rescheduled and the impressions still queued
     *   are delivered later.
     */
    override fun onStopJob(params: JobParameters?): Boolean = true
}
