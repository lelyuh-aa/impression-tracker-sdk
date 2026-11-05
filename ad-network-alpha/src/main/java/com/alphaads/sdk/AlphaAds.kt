package com.alphaads.sdk

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.UUID

/** Bidders this network pretends to serve ads for. */
private val BIDDERS = listOf("ACME", "NORTHWIND", "UMBRELLA", "INITECH")

/** Simulated network latency, in milliseconds. */
private const val LOAD_DELAY_MILLIS = 400L
private const val SHOW_DELAY_MILLIS = 200L
private const val CLOSE_DELAY_MILLIS = 1_500L

/**
 * A stand-in for a closed source ad network SDK that requires explicit
 * initialisation and accepts exactly one impression listener.
 *
 * It exists so this repository builds and runs on its own. Its shape is what
 * matters: a single listener slot with no `add` method, which is the constraint
 * the impression tracker has to work around.
 */
object AlphaAds {

    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var initialised = false

    /** Prepares the network. Nothing else works until this has run. */
    fun initialize() {
        initialised = true
    }

    /**
     * Registers the impression listener, replacing any previous one.
     *
     * @param listener Listener to receive every impression from now on.
     */
    fun setAdImpressionDataListener(listener: AlphaImpressionListener) {
        ServiceRegistry.dispatcher.setListener(listener)
    }

    /**
     * Requests an ad.
     *
     * @param placementId Placement the ad is requested for.
     * @param listener Receives the loaded ad or the failure.
     */
    fun loadAd(placementId: String, listener: AlphaAdLoadListener) {
        if (!initialised) {
            listener.onLoadFailure(IllegalStateException("AlphaAds.initialize() has not been called"))
            return
        }
        handler.postDelayed({ listener.onLoadSuccess(UUID.randomUUID().toString()) }, LOAD_DELAY_MILLIS)
    }

    /**
     * Shows a previously loaded ad and records an impression for it.
     *
     * @param context Context the ad would be presented in.
     * @param adId Handle received from [loadAd].
     * @param listener Receives the show, impression and close callbacks.
     */
    fun showAd(context: Context, adId: String, listener: AlphaAdShowListener) {
        handler.postDelayed({
            listener.onShowSuccess()
            ServiceRegistry.dispatcher.dispatch(AlphaImpression(adId, BIDDERS.random()))
            handler.postDelayed(listener::onAdClose, CLOSE_DELAY_MILLIS)
        }, SHOW_DELAY_MILLIS)
    }
}
