package com.alphaads.sdk

/**
 * One impression as this network reports it.
 *
 * @property adId Identifier of the ad that was shown.
 * @property bidderName Name of the bidder the ad was served for.
 */
class AlphaImpression(
    val adId: String,
    val bidderName: String,
) {
    override fun toString(): String = "AlphaImpression(adId=$adId, bidderName=$bidderName)"
}

/**
 * Receives an impression every time an ad is shown.
 *
 * The network holds **one** of these at a time. There is no way to add a second
 * one, so whoever registers last wins.
 */
fun interface AlphaImpressionListener {

    /**
     * Called once per shown ad.
     *
     * @param impression The impression that was just recorded.
     */
    fun onImpression(impression: AlphaImpression)
}

/** Outcome of [AlphaAds.loadAd]. */
interface AlphaAdLoadListener {

    /**
     * An ad is ready to be shown.
     *
     * @param adId Handle to pass to [AlphaAds.showAd].
     */
    fun onLoadSuccess(adId: String)

    /**
     * No ad could be loaded.
     *
     * @param exception Why the load failed.
     */
    fun onLoadFailure(exception: Exception)
}

/** Outcome of [AlphaAds.showAd]. */
interface AlphaAdShowListener {

    /** The ad is on screen; an impression has been recorded. */
    fun onShowSuccess()

    /** The user dismissed the ad. */
    fun onAdClose()

    /**
     * The ad could not be shown.
     *
     * @param exception Why the ad failed to show.
     */
    fun onShowFailure(exception: Exception)
}
