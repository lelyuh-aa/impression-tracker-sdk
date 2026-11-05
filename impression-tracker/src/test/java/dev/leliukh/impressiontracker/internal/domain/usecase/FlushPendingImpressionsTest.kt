package dev.leliukh.impressiontracker.internal.domain.usecase

import dev.leliukh.impressiontracker.internal.domain.model.ImpressionEvent
import dev.leliukh.impressiontracker.internal.domain.model.ImpressionSource
import dev.leliukh.impressiontracker.internal.domain.model.QueuedImpression
import dev.leliukh.impressiontracker.internal.domain.port.ImpressionQueue
import dev.leliukh.impressiontracker.internal.domain.port.ImpressionTransport
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Test

class FlushPendingImpressionsTest {

    private val queue = mockk<ImpressionQueue>(relaxUnitFun = true)
    private val transport = mockk<ImpressionTransport>()
    private val flush = FlushPendingImpressions(queue, transport)

    @Test
    fun `sends the queued impressions and deletes what was accepted`() {
        val batch = listOf(queued(1L, "ad-1"), queued(2L, "ad-2"))
        every { queue.peek(any()) } returnsMany listOf(batch, emptyList())
        every { transport.send(any()) } returns true

        flush()

        verifyOrder {
            transport.send(batch.map { it.event })
            queue.remove(listOf(1L, 2L))
        }
    }

    @Test
    fun `keeps the impressions when the backend does not accept them`() {
        every { queue.peek(any()) } returns listOf(queued(1L, "ad-1"))
        every { transport.send(any()) } returns false

        flush()

        verify(exactly = 0) { queue.remove(any()) }
    }

    @Test
    fun `does not call the backend when there is nothing to send`() {
        every { queue.peek(any()) } returns emptyList()

        flush()

        verify(exactly = 0) { transport.send(any()) }
    }

    @Test
    fun `keeps sending while the queue still has batches`() {
        every { queue.peek(any()) } returnsMany listOf(
            listOf(queued(1L, "ad-1")),
            listOf(queued(2L, "ad-2")),
            emptyList(),
        )
        every { transport.send(any()) } returns true

        flush()

        verify(exactly = 2) { transport.send(any()) }
        verify(exactly = 1) { queue.remove(listOf(1L)) }
        verify(exactly = 1) { queue.remove(listOf(2L)) }
    }

    @Test
    fun `stops instead of looping forever when delivered entries stay in the queue`() {
        every { queue.peek(any()) } returns listOf(queued(1L, "ad-1"))
        every { transport.send(any()) } returns true

        flush()

        verify(atMost = 20) { transport.send(any()) }
    }

    private fun queued(queueId: Long, adId: String) = QueuedImpression(
        queueId = queueId,
        event = ImpressionEvent(
            id = adId,
            bidderName = "ACME",
            source = ImpressionSource.ALPHA,
            occurredAt = 1_000L,
        ),
    )
}
