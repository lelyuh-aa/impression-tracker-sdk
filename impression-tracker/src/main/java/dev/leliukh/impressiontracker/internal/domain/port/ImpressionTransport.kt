package dev.leliukh.impressiontracker.internal.domain.port

import dev.leliukh.impressiontracker.internal.domain.model.ImpressionEvent

/** Delivers impressions to the backend. */
internal interface ImpressionTransport {

    /**
     * Sends one batch.
     *
     * Implementations never throw: a backend that is down or a device that is
     * offline is an ordinary outcome, not an error the SDK reacts to.
     *
     * @param events Impressions to deliver, oldest first.
     * @return `true` when the batch was accepted and may be deleted locally.
     */
    fun send(events: List<ImpressionEvent>): Boolean
}
