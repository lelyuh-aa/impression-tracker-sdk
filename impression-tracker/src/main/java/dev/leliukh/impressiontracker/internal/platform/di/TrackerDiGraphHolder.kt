package dev.leliukh.impressiontracker.internal.platform.di

import android.annotation.SuppressLint

/**
 * Makes the dependency injection graph reachable by components that Android
 * itself creates.
 *
 * A `JobService` is instantiated by the framework through its no argument
 * constructor, so it cannot be handed anything. Everywhere else the SDK passes
 * collaborators through constructors, and this is the single deliberate
 * exception to that.
 *
 * Nothing needs to be cleared: the graph lives exactly as long as the process
 * that created it.
 */
internal object TrackerDiGraphHolder {

    /**
     * Static reference to an object that transitively holds a context, which
     * the leak inspection flags on sight. It is not a leak here: the graph
     * keeps the application context, which already lives for the whole
     * process, and never an activity or any other short lived one.
     */
    @SuppressLint("StaticFieldLeak")
    @Volatile
    private var diGraph: TrackerDiGraph? = null

    /**
     * Publishes the graph of this process.
     *
     * @param diGraph Graph built while the process was starting.
     */
    fun publish(diGraph: TrackerDiGraph) {
        this.diGraph = diGraph
    }

    /**
     * Reads the graph.
     *
     * @return The graph, or `null` when the SDK did not start in this process,
     *   which happens in an application's secondary processes.
     */
    fun current(): TrackerDiGraph? = diGraph
}
