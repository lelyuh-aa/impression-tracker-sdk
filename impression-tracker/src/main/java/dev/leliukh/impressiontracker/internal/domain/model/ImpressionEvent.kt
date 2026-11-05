package dev.leliukh.impressiontracker.internal.domain.model

import java.util.Locale

/** Message impressions are logged in, with the ad id and the bidder name as arguments. */
private const val LOG_MESSAGE_FORMAT = "Intercepted impression event:\n- ID: %s\n- Bidder Name: %s"

/**
 * A single ad impression model.
 *
 * Each ad network reports impressions with its own type and its own field
 * names. Interception adapters translate those into this model, so nothing
 * beyond the adapter has to know which network an event came from.
 *
 * @property id Identifier of the ad that produced the impression.
 * @property bidderName Name of the bidder the ad was served for.
 * @property source Ad network the impression was intercepted from.
 * @property occurredAt Moment of interception, in milliseconds since the Unix epoch.
 */
internal data class ImpressionEvent(
    val id: String,
    val bidderName: String,
    val source: ImpressionSource,
    val occurredAt: Long,
)

/**
 * Ad network an [ImpressionEvent] was intercepted from.
 *
 * @property networkName Name this network is reported under.
 */
internal enum class ImpressionSource(val networkName: String) {

    /** The `AlphaAds` SDK, which exposes a single impression listener. */
    ALPHA("AlphaAds"),

    /** The `BetaAds` SDK, which accepts any number of listeners. */
    BETA("BetaAds"),
}

/**
 * Renders the event in the exact message format impressions are logged in.
 *
 * @receiver Impression to render.
 * @return Three line message carrying the ad id and the bidder name.
 */
internal fun ImpressionEvent.toLogMessage(): String =
    String.format(Locale.ROOT, LOG_MESSAGE_FORMAT, id, bidderName)
