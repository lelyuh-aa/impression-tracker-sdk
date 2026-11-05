package dev.leliukh.impressiontracker.internal.interception

import dev.leliukh.impressiontracker.internal.domain.model.ImpressionEvent
import dev.leliukh.impressiontracker.internal.domain.model.ImpressionSource
import dev.leliukh.impressiontracker.internal.domain.port.Clock

private const val ID_GETTER = "getCreativeId"
private const val BIDDER_NAME_GETTER = "getNetworkName"

/**
 * Reads `BetaAds` impressions, which report a creative id and a
 * network name.
 *
 * @param clock Supplies the interception timestamp.
 */
internal class BetaImpressionMapper(
    private val clock: Clock,
) : ImpressionMapper {

    override fun map(payload: Any): ImpressionEvent? {
        val id = payload.readStringGetter(ID_GETTER) ?: return null
        val bidderName = payload.readStringGetter(BIDDER_NAME_GETTER) ?: return null
        return ImpressionEvent(
            id = id,
            bidderName = bidderName,
            source = ImpressionSource.BETA,
            occurredAt = clock.nowMillis(),
        )
    }
}
