package dev.leliukh.impressiontracker.internal.interception

import dev.leliukh.impressiontracker.internal.TrackerLog
import dev.leliukh.impressiontracker.internal.domain.model.ImpressionSource
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy

private const val LISTENER_INTERFACE = "com.alphaads.sdk.AlphaImpressionListener"
private const val SERVICE_LOCATOR_CLASS = "com.alphaads.sdk.ServiceRegistry"
private const val CALLBACK_METHOD = "onImpression"

/**
 * Hook for `AlphaAds`.
 *
 * Unlike the other network, this one holds a *single* impression listener and
 * its public API only offers a setter. Registering the SDK's own listener would
 * therefore throw the host application's listener away and silence it, which
 * the host must never notice. Instead the SDK reads the listener the host
 * installed,
 * wraps it in a [Proxy] that forwards every call to it and additionally reports
 * the impression, and puts the wrapper back - the decorator pattern applied
 * through reflection.
 *
 * Two details make this reliable:
 *
 * - The slot is a plain setter, so the host can overwrite the wrapper at any
 *   point - when it re-registers its listener after a configuration change,
 *   for instance. [ensureAttached] is therefore idempotent and cheap, and runs
 *   again on activity lifecycle events, long before any ad can be shown.
 * - Only the listener interface and the class holding the slot are looked up by
 *   name. The slot itself is found by *type*, so renaming fields or methods
 *   inside the ad network does not break the hook.
 *
 * @param handler Receives every intercepted payload.
 */
internal class AlphaAdNetworkHook(
    private val handler: InterceptedImpressionHandler,
) : AdNetworkHook {

    /** The object holding the listener, and the field the listener sits in. */
    private class ListenerSlot(val owner: Any, val field: Field)

    override val source: ImpressionSource = ImpressionSource.ALPHA

    private var listenerInterface: Class<*>? = null
    private var slot: ListenerSlot? = null
    private var installedListener: Any? = null

    /**
     * Three outcomes are told apart on purpose, because they mean very
     * different things to whoever reads the log: the network is not in this
     * application at all, which is ordinary; the network is present but its
     * internals no longer look the way this hook expects, which needs
     * attention; or something else failed, which is contained rather than
     * raised, since a missing impression is acceptable and a crash during the
     * host's start-up is not.
     *
     * @return `true` when impressions will now be delivered to the SDK.
     */
    override fun install(): Boolean {
        val listenerInterface = try {
            Class.forName(LISTENER_INTERFACE)
        } catch (_: ClassNotFoundException) {
            TrackerLog.debug("${source.networkName} is not present in this app")
            return false
        }
        this.listenerInterface = listenerInterface

        return try {
            val slot = findListenerSlot(listenerInterface)
            this.slot = slot
            if (slot == null) {
                TrackerLog.warn("${source.networkName} internals changed, impressions will be missed")
                false
            } else {
                ensureAttached()
                true
            }
        } catch (failure: Throwable) {
            TrackerLog.warn("Could not hook ${source.networkName}", failure)
            false
        }
    }

    @Synchronized
    override fun ensureAttached() {
        val slot = slot ?: return
        val listenerInterface = listenerInterface ?: return
        try {
            val current = slot.field.get(slot.owner)
            if (current === installedListener) return
            val wrapper = newListener(listenerInterface, delegate = current)
            slot.field.set(slot.owner, wrapper)
            installedListener = wrapper
        } catch (failure: Throwable) {
            TrackerLog.warn("Could not attach to ${source.networkName}", failure)
        }
    }

    /**
     * Finds the field holding the impression listener.
     *
     * The network keeps its collaborators in a service locator. Rather than
     * relying on field and method names inside it, which an optimizer is free
     * to rename, this walks the locator's static fields and picks the one whose
     * type declares a field of the listener's own type.
     *
     * The type has to match exactly. A field narrowed to some subtype of the
     * listener could not hold a [Proxy] anyway, so finding one would only
     * postpone the failure to the write. The SDK ships keep rules that stop an
     * optimizer from narrowing it in the first place.
     *
     * @param listenerInterface The network's impression listener interface.
     * @return The slot, or `null` when the network's internals no longer look
     *   the way this hook expects.
     */
    private fun findListenerSlot(listenerInterface: Class<*>): ListenerSlot? {
        val locator = try {
            Class.forName(SERVICE_LOCATOR_CLASS)
        } catch (_: ClassNotFoundException) {
            return null
        }
        for (staticField in locator.declaredFields) {
            if (!Modifier.isStatic(staticField.modifiers)) continue
            val listenerField = staticField.type.declaredFields
                .firstOrNull { it.type == listenerInterface }
                ?: continue
            staticField.isAccessible = true
            val owner = staticField.get(null) ?: continue
            listenerField.isAccessible = true
            return ListenerSlot(owner, listenerField)
        }
        return null
    }

    private fun newListener(listenerInterface: Class<*>, delegate: Any?): Any =
        Proxy.newProxyInstance(listenerInterface.classLoader, arrayOf(listenerInterface)) { proxy, method, args ->
            when {
                method.name == CALLBACK_METHOD && args?.size == 1 -> {
                    // The host is served first, so its own logging happens
                    // exactly as it did before the SDK was added.
                    val result = forward(delegate, method, args)
                    handler.handle(args[0])
                    result
                }

                method.name == "equals" && args?.size == 1 -> proxy === args[0]
                method.name == "hashCode" -> System.identityHashCode(proxy)
                method.name == "toString" -> "${source.networkName}Hook"
                else -> forward(delegate, method, args)
            }
        }

    /**
     * Passes a call on to the listener the host application installed.
     *
     * @param delegate The host's listener, or `null` if it had not registered one.
     * @param method Method that was called on the wrapper.
     * @param args Arguments of that call.
     * @return Whatever the host's listener returned.
     */
    private fun forward(delegate: Any?, method: Method, args: Array<out Any>?): Any? {
        if (delegate == null) return null
        return try {
            method.invoke(delegate, *(args ?: emptyArray()))
        } catch (invocationFailed: InvocationTargetException) {
            // Rethrow what the host's listener threw, so the ad network sees
            // the same failure it would have seen without the wrapper.
            throw invocationFailed.targetException
        }
    }
}
