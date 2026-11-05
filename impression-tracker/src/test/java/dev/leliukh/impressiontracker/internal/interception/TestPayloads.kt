package dev.leliukh.impressiontracker.internal.interception

/**
 * Stand-ins for the ad networks' impression types, which the SDK never
 * references and which a JVM test therefore cannot get hold of.
 *
 * The mappers read their payloads by getter name, so any class exposing the
 * right getters plays the part. These classes are deliberately public and
 * top-level: reflection cannot reach a public method of a non-public class
 * without making it accessible first.
 */

/** Looks like `BetaImpressionInfo`. */
class BetaPayload(private val creative: String, private val network: String) {
    fun getCreativeId(): String = creative
    fun getNetworkName(): String = network
}

/** Looks like `AlphaImpression`. */
class AlphaPayload(private val ad: String, private val bidder: String) {
    fun getAdId(): String = ad
    fun getBidderName(): String = bidder
}

/** Looks like neither, so every mapper must reject it. */
class ForeignPayload {
    fun getSomethingElse(): String = "nope"
}
