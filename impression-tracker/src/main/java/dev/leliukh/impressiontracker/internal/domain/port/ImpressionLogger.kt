package dev.leliukh.impressiontracker.internal.domain.port

import dev.leliukh.impressiontracker.internal.domain.model.ImpressionEvent

/** Writes intercepted impressions to the device log. */
internal interface ImpressionLogger {

    /**
     * Writes a single impression.
     *
     * @param event Impression to write.
     */
    fun log(event: ImpressionEvent)
}
