package dev.leliukh.impressiontracker.internal.interception

import dev.leliukh.impressiontracker.internal.TrackerLog
import dev.leliukh.impressiontracker.internal.domain.model.ImpressionSource
import java.lang.reflect.Proxy

private const val NETWORK_CLASS = "com.betaads.sdk.BetaAds"
private const val LISTENER_CLASS = "com.betaads.sdk.BetaImpressionListener"
private const val INSTANCE_FIELD = "INSTANCE"
private const val ADD_LISTENER_METHOD = "addImpressionListener"
private const val CALLBACK_METHOD = "onAdImpression"

/**
 * Hook for `BetaAds`.
 *
 * That network keeps its impression listeners in a list and exposes an `add`
 * method, so the SDK simply registers one more listener next to the host
 * application's own. Nothing the host registered is touched.
 *
 * The listener is created with [Proxy] rather than by implementing the
 * network's interface, because doing the latter would make the SDK depend on
 * the ad network library at runtime.
 *
 * @param handler Receives every intercepted payload.
 */
internal class BetaAdNetworkHook(
    private val handler: InterceptedImpressionHandler,
) : AdNetworkHook {

    override val source: ImpressionSource = ImpressionSource.BETA

    /**
     * A failure is contained rather than raised: this runs during the host
     * application's start-up, where a missing impression is acceptable and a
     * crash is not. A network that is simply absent is reported quietly,
     * because that is an ordinary situation and not a fault.
     *
     * @return `true` when impressions will now be delivered to the SDK.
     */
    override fun install(): Boolean =
        try {
            val networkClass = Class.forName(NETWORK_CLASS)
            val listenerClass = Class.forName(LISTENER_CLASS)
            val network = networkClass.getField(INSTANCE_FIELD).get(null)
            networkClass.getMethod(ADD_LISTENER_METHOD, listenerClass)
                .invoke(network, newListener(listenerClass))
            true
        } catch (_: ClassNotFoundException) {
            TrackerLog.debug("${source.networkName} is not present in this app")
            false
        } catch (failure: Throwable) {
            TrackerLog.warn("Could not hook ${source.networkName}", failure)
            false
        }

    private fun newListener(listenerClass: Class<*>): Any =
        Proxy.newProxyInstance(listenerClass.classLoader, arrayOf(listenerClass)) { proxy, method, args ->
            when {
                method.name == CALLBACK_METHOD && args?.size == 1 -> {
                    handler.handle(args[0])
                    null
                }

                // The network stores listeners in a list and looks them up by
                // equality, so these three must behave like Object's own.
                method.name == "equals" && args?.size == 1 -> proxy === args[0]
                method.name == "hashCode" -> System.identityHashCode(proxy)
                method.name == "toString" -> "${source.networkName}Hook"
                else -> null
            }
        }
}
