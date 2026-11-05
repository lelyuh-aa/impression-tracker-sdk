package com.alphaads.sdk

/**
 * Holds the network's single impression listener and fans impressions out to it.
 *
 * Internal on purpose: an integrator is expected to reach this only through
 * [AlphaAds], which is why there is no public way to inspect or extend the slot.
 */
internal class ImpressionDispatcher {

    private var listener: AlphaImpressionListener? = null

    /**
     * Replaces the single listener.
     *
     * @param listener Listener to store, or `null` to clear the slot.
     */
    fun setListener(listener: AlphaImpressionListener?) {
        this.listener = listener
    }

    /**
     * Hands an impression to the stored listener, if there is one.
     *
     * @param impression Impression to report.
     */
    fun dispatch(impression: AlphaImpression) {
        listener?.onImpression(impression)
    }
}

/**
 * Service locator the network wires itself through.
 *
 * @property dispatcher The process wide impression dispatcher.
 */
internal object ServiceRegistry {

    @JvmField
    val dispatcher: ImpressionDispatcher = ImpressionDispatcher()
}
