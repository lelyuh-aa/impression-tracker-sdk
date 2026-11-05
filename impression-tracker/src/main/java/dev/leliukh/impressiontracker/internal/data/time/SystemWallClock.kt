package dev.leliukh.impressiontracker.internal.data.time

import dev.leliukh.impressiontracker.internal.domain.port.Clock

/** [Clock] backed by the device's wall clock. */
internal class SystemWallClock : Clock {

    override fun nowMillis(): Long = System.currentTimeMillis()
}
