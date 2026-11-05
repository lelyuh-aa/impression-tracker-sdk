package dev.leliukh.impressiontracker.internal.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ImpressionEventTest {

    @Test
    fun `log message matches the required format`() {
        val event = ImpressionEvent(
            id = "ad-42",
            bidderName = "META",
            source = ImpressionSource.ALPHA,
            occurredAt = 0L,
        )

        assertEquals(
            "Intercepted impression event:\n- ID: ad-42\n- Bidder Name: META",
            event.toLogMessage(),
        )
    }

    @Test
    fun `log message keeps empty values rather than hiding them`() {
        val event = ImpressionEvent(
            id = "",
            bidderName = "",
            source = ImpressionSource.BETA,
            occurredAt = 0L,
        )

        assertEquals(
            "Intercepted impression event:\n- ID: \n- Bidder Name: ",
            event.toLogMessage(),
        )
    }
}
