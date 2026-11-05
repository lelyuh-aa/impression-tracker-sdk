package dev.leliukh.impressiontracker.internal.platform.config

import android.content.Context
import android.content.pm.PackageManager
import dev.leliukh.impressiontracker.DispatchPolicy
import dev.leliukh.impressiontracker.ImpressionTrackerConfig
import dev.leliukh.impressiontracker.internal.TrackerLog

private const val META_ENDPOINT = "dev.leliukh.impressiontracker.ENDPOINT"
private const val META_DISPATCH_POLICY = "dev.leliukh.impressiontracker.DISPATCH_POLICY"

/**
 * Reads the SDK's settings from the host application's manifest.
 *
 * Lets an application configure the SDK without writing any code, which matters
 * because the SDK starts on its own and may never be called by the host.
 *
 * @param context Any context of the host application.
 */
internal class ManifestConfigReader(
    private val context: Context,
) {

    /**
     * Reads the settings.
     *
     * @return Values found in the manifest, with [ImpressionTrackerConfig.DEFAULT]
     *   filling in whatever is missing or unreadable.
     */
    fun read(): ImpressionTrackerConfig {
        val default = ImpressionTrackerConfig.DEFAULT
        val metaData = try {
            @Suppress("DEPRECATION")
            context.packageManager
                .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
                .metaData
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } ?: return default

        return ImpressionTrackerConfig(
            endpoint = metaData.getString(META_ENDPOINT) ?: default.endpoint,
            dispatchPolicy = dispatchPolicyOf(metaData.getString(META_DISPATCH_POLICY)) ?: default.dispatchPolicy,
        )
    }

    /**
     * Turns a manifest value into a policy.
     *
     * @param name Value declared in the manifest, if any.
     * @return The matching policy, or `null` when nothing was declared or the
     *   value does not name one.
     */
    private fun dispatchPolicyOf(name: String?): DispatchPolicy? {
        if (name == null) return null
        val policy = DispatchPolicy.entries.firstOrNull { it.name == name }
        if (policy == null) {
            TrackerLog.warn("Unknown dispatch policy in the manifest: $name")
        }
        return policy
    }
}
