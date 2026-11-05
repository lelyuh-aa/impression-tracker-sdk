package dev.leliukh.impressiontracker.internal.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.leliukh.impressiontracker.internal.domain.model.ImpressionEvent
import dev.leliukh.impressiontracker.internal.domain.model.ImpressionSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs on a device because `SQLiteOpenHelper` is part of the Android framework
 * and is only a stub on the JVM.
 */
@RunWith(AndroidJUnit4::class)
class SqliteImpressionQueueTest {

    private lateinit var database: ImpressionDatabase
    private lateinit var queue: SqliteImpressionQueue

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase("impression_tracker.db")
        database = ImpressionDatabase(context)
        queue = SqliteImpressionQueue(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun storesAnImpressionWithEveryField() {
        val event = impression(id = "ad-1", source = ImpressionSource.ALPHA, occurredAt = 1_700_000_000_000L)

        queue.enqueue(event)

        assertEquals(listOf(event), queue.peek(limit = 10).map { it.event })
    }

    @Test
    fun keepsImpressionsInTheOrderTheyWereRecorded() {
        queue.enqueue(impression(id = "ad-1"))
        queue.enqueue(impression(id = "ad-2"))
        queue.enqueue(impression(id = "ad-3"))

        assertEquals(
            listOf("ad-1", "ad-2", "ad-3"),
            queue.peek(limit = 10).map { it.event.id },
        )
    }

    @Test
    fun keepsBothNetworksApart() {
        queue.enqueue(impression(id = "ad-1", source = ImpressionSource.ALPHA))
        queue.enqueue(impression(id = "ad-2", source = ImpressionSource.BETA))

        assertEquals(
            listOf(ImpressionSource.ALPHA, ImpressionSource.BETA),
            queue.peek(limit = 10).map { it.event.source },
        )
    }

    @Test
    fun peekReturnsAtMostTheRequestedNumber() {
        repeat(5) { queue.enqueue(impression(id = "ad-$it")) }

        assertEquals(2, queue.peek(limit = 2).size)
    }

    @Test
    fun peekDoesNotConsumeEntries() {
        queue.enqueue(impression(id = "ad-1"))

        queue.peek(limit = 10)

        assertEquals(1, queue.peek(limit = 10).size)
    }

    @Test
    fun removesOnlyTheDeliveredEntries() {
        repeat(3) { queue.enqueue(impression(id = "ad-$it")) }
        val delivered = queue.peek(limit = 2)

        queue.remove(delivered.map { it.queueId })

        assertEquals(listOf("ad-2"), queue.peek(limit = 10).map { it.event.id })
    }

    @Test
    fun removingNothingIsHarmless() {
        queue.enqueue(impression(id = "ad-1"))

        queue.remove(emptyList())

        assertEquals(1, queue.peek(limit = 10).size)
    }

    @Test
    fun survivesReopeningTheDatabase() {
        queue.enqueue(impression(id = "ad-1"))
        database.close()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = ImpressionDatabase(context)
        queue = SqliteImpressionQueue(database)

        assertEquals(listOf("ad-1"), queue.peek(limit = 10).map { it.event.id })
    }

    @Test
    fun dropsTheOldestEntriesOnceFull() {
        repeat(1_010) { queue.enqueue(impression(id = "ad-$it")) }

        val stored = queue.peek(limit = 2_000)

        assertEquals(1_000, stored.size)
        assertEquals("ad-10", stored.first().event.id)
        assertTrue(stored.none { it.event.id == "ad-0" })
    }

    private fun impression(
        id: String,
        source: ImpressionSource = ImpressionSource.ALPHA,
        occurredAt: Long = 1_000L,
    ) = ImpressionEvent(
        id = id,
        bidderName = "ACME",
        source = source,
        occurredAt = occurredAt,
    )
}
