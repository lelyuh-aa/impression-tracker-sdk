package dev.leliukh.impressiontracker.internal.domain.usecase

import dev.leliukh.impressiontracker.DispatchPolicy
import dev.leliukh.impressiontracker.internal.domain.model.ImpressionEvent
import dev.leliukh.impressiontracker.internal.domain.port.ImpressionLogger
import dev.leliukh.impressiontracker.internal.domain.port.ImpressionQueue
import java.util.concurrent.Executor

/**
 * Handles one intercepted impression.
 *
 * Single entry point into the SDK for every interception adapter, so that all
 * networks are treated identically once their events have been translated.
 *
 * Logging happens on the calling thread because it costs microseconds and keeps
 * the SDK's message next to the host's own. Storing and sending do not: they
 * touch the disk and the network, and the caller is a thread owned by an ad
 * network that should not be held up by the SDK.
 *
 * @param logger Destination for the required impression message.
 * @param queue Keeps the impression until it reaches the backend.
 * @param flushPendingImpressions Delivers what the queue holds.
 * @param dispatchPolicy Reads the policy in force right now. A function and
 *   not a value on purpose: this use case is built once and lives for the
 *   whole process, while the host may call `configure` at any time and the
 *   new policy has to apply to the very next impression.
 * @param executor Runs storing and sending away from the ad network's thread.
 */
internal class TrackImpression(
    private val logger: ImpressionLogger,
    private val queue: ImpressionQueue,
    private val flushPendingImpressions: FlushPendingImpressions,
    private val dispatchPolicy: () -> DispatchPolicy,
    private val executor: Executor,
) {

    /**
     * Processes one impression.
     *
     * @param event Impression that was just intercepted.
     */
    operator fun invoke(event: ImpressionEvent) {
        logger.log(event)
        executor.execute {
            queue.enqueue(event)
            if (dispatchPolicy() == DispatchPolicy.IMMEDIATE) {
                flushPendingImpressions()
            }
        }
    }
}
