package dev.leliukh.impressiontracker.internal.platform.startup

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * Runs an action whenever an activity comes up.
 *
 * Gives the SDK a cheap, repeated moment to check that its hooks are still in
 * place, well before an ad can be shown.
 *
 * @param onActivityAppeared Action to run; must be idempotent and cheap.
 */
internal class ActivityLifecycleTrigger(
    private val onActivityAppeared: () -> Unit,
) : Application.ActivityLifecycleCallbacks {

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?): Unit = onActivityAppeared()

    override fun onActivityStarted(activity: Activity): Unit = onActivityAppeared()

    override fun onActivityResumed(activity: Activity): Unit = onActivityAppeared()

    override fun onActivityPaused(activity: Activity): Unit = Unit

    override fun onActivityStopped(activity: Activity): Unit = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle): Unit = Unit

    override fun onActivityDestroyed(activity: Activity): Unit = Unit
}
