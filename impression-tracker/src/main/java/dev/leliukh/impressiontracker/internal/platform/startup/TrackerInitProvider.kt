package dev.leliukh.impressiontracker.internal.platform.startup

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import dev.leliukh.impressiontracker.ImpressionTracker
import dev.leliukh.impressiontracker.internal.TrackerLog
import dev.leliukh.impressiontracker.internal.platform.config.ManifestConfigReader
import dev.leliukh.impressiontracker.internal.platform.di.TrackerDiGraph
import dev.leliukh.impressiontracker.internal.platform.di.TrackerDiGraphHolder
import dev.leliukh.impressiontracker.internal.platform.scheduling.FlushScheduler
import dev.leliukh.impressiontracker.internal.platform.process.CurrentProcess

private const val LOG_BOOTSTRAPPING = "Bootstrapping from ContentProvider"
private const val LOG_SECONDARY_PROCESS = "Not the main process, staying idle"

/**
 * Entry point of the SDK.
 *
 * The host application cannot be changed, so it will never call an `init`
 * method. Android instantiates every `ContentProvider` declared in the merged
 * manifest while the process starts, which is what gives the SDK control.
 *
 * Providers are created before `Application.onCreate()`, which is too early to
 * hook the ad networks: the host registers its own impression listeners there
 * and would overwrite anything installed sooner. Both steps happen while the
 * main looper processes a single message, and the looper cannot start the next
 * message before the current one finishes, so a runnable posted from [onCreate]
 * is guaranteed to run after `Application.onCreate()` has returned.
 *
 * The SDK only runs in the main process. Android creates providers in every
 * process of an application, and a second instance would open the same queue
 * database a second time and compete with the first for its locks.
 *
 * This is not a real content provider: it stores nothing and answers every
 * content operation with an empty result.
 */
internal class TrackerInitProvider : ContentProvider() {

    /**
     * Runs while the host application's process is starting, so nothing is
     * allowed to escape. Failure is also not reported back: the platform would
     * read it as a broken provider, while the SDK can simply stay inactive.
     *
     * @return Always `true`, whether or not the SDK managed to start.
     */
    override fun onCreate(): Boolean {
        try {
            bootstrapSdk()
        } catch (failure: Throwable) {
            TrackerLog.warn("Could not bootstrap, impressions will not be tracked", failure)
        }
        return true
    }

    /**
     * Builds the SDK and arranges for it to start once the host application
     * has finished its own `onCreate`.
     *
     * Settings from the manifest are applied here rather than later, so that a
     * host calling `ImpressionTracker.configure` from its own `onCreate` has
     * the last word.
     */
    private fun bootstrapSdk() {
        val application = context?.applicationContext as? Application ?: return
        if (!CurrentProcess.isMain(application)) {
            TrackerLog.debug(LOG_SECONDARY_PROCESS)
            return
        }
        TrackerLog.debug(LOG_BOOTSTRAPPING)
        ImpressionTracker.configure(ManifestConfigReader(application).read())
        val diGraph = TrackerDiGraph(application.applicationContext)
        TrackerDiGraphHolder.publish(diGraph)
        val bootstrap = TrackerBootstrap(application, diGraph, FlushScheduler(application))
        Handler(Looper.getMainLooper()).post(bootstrap::start)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
