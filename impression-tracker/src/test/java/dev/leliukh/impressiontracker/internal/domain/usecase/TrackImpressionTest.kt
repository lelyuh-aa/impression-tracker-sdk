package dev.leliukh.impressiontracker.internal.domain.usecase

import dev.leliukh.impressiontracker.DispatchPolicy
import dev.leliukh.impressiontracker.internal.domain.model.ImpressionEvent
import dev.leliukh.impressiontracker.internal.domain.model.ImpressionSource
import dev.leliukh.impressiontracker.internal.domain.port.ImpressionLogger
import dev.leliukh.impressiontracker.internal.domain.port.ImpressionQueue
import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Test
import java.util.concurrent.Executor

class TrackImpressionTest {

    private val logger = mockk<ImpressionLogger>(relaxUnitFun = true)
    private val queue = mockk<ImpressionQueue>(relaxUnitFun = true)
    private val flush = mockk<FlushPendingImpressions>(relaxUnitFun = true)

    /** Runs the task immediately, so the test observes the finished result. */
    private val immediateExecutor = Executor { task -> task.run() }

    private fun trackImpression(
        policy: DispatchPolicy = DispatchPolicy.IMMEDIATE,
        executor: Executor = immediateExecutor,
    ) = TrackImpression(logger, queue, flush, { policy }, executor)

    @Test
    fun `logs the impression, stores it and sends it right away`() {
        val event = impression("ad-1")

        trackImpression(policy = DispatchPolicy.IMMEDIATE).invoke(event)

        verifyOrder {
            logger.log(event)
            queue.enqueue(event)
            flush.invoke()
        }
        confirmVerified(logger, queue, flush)
    }

    @Test
    fun `stores without sending when impressions are batched daily`() {
        val event = impression("ad-1")

        trackImpression(policy = DispatchPolicy.DAILY_BATCH).invoke(event)

        verify(exactly = 1) { logger.log(event) }
        verify(exactly = 1) { queue.enqueue(event) }
        verify(exactly = 0) { flush.invoke() }
    }

    @Test
    fun `handles every impression, including repeats of the same ad`() {
        val first = impression("ad-1")
        val second = impression("ad-2")
        val track = trackImpression()

        track(first)
        track(first)
        track(second)

        verify(exactly = 2) { queue.enqueue(first) }
        verify(exactly = 1) { queue.enqueue(second) }
    }

    @Test
    fun `logs on the calling thread but stores through the executor`() {
        val event = impression("ad-1")
        val pending = mutableListOf<Runnable>()

        trackImpression(executor = { task -> pending += task }).invoke(event)

        verify(exactly = 1) { logger.log(event) }
        verify(exactly = 0) { queue.enqueue(any()) }

        pending.forEach(Runnable::run)
        verify(exactly = 1) { queue.enqueue(event) }
    }

    private fun impression(id: String) = ImpressionEvent(
        id = id,
        bidderName = "META",
        source = ImpressionSource.ALPHA,
        occurredAt = 1_000L,
    )
}
