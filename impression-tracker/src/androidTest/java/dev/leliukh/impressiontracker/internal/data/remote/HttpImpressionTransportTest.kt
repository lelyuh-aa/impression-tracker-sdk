package dev.leliukh.impressiontracker.internal.data.remote

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.leliukh.impressiontracker.internal.domain.model.ImpressionEvent
import dev.leliukh.impressiontracker.internal.domain.model.ImpressionSource
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Drives the transport against a real socket, so the request it produces is
 * inspected exactly as a backend would see it.
 */
@RunWith(AndroidJUnit4::class)
class HttpImpressionTransportTest {

    private lateinit var server: ServerSocket
    private val requestReceived = CountDownLatch(1)

    @Volatile
    private var requestBody: String? = null

    @Before
    fun setUp() {
        server = ServerSocket(0)
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun sendsEveryImpressionAsJson() {
        respondWith("200 OK")
        val transport = transportTo(server.localPort)

        val accepted = transport.send(
            listOf(
                impression("ad-1", ImpressionSource.ALPHA),
                impression("ad-2", ImpressionSource.BETA),
            ),
        )

        assertTrue(accepted)
        assertTrue(requestReceived.await(5, TimeUnit.SECONDS))

        val impressions = JSONObject(requestBody!!).getJSONArray("impressions")
        assertEquals(2, impressions.length())
        with(impressions.getJSONObject(0)) {
            assertEquals("ad-1", getString("id"))
            assertEquals("ACME", getString("bidderName"))
            assertEquals("AlphaAds", getString("network"))
            assertEquals(1_700_000_000_000L, getLong("occurredAt"))
        }
        assertEquals("BetaAds", impressions.getJSONObject(1).getString("network"))
    }

    @Test
    fun keepsImpressionsWhenTheBackendRejectsThem() {
        respondWith("500 Internal Server Error")

        val accepted = transportTo(server.localPort).send(listOf(impression("ad-1", ImpressionSource.ALPHA)))

        assertFalse(accepted)
    }

    @Test
    fun reportsFailureInsteadOfThrowingWhenNothingIsListening() {
        val deadPort = server.localPort
        server.close()

        val accepted = transportTo(deadPort).send(listOf(impression("ad-1", ImpressionSource.ALPHA)))

        assertFalse(accepted)
    }

    @Test
    fun reportsFailureWhenTheEndpointIsNotAnHttpAddress() {
        val transport = HttpImpressionTransport { "file:///tmp/impressions" }

        val accepted = transport.send(listOf(impression("ad-1", ImpressionSource.ALPHA)))

        assertFalse(accepted)
    }

    @Test
    fun sendingAnEmptyBatchTouchesNoNetwork() {
        val accepted = transportTo(port = 1).send(emptyList())

        assertTrue(accepted)
    }

    private fun transportTo(port: Int) =
        HttpImpressionTransport { "http://127.0.0.1:$port/impressions" }

    /** Accepts one request on a background thread and answers with [status]. */
    private fun respondWith(status: String) {
        Thread {
            server.accept().use { socket ->
                val reader = socket.getInputStream().bufferedReader()
                var contentLength = 0
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    if (line.startsWith("Content-Length:", ignoreCase = true)) {
                        contentLength = line.substringAfter(':').trim().toInt()
                    }
                }
                val body = CharArray(contentLength)
                reader.read(body, 0, contentLength)
                requestBody = String(body)
                socket.getOutputStream().write(
                    "HTTP/1.1 $status\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray(),
                )
                socket.getOutputStream().flush()
                requestReceived.countDown()
            }
        }.apply { isDaemon = true }.start()
    }

    private fun impression(id: String, source: ImpressionSource) = ImpressionEvent(
        id = id,
        bidderName = "ACME",
        source = source,
        occurredAt = 1_700_000_000_000L,
    )
}
