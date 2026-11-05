package dev.leliukh.impressiontracker.internal.interception

import dev.leliukh.impressiontracker.internal.domain.model.ImpressionEvent
import dev.leliukh.impressiontracker.internal.domain.model.ImpressionSource
import dev.leliukh.impressiontracker.internal.domain.port.Clock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlphaImpressionMapperTest {

    private val mapper = AlphaImpressionMapper(Clock { 1_700_000_000_000L })

    @Test
    fun `maps the network's own field names onto the shared model`() {
        val event = mapper.map(AlphaPayload(ad = "ad-42", bidder = "ACME"))

        assertEquals(
            ImpressionEvent(
                id = "ad-42",
                bidderName = "ACME",
                source = ImpressionSource.ALPHA,
                occurredAt = 1_700_000_000_000L,
            ),
            event,
        )
    }

    @Test
    fun `returns null for a payload without the expected getters`() {
        assertNull(mapper.map(ForeignPayload()))
    }

    @Test
    fun `does not confuse the two networks' payloads`() {
        assertNull(mapper.map(BetaPayload(creative = "creative-7", network = "SMAATO")))
    }
}
