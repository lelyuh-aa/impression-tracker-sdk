package dev.leliukh.impressiontracker.internal.interception

import dev.leliukh.impressiontracker.internal.domain.model.ImpressionEvent

/**
 * Translates one ad network's impression object into the SDK's own model.
 *
 * The payload arrives as [Any] because the SDK never references the ad
 * networks' types; each implementation knows how to read its own network.
 */
internal interface ImpressionMapper {

    /**
     * Translates a payload.
     *
     * @param payload Impression object handed over by the ad network.
     * @return The translated event, or `null` when the payload does not look
     *   the way the network is expected to report impressions.
     */
    fun map(payload: Any): ImpressionEvent?
}
