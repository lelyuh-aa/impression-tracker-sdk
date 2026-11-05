package dev.leliukh.impressiontracker.internal.platform.startup

import android.app.Application
import dev.leliukh.impressiontracker.internal.TrackerLog
import dev.leliukh.impressiontracker.internal.interception.AdNetworkHook
import dev.leliukh.impressiontracker.internal.platform.di.TrackerDiGraph
import dev.leliukh.impressiontracker.internal.platform.scheduling.FlushScheduler

private const val LOG_HOST_READY = "Host application ready"

/**
 * Starts the SDK once the host application has finished its own `onCreate`.
 *
 * Holds the startup sequence, so that [TrackerDiGraph] stays a plain registry of
 * collaborators with no behaviour of its own.
 *
 * @param application Host application, needed to observe activity lifecycles.
 * @param diGraph Supplies the collaborators to start.
 * @param flushScheduler Arranges for queued impressions to be delivered daily.
 */
internal class TrackerBootstrap(
    private val application: Application,
    private val diGraph: TrackerDiGraph,
    private val flushScheduler: FlushScheduler,
) {

    /**
     * Installs the ad network hooks and arranges for them to stay installed.
     *
     * The daily delivery is scheduled under both dispatch policies. For
     * `DAILY_BATCH` it is the only delivery path; for `IMMEDIATE` it is the
     * safety net, because a failed delivery is otherwise only retried on the
     * next impression, which may never come.
     *
     * Runs on the host application's main thread, which is why nothing is
     * allowed to escape: an SDK that fails to start is a lost feature, an SDK
     * that throws here takes the host application down with it.
     */
    fun start() {
        try {
            TrackerLog.debug(LOG_HOST_READY)
            diGraph.adNetworkHooks.forEach { hook ->
                if (hook.install()) {
                    TrackerLog.debug("Hooked ${hook.source.networkName}")
                }
            }
            watchActivities()
            flushScheduler.schedule()
        } catch (failure: Throwable) {
            TrackerLog.warn("Could not start, impressions will not be tracked", failure)
        }
    }

    /**
     * One network stores its listener asynchronously and lets the host replace
     * it at any time, so the hooks are re-checked whenever an activity appears.
     */
    private fun watchActivities() {
        application.registerActivityLifecycleCallbacks(
            ActivityLifecycleTrigger { diGraph.adNetworkHooks.forEach(AdNetworkHook::ensureAttached) },
        )
    }
}
