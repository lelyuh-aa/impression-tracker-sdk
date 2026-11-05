package dev.leliukh.addemo

import android.app.Application
import android.util.Log
import com.alphaads.sdk.AlphaAds
import com.betaads.sdk.BetaImpressionInfo
import com.betaads.sdk.BetaAds
import com.betaads.sdk.BetaImpressionListener

class AdDemoApplication: Application() {
    override fun onCreate() {
        super.onCreate()

        AlphaAds.initialize()

        AlphaAds.setAdImpressionDataListener {
            Log.i("Ad Demo", "New Impression $it")
        }

        // BetaAds has no explicit initialize(); it boots lazily on
        // first use. Registering an impression listener is enough to start
        // receiving impressions (additive, multi-listener API).
        BetaAds.addImpressionListener(object : BetaImpressionListener {
            override fun onAdImpression(info: BetaImpressionInfo) {
                Log.i("Ad Demo", "New Second Impression $info")
            }
        })
    }
}