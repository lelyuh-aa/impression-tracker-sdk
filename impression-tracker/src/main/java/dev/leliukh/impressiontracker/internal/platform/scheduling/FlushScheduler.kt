package dev.leliukh.impressiontracker.internal.platform.scheduling

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import dev.leliukh.impressiontracker.internal.TrackerLog
import java.util.concurrent.TimeUnit

/**
 * Identifier of the daily job.
 *
 * Job identifiers are unique per application, not per library, so this one is
 * deliberately an unusual value. A host that happens to use the same number
 * would replace this job with its own.
 */
internal const val DAILY_FLUSH_JOB_ID = 0x1D7A

private val FLUSH_INTERVAL_MILLIS = TimeUnit.DAYS.toMillis(1)

/**
 * Arranges for [DailyFlushJobService] to run once a day.
 *
 * The job is not persisted across reboots. Persisting it would oblige the host
 * application to hold `RECEIVE_BOOT_COMPLETED`, and it buys nothing: the SDK
 * starts with the process and schedules the job again.
 *
 * @param context Any context of the host application.
 */
internal class FlushScheduler(
    private val context: Context,
) {

    /**
     * Schedules the job, unless it is already waiting to run.
     *
     * The second half of that sentence carries the weight: scheduling a
     * periodic job that already exists restarts its period. Since the SDK
     * starts with every process, scheduling unconditionally would mean that an
     * application opened more than once a day never reaches the end of a single
     * period, and the job would never run at all.
     *
     * A freshly scheduled job does not run at once either; the platform picks a
     * moment within the first period.
     */
    fun schedule() {
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler
        if (scheduler == null) {
            TrackerLog.warn("No JobScheduler on this device, impressions are sent on the next one")
            return
        }
        if (scheduler.allPendingJobs.any { it.id == DAILY_FLUSH_JOB_ID }) return

        val job = JobInfo.Builder(
            DAILY_FLUSH_JOB_ID,
            ComponentName(context, DailyFlushJobService::class.java),
        )
            .setPeriodic(FLUSH_INTERVAL_MILLIS)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .build()

        if (scheduler.schedule(job) != JobScheduler.RESULT_SUCCESS) {
            TrackerLog.warn("Could not schedule the daily flush")
        }
    }
}
