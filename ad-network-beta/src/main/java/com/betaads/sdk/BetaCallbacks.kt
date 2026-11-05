package com.betaads.sdk

/**
 * One impression as this network reports it.
 *
 * The field names differ from the other network's on purpose: an integrator has
 * to map each network's own vocabulary.
 *
 * @property creativeId Identifier of the creative that was shown.
 * @property networkName Name of the demand source the creative came from.
 */
class BetaImpressionInfo(
    val creativeId: String,
    val networkName: String,
) {
    override fun toString(): String = "BetaImpressionInfo(creativeId=$creativeId, networkName=$networkName)"
}

/** Receives an impression every time an ad is shown. */
interface BetaImpressionListener {

    /**
     * Called once per shown ad, on every registered listener.
     *
     * @param info The impression that was just recorded.
     */
    fun onAdImpression(info: BetaImpressionInfo)
}

/** Outcome of [BetaAds.loadAd]. */
interface BetaAdLoadCallback {

    /**
     * An ad is ready to be shown.
     *
     * @param adId Handle to pass to [BetaAds.showAd].
     */
    fun onLoaded(adId: String)

    /**
     * No ad could be loaded.
     *
     * @param error Why the load failed.
     */
    fun onLoadError(error: Exception)
}

/** Outcome of [BetaAds.showAd]. */
interface BetaAdShowCallback {

    /** The ad is on screen; an impression has been recorded. */
    fun onShown()

    /** The user dismissed the ad. */
    fun onDismissed()

    /**
     * The ad could not be shown.
     *
     * @param error Why the ad failed to show.
     */
    fun onShowError(error: Exception)
}
