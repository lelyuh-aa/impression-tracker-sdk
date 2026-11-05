package dev.leliukh.impressiontracker.internal.domain.model

/**
 * An [ImpressionEvent] waiting to be sent, together with the identity the
 * queue gave it.
 *
 * @property queueId Identity of the queue entry, used to delete it once the
 *   impression has been delivered. Unrelated to [ImpressionEvent.id], which
 *   names the ad and repeats whenever the same ad is shown again.
 * @property event The impression itself.
 */
internal data class QueuedImpression(
    val queueId: Long,
    val event: ImpressionEvent,
)
