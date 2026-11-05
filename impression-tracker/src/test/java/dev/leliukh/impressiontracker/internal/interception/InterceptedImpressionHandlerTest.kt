package dev.leliukh.impressiontracker.internal.interception

import dev.leliukh.impressiontracker.internal.domain.model.ImpressionEvent
import dev.leliukh.impressiontracker.internal.domain.model.ImpressionSource
import dev.leliukh.impressiontracker.internal.domain.usecase.TrackImpression
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class InterceptedImpressionHandlerTest {

    private val event = ImpressionEvent(
        id = "ad-1",
        bidderName = "ACME",
        source = ImpressionSource.ALPHA,
        occurredAt = 1_000L,
    )
    private val payload = Any()

    private val mapper = mockk<ImpressionMapper>()
    private val trackImpression = mockk<TrackImpression>(relaxUnitFun = true)
    private val handler = InterceptedImpressionHandler(ImpressionSource.ALPHA, mapper, trackImpression)

    @Test
    fun `tracks a payload the mapper understands`() {
        every { mapper.map(payload) } returns event

        handler.handle(payload)

        verify(exactly = 1) { trackImpression.invoke(event) }
        confirmVerified(trackImpression)
    }

    @Test
    fun `ignores a missing payload without touching the mapper`() {
        handler.handle(null)

        confirmVerified(mapper, trackImpression)
    }

    @Test
    fun `does not track a payload the mapper rejects`() {
        every { mapper.map(payload) } returns null

        handler.handle(payload)

        confirmVerified(trackImpression)
    }

    @Test
    fun `swallows a failure so it never reaches the ad network`() {
        every { mapper.map(payload) } throws IllegalStateException("boom")

        handler.handle(payload)

        confirmVerified(trackImpression)
    }
}
