package dev.leliukh.impressiontracker.internal.interception

import dev.leliukh.impressiontracker.internal.TrackerLog
import dev.leliukh.impressiontracker.internal.domain.model.ImpressionSource
import dev.leliukh.impressiontracker.internal.domain.usecase.TrackImpression

/**
 * Turns an ad network's raw impression payload into a tracked event.
 *
 * Shared by every hook, because the steps are the same for all of them:
 * translate the payload, drop what cannot be read, and never let a failure
 * travel back into the ad network.
 *
 * @param source Ad network the payloads come from.
 * @param mapper Translates that network's payload.
 * @param trackImpression Receives the translated event.
 */
internal class InterceptedImpressionHandler(
    private val source: ImpressionSource,
    private val mapper: ImpressionMapper,
    private val trackImpression: TrackImpression,
) {

    /**
     * Handles one payload.
     *
     * The ad network calls this on its own thread, so nothing is allowed to
     * travel back: an exception escaping here would break that network's
     * dispatch, or crash the host application outright.
     *
     * @param payload Impression object handed over by the ad network, if any.
     */
    fun handle(payload: Any?) {
        if (payload == null) return
        try {
            val event = mapper.map(payload)
            if (event == null) {
                TrackerLog.warn("Unexpected ${source.networkName} payload: ${payload.javaClass.name}")
                return
            }
            trackImpression(event)
        } catch (failure: Throwable) {
            TrackerLog.warn("Could not handle a ${source.networkName} impression", failure)
        }
    }
}
