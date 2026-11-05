package dev.leliukh.impressiontracker.internal.domain.port

/** Source of the current time, so that code depending on it stays testable. */
internal fun interface Clock {

    /**
     * Reads the current time.
     *
     * @return Milliseconds since the Unix epoch.
     */
    fun nowMillis(): Long
}
