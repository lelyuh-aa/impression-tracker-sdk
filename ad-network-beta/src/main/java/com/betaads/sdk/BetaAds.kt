package com.betaads.sdk

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/** Demand sources this network pretends to serve creatives from. */
private val NETWORKS = listOf("ACME", "NORTHWIND", "UMBRELLA", "INITECH")

/** Simulated network latency, in milliseconds. */
private const val LOAD_DELAY_MILLIS = 400L
private const val SHOW_DELAY_MILLIS = 200L
private const val DISMISS_DELAY_MILLIS = 1_500L

/**
 * A stand-in for a closed source ad network SDK that boots lazily and accepts
 * any number of impression listeners.
 *
 * It exists so this repository builds and runs on its own. Its shape is what
 * matters: unlike the other network it offers an additive listener API, so an
 * integrator can observe impressions without displacing anyone.
 */
object BetaAds {

    private val handler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<BetaImpressionListener>()

    /**
     * Registers one more impression listener.
     *
     * Listeners are compared by identity, so registering the same one twice has
     * no effect.
     *
     * @param listener Listener to add.
     */
    fun addImpressionListener(listener: BetaImpressionListener) {
        listeners.addIfAbsent(listener)
    }

    /**
     * Unregisters a previously added listener.
     *
     * @param listener Listener to remove.
     */
    fun removeImpressionListener(listener: BetaImpressionListener) {
        listeners.remove(listener)
    }

    /**
     * Requests an ad.
     *
     * @param placementId Placement the ad is requested for.
     * @param callback Receives the loaded ad or the failure.
     */
    fun loadAd(placementId: String, callback: BetaAdLoadCallback) {
        handler.postDelayed({ callback.onLoaded(UUID.randomUUID().toString()) }, LOAD_DELAY_MILLIS)
    }

    /**
     * Shows a previously loaded ad and records an impression for it.
     *
     * @param context Context the ad would be presented in.
     * @param adId Handle received from [loadAd].
     * @param callback Receives the show, impression and dismiss callbacks.
     */
    fun showAd(context: Context, adId: String, callback: BetaAdShowCallback) {
        handler.postDelayed({
            callback.onShown()
            val info = BetaImpressionInfo(adId, NETWORKS.random())
            listeners.forEach { it.onAdImpression(info) }
            handler.postDelayed(callback::onDismissed, DISMISS_DELAY_MILLIS)
        }, SHOW_DELAY_MILLIS)
    }
}
