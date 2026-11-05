package dev.leliukh.impressiontracker.internal.platform.di

import android.content.Context
import dev.leliukh.impressiontracker.ImpressionTracker
import dev.leliukh.impressiontracker.internal.data.local.ImpressionDatabase
import dev.leliukh.impressiontracker.internal.data.local.SqliteImpressionQueue
import dev.leliukh.impressiontracker.internal.data.logging.LogcatImpressionLogger
import dev.leliukh.impressiontracker.internal.data.remote.HttpImpressionTransport
import dev.leliukh.impressiontracker.internal.data.time.SystemWallClock
import dev.leliukh.impressiontracker.internal.domain.model.ImpressionSource
import dev.leliukh.impressiontracker.internal.domain.port.Clock
import dev.leliukh.impressiontracker.internal.domain.port.ImpressionLogger
import dev.leliukh.impressiontracker.internal.domain.port.ImpressionQueue
import dev.leliukh.impressiontracker.internal.domain.port.ImpressionTransport
import dev.leliukh.impressiontracker.internal.domain.usecase.FlushPendingImpressions
import dev.leliukh.impressiontracker.internal.domain.usecase.TrackImpression
import dev.leliukh.impressiontracker.internal.interception.AdNetworkHook
import dev.leliukh.impressiontracker.internal.interception.AlphaAdNetworkHook
import dev.leliukh.impressiontracker.internal.interception.AlphaImpressionMapper
import dev.leliukh.impressiontracker.internal.interception.ImpressionMapper
import dev.leliukh.impressiontracker.internal.interception.InterceptedImpressionHandler
import dev.leliukh.impressiontracker.internal.interception.BetaAdNetworkHook
import dev.leliukh.impressiontracker.internal.interception.BetaImpressionMapper
import java.util.concurrent.Executor
import java.util.concurrent.Executors

private const val WORKER_THREAD_NAME = "ImpressionTrackerWorkerThread"

/**
 * Dependency injection graph of the SDK: creates every collaborator and
 * wires them together, and does nothing else. Starting the SDK belongs to
 * its caller.
 *
 * Written by hand on purpose. A dependency injection framework would add a
 * runtime dependency the SDK is not allowed to have, for a graph small enough
 * to fit on one screen.
 *
 * @param context Any context of the host application. Only its application
 *   context is kept, so the graph can never hold on to a short lived one.
 */
internal class TrackerDiGraph(
    context: Context,
) {

    private val applicationContext: Context = context.applicationContext

    private val clock: Clock by lazy { SystemWallClock() }

    private val logger: ImpressionLogger by lazy { LogcatImpressionLogger() }

    private val queue: ImpressionQueue by lazy { SqliteImpressionQueue(ImpressionDatabase(applicationContext)) }

    /**
     * Single background thread shared by the whole SDK. One thread is enough
     * for the work it does and keeps writes to the queue naturally ordered.
     */
    val backgroundExecutor: Executor by lazy {
        Executors.newSingleThreadExecutor { task ->
            Thread(task, WORKER_THREAD_NAME).apply { isDaemon = true }
        }
    }

    /**
     * Settings are handed over as functions rather than as values, because
     * everything here is created once and kept for the life of the process,
     * while the host may call [ImpressionTracker.configure] at any moment.
     */
    private val transport: ImpressionTransport by lazy {
        HttpImpressionTransport { ImpressionTracker.config.endpoint }
    }

    val flushPendingImpressions: FlushPendingImpressions by lazy {
        FlushPendingImpressions(queue, transport)
    }

    private val trackImpression: TrackImpression by lazy {
        TrackImpression(
            logger = logger,
            queue = queue,
            flushPendingImpressions = flushPendingImpressions,
            dispatchPolicy = { ImpressionTracker.config.dispatchPolicy },
            executor = backgroundExecutor,
        )
    }

    val adNetworkHooks: List<AdNetworkHook> by lazy {
        listOf(
            AlphaAdNetworkHook(handlerFor(ImpressionSource.ALPHA, AlphaImpressionMapper(clock))),
            BetaAdNetworkHook(handlerFor(ImpressionSource.BETA, BetaImpressionMapper(clock))),
        )
    }

    private fun handlerFor(
        source: ImpressionSource,
        mapper: ImpressionMapper,
    ): InterceptedImpressionHandler = InterceptedImpressionHandler(source, mapper, trackImpression)
}
