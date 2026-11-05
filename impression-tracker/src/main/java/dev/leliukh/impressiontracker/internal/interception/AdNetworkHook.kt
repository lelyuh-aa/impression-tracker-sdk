package dev.leliukh.impressiontracker.internal.interception

import dev.leliukh.impressiontracker.internal.domain.model.ImpressionSource

/**
 * Attaches the SDK to one ad network's impression callbacks.
 *
 * Implementations reach their network through reflection only. The SDK must
 * keep working when a network is missing from the host application, so a hook
 * that cannot attach reports failure instead of throwing.
 */
internal interface AdNetworkHook {

    /** Ad network this hook attaches to. */
    val source: ImpressionSource

    /**
     * Attaches to the network, if it is present in the host application.
     *
     * @return `true` when impressions will now be delivered to the SDK.
     */
    fun install(): Boolean

    /**
     * Re-attaches if the host application has replaced the network's listener
     * since [install] ran. Networks that accept several listeners cannot lose
     * the SDK's one, so they do nothing here.
     */
    fun ensureAttached(): Unit = Unit
}
