package dev.leliukh.impressiontracker.internal.platform.scheduling

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/** Uses the device's real `JobScheduler`, which has no stand-in on the JVM. */
@RunWith(AndroidJUnit4::class)
class FlushSchedulerTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler

    @Before
    fun setUp() {
        jobScheduler.cancel(DAILY_FLUSH_JOB_ID)
    }

    @After
    fun tearDown() {
        jobScheduler.cancel(DAILY_FLUSH_JOB_ID)
    }

    @Test
    fun schedulesAJobThatRepeatsOnceADay() {
        FlushScheduler(context).schedule()

        val job = jobScheduler.allPendingJobs.first { it.id == DAILY_FLUSH_JOB_ID }
        assertTrue(job.isPeriodic)
        assertEquals(TimeUnit.DAYS.toMillis(1), job.intervalMillis)
    }

    @Test
    fun waitsForNetworkBeforeRunning() {
        FlushScheduler(context).schedule()

        val job = jobScheduler.allPendingJobs.first { it.id == DAILY_FLUSH_JOB_ID }
        assertEquals(JobInfo.NETWORK_TYPE_ANY, job.networkType)
    }

    @Test
    fun doesNotRestartThePeriodWhenTheJobIsAlreadyWaiting() {
        FlushScheduler(context).schedule()
        FlushScheduler(context).schedule()

        assertEquals(1, jobScheduler.allPendingJobs.count { it.id == DAILY_FLUSH_JOB_ID })
    }

    @Test
    fun schedulesTheJobAgainAfterItWasCancelled() {
        FlushScheduler(context).schedule()
        jobScheduler.cancel(DAILY_FLUSH_JOB_ID)

        FlushScheduler(context).schedule()

        assertEquals(1, jobScheduler.allPendingJobs.count { it.id == DAILY_FLUSH_JOB_ID })
    }
}
