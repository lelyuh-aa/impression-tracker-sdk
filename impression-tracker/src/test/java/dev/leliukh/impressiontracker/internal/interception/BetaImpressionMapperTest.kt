package dev.leliukh.impressiontracker.internal.interception

import dev.leliukh.impressiontracker.internal.domain.model.ImpressionEvent
import dev.leliukh.impressiontracker.internal.domain.model.ImpressionSource
import dev.leliukh.impressiontracker.internal.domain.port.Clock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BetaImpressionMapperTest {

    private val clock = Clock { 1_700_000_000_000L }
    private val mapper = BetaImpressionMapper(clock)

    @Test
    fun `maps the network's own field names onto the shared model`() {
        val event = mapper.map(BetaPayload(creative = "creative-7", network = "SMAATO"))

        assertEquals(
            ImpressionEvent(
                id = "creative-7",
                bidderName = "SMAATO",
                source = ImpressionSource.BETA,
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
    fun `returns null when a getter does not yield a string`() {
        assertNull(mapper.map(Any()))
    }
}
