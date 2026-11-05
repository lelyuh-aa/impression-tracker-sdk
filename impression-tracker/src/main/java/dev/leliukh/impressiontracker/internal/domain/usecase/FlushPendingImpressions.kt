package dev.leliukh.impressiontracker.internal.domain.usecase

import dev.leliukh.impressiontracker.internal.domain.model.QueuedImpression
import dev.leliukh.impressiontracker.internal.domain.port.ImpressionQueue
import dev.leliukh.impressiontracker.internal.domain.port.ImpressionTransport

/** Impressions sent in one request. */
private const val BATCH_SIZE = 50

/**
 * Upper bound on the batches a single flush sends.
 *
 * Large enough to drain a full queue, and it guarantees the loop ends even if
 * deleting delivered entries keeps failing.
 */
private const val MAX_BATCHES_PER_FLUSH = 20

/**
 * Sends everything the queue holds and deletes what the backend accepted.
 *
 * Entries are deleted only after a successful response, so an impression is
 * never lost because the device was offline. The cost is that a response lost
 * on the way back causes a resend, which the backend is expected to tolerate.
 *
 * A batch the backend does not accept ends the flush instead of being retried
 * on the spot. Retrying in a tight loop against an unreachable backend would
 * drain the battery and keep the radio awake for nothing; the impressions
 * stay queued and the next flush - the next impression, or the daily job -
 * picks them up.
 *
 * @param queue Holds impressions that have not been delivered.
 * @param transport Delivers them.
 */
internal class FlushPendingImpressions(
    private val queue: ImpressionQueue,
    private val transport: ImpressionTransport,
) {

    /** Drains the queue, stopping at the first batch the backend does not accept. */
    operator fun invoke() {
        repeat(MAX_BATCHES_PER_FLUSH) {
            val batch = queue.peek(BATCH_SIZE)
            if (batch.isEmpty()) return
            if (!transport.send(batch.map(QueuedImpression::event))) return
            queue.remove(batch.map(QueuedImpression::queueId))
        }
    }
}
