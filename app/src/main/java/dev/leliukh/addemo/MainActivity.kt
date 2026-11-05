package dev.leliukh.addemo

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.alphaads.sdk.AlphaAdLoadListener
import com.alphaads.sdk.AlphaAds
import com.alphaads.sdk.AlphaAdShowListener
import dev.leliukh.addemo.ui.theme.AdDemoTheme
import com.betaads.sdk.BetaAdLoadCallback
import com.betaads.sdk.BetaAdShowCallback
import com.betaads.sdk.BetaAds
import kotlin.random.Random

private const val TAG = "MainActivity"

/** Identifies which ad network a loaded ad belongs to. */
private enum class AdNetwork { ALPHA, BETA }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AdDemoTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AdNetworkDemo(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun AdNetworkDemo(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var loadedAdId: String? by rememberSaveable { mutableStateOf(null) }
    var loadedNetwork: AdNetwork? by rememberSaveable { mutableStateOf(null) }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxSize()
    ) {
        Button(
            onClick = {
                // Randomly pick which ad network to load from.
                val network = if (Random.nextBoolean()) AdNetwork.ALPHA else AdNetwork.BETA
                Log.i(TAG, "loading from $network")

                when (network) {
                    AdNetwork.ALPHA -> AlphaAds.loadAd("placementId", object : AlphaAdLoadListener {
                        override fun onLoadSuccess(adId: String) {
                            Log.i(TAG, "load success")
                            loadedAdId = adId
                            loadedNetwork = AdNetwork.ALPHA
                        }

                        override fun onLoadFailure(exception: Exception) {
                            Log.e(TAG, "load failure", exception)
                        }
                    })

                    AdNetwork.BETA -> BetaAds.loadAd("placementId", object : BetaAdLoadCallback {
                        override fun onLoaded(adId: String) {
                            Log.i(TAG, "load success")
                            loadedAdId = adId
                            loadedNetwork = AdNetwork.BETA
                        }

                        override fun onLoadError(error: Exception) {
                            Log.e(TAG, "load failure", error)
                        }
                    })
                }
            }
        ) {
            Text("Load")
        }

        Button(
            enabled = loadedAdId != null,
            onClick = {
                val adId = loadedAdId
                when (loadedNetwork) {
                    AdNetwork.ALPHA -> adId?.let {
                        AlphaAds.showAd(context, it, object : AlphaAdShowListener {
                            override fun onShowSuccess() {
                                Log.i(TAG, "show success")
                            }

                            override fun onAdClose() {
                                Log.i(TAG, "ad close")
                            }

                            override fun onShowFailure(exception: Exception) {
                                Log.e(TAG, "load failure", exception)
                            }
                        })
                    }

                    AdNetwork.BETA -> adId?.let {
                        BetaAds.showAd(context, it, object : BetaAdShowCallback {
                            override fun onShown() {
                                Log.i(TAG, "show success")
                            }

                            override fun onDismissed() {
                                Log.i(TAG, "ad close")
                            }

                            override fun onShowError(error: Exception) {
                                Log.e(TAG, "load failure", error)
                            }
                        })
                    }

                    null -> Unit
                }
                loadedAdId = null
                loadedNetwork = null
            }
        ) {
            Text("Show")
        }
    }
}
