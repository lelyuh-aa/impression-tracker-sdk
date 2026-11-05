package dev.leliukh.impressiontracker.internal.interception

import dev.leliukh.impressiontracker.internal.domain.model.ImpressionEvent
import dev.leliukh.impressiontracker.internal.domain.model.ImpressionSource
import dev.leliukh.impressiontracker.internal.domain.port.Clock

private const val ID_GETTER = "getAdId"
private const val BIDDER_NAME_GETTER = "getBidderName"

/**
 * Reads `AlphaAds` impressions, which report an ad id and a bidder name.
 *
 * @param clock Supplies the interception timestamp.
 */
internal class AlphaImpressionMapper(
    private val clock: Clock,
) : ImpressionMapper {

    override fun map(payload: Any): ImpressionEvent? {
        val id = payload.readStringGetter(ID_GETTER) ?: return null
        val bidderName = payload.readStringGetter(BIDDER_NAME_GETTER) ?: return null
        return ImpressionEvent(
            id = id,
            bidderName = bidderName,
            source = ImpressionSource.ALPHA,
            occurredAt = clock.nowMillis(),
        )
    }
}
