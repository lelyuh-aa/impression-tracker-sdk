package dev.leliukh.impressiontracker.internal.data.remote

import dev.leliukh.impressiontracker.internal.TrackerLog
import dev.leliukh.impressiontracker.internal.domain.model.ImpressionEvent
import dev.leliukh.impressiontracker.internal.domain.port.ImpressionTransport
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val CONNECT_TIMEOUT_MILLIS = 10_000
private const val READ_TIMEOUT_MILLIS = 10_000
private const val CONTENT_TYPE = "application/json; charset=utf-8"

private const val FIELD_IMPRESSIONS = "impressions"
private const val FIELD_ID = "id"
private const val FIELD_BIDDER_NAME = "bidderName"
private const val FIELD_NETWORK = "network"
private const val FIELD_OCCURRED_AT = "occurredAt"

/**
 * [ImpressionTransport] speaking plain HTTP through the JDK's own client.
 *
 * `HttpURLConnection` is used because the SDK may not depend on a networking
 * library, and `org.json` for the same reason on the serialisation side.
 *
 * @param endpoint Reads the URL in force right now. A function and not a
 *   value on purpose: this transport is built once and lives for the whole
 *   process, while the host may call `configure` at any time and the next
 *   batch has to go to the new address.
 */
internal class HttpImpressionTransport(
    private val endpoint: () -> String,
) : ImpressionTransport {

    /**
     * The cause of a failure is logged without its stack trace: being offline
     * is an ordinary condition, and the exception's type and message already
     * say everything worth knowing about it.
     *
     * @param events Impressions to deliver, oldest first.
     * @return `true` when the batch was accepted and may be deleted locally.
     */
    override fun send(events: List<ImpressionEvent>): Boolean {
        if (events.isEmpty()) return true

        var connection: HttpURLConnection? = null
        return try {
            connection = openConnection() ?: return false
            connection.outputStream.use { stream ->
                stream.write(payloadOf(events).toByteArray(Charsets.UTF_8))
            }
            val status = connection.responseCode
            val accepted = status in HTTP_OK_RANGE
            if (!accepted) {
                TrackerLog.warn("Backend answered $status, keeping ${events.size} impressions")
            }
            accepted
        } catch (failure: Exception) {
            TrackerLog.warn("Could not deliver ${events.size} impressions: $failure")
            false
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Opens a connection to the configured endpoint.
     *
     * The cast cannot be assumed to succeed: `openConnection` returns whatever
     * handler matches the URL's scheme, and only `http` and `https` produce an
     * [HttpURLConnection]. A `file` or `ftp` address would yield something else,
     * so it is reported as the configuration mistake it is instead of surfacing
     * as a cast failure.
     *
     * @return The prepared connection, or `null` when the endpoint is not an HTTP address.
     */
    private fun openConnection(): HttpURLConnection? {
        val url = endpoint()
        val connection = URL(url).openConnection() as? HttpURLConnection
        if (connection == null) {
            TrackerLog.warn("Endpoint is not an http or https address, impressions cannot be sent: $url")
            return null
        }
        return connection.apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
            doOutput = true
            setRequestProperty("Content-Type", CONTENT_TYPE)
        }
    }

    /**
     * Serialises a batch.
     *
     * @param events Impressions to describe.
     * @return A JSON document holding one object per impression.
     */
    private fun payloadOf(events: List<ImpressionEvent>): String {
        val impressions = JSONArray()
        events.forEach { event ->
            impressions.put(
                JSONObject()
                    .put(FIELD_ID, event.id)
                    .put(FIELD_BIDDER_NAME, event.bidderName)
                    .put(FIELD_NETWORK, event.source.networkName)
                    .put(FIELD_OCCURRED_AT, event.occurredAt),
            )
        }
        return JSONObject().put(FIELD_IMPRESSIONS, impressions).toString()
    }

    private companion object {
        private val HTTP_OK_RANGE = 200..299
    }
}
