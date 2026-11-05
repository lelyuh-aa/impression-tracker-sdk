package dev.leliukh.impressiontracker.internal.platform.process

import android.app.Application
import android.content.Context
import android.os.Build
import dev.leliukh.impressiontracker.internal.TrackerLog
import java.io.File

private const val CMDLINE = "/proc/self/cmdline"

/** Tells which process of the host application the SDK is running in. */
internal object CurrentProcess {

    /**
     * Decides whether this is the application's main process.
     *
     * Android names the main process after the application id and gives every
     * extra process a `:suffix`. When the name cannot be read the answer is
     * `true`, so that a single process app never ends up tracking nothing.
     *
     * @param context Any context of the host application.
     * @return `true` when the SDK should run here.
     */
    fun isMain(context: Context): Boolean {
        val name = name()
        if (name == null) {
            TrackerLog.warn("Could not read the process name, assuming the main process")
            return true
        }
        return name == context.packageName
    }

    private fun name(): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            readCmdline()
        }

    private fun readCmdline(): String? =
        try {
            File(CMDLINE).readText().trim { it <= ' ' }.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
}
