package dev.leliukh.impressiontracker.internal.platform.startup

import android.app.Activity
import android.app.Application
import dev.leliukh.impressiontracker.internal.domain.model.ImpressionSource
import dev.leliukh.impressiontracker.internal.interception.AdNetworkHook
import dev.leliukh.impressiontracker.internal.platform.di.TrackerDiGraph
import dev.leliukh.impressiontracker.internal.platform.scheduling.FlushScheduler
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Test

class TrackerBootstrapTest {

    private val attachedHook = mockk<AdNetworkHook>(relaxUnitFun = true) {
        every { source } returns ImpressionSource.ALPHA
        every { install() } returns true
    }
    private val absentHook = mockk<AdNetworkHook>(relaxUnitFun = true) {
        every { source } returns ImpressionSource.BETA
        every { install() } returns false
    }

    private val application = mockk<Application>(relaxUnitFun = true)
    private val diGraph = mockk<TrackerDiGraph> {
        every { adNetworkHooks } returns listOf(attachedHook, absentHook)
    }

    private val flushScheduler = mockk<FlushScheduler>(relaxUnitFun = true)

    private val bootstrap = TrackerBootstrap(application, diGraph, flushScheduler)

    @Test
    fun `installs every hook, including ones that cannot attach`() {
        bootstrap.start()

        verify(exactly = 1) { attachedHook.install() }
        verify(exactly = 1) { absentHook.install() }
    }

    @Test
    fun `observes activities so hooks can be restored`() {
        bootstrap.start()

        verify(exactly = 1) { application.registerActivityLifecycleCallbacks(any()) }
    }

    @Test
    fun `re-attaches every hook when an activity appears`() {
        val registered = slot<Application.ActivityLifecycleCallbacks>()
        every { application.registerActivityLifecycleCallbacks(capture(registered)) } returns Unit

        bootstrap.start()
        registered.captured.onActivityResumed(mockk())

        verify(exactly = 1) { attachedHook.ensureAttached() }
        verify(exactly = 1) { absentHook.ensureAttached() }
    }

    @Test
    fun `schedules the daily delivery of queued impressions`() {
        bootstrap.start()

        verify(exactly = 1) { flushScheduler.schedule() }
    }

    @Test
    fun `does not let a failure escape into the host application`() {
        every { flushScheduler.schedule() } throws IllegalStateException("boom")

        bootstrap.start()

        verify(exactly = 1) { attachedHook.install() }
    }

    @Test
    fun `does not touch the hooks before the host application is ready`() {
        verify(exactly = 0) { attachedHook.install() }
        verify(exactly = 0) { attachedHook.ensureAttached() }
    }
}
