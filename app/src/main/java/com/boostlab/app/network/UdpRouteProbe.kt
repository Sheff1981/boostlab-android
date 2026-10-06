package com.boostlab.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.security.SecureRandom
import kotlin.math.max

class UdpRouteProbe {
    suspend fun measure(
        host: String,
        port: Int = DEFAULT_PORT,
        samples: Int = DEFAULT_SAMPLES,
        timeoutMs: Int = DEFAULT_TIMEOUT_MS,
        gapMs: Long = DEFAULT_GAP_MS,
    ): RouteMetrics = withContext(Dispatchers.IO) {
        require(host.isNotBlank()) { "Gateway host is empty" }
        require(port in 1..65535) { "Invalid UDP port" }
        require(samples in 1..50) { "Invalid sample count" }

        val address = InetAddress.getByName(host.trim())
        val nonce = randomNonceHex()
        val results = MutableList<Long?>(samples) { null }

        DatagramSocket().use { socket ->
            repeat(samples) { index ->
                val expected = probePayload(nonce, index)
                val payload = expected.toByteArray(Charsets.UTF_8)
                val started = System.nanoTime()
                val deadline = started + (timeoutMs * 1_000_000L)

                socket.send(DatagramPacket(payload, payload.size, address, port))

                while (System.nanoTime() < deadline && results[index] == null) {
                    val remainingMs = ((deadline - System.nanoTime()) / 1_000_000L)
                        .coerceAtLeast(1L)
                        .coerceAtMost(timeoutMs.toLong())
                        .toInt()
                    socket.soTimeout = remainingMs

                    try {
                        val replyBuffer = ByteArray(128)
                        val reply = DatagramPacket(replyBuffer, replyBuffer.size)
                        socket.receive(reply)

                        val body = String(
                            reply.data,
                            reply.offset,
                            reply.length,
                            Charsets.UTF_8,
                        )

                        if (
                            body == expected &&
                            reply.address == address &&
                            reply.port == port
                        ) {
                            val elapsedMs = (System.nanoTime() - started) / 1_000_000L
                            results[index] = max(1L, elapsedMs)
                        }
                    } catch (_: SocketTimeoutException) {
                        break
                    }
                }

                if (index != samples - 1) {
                    delay(gapMs)
                }
            }
        }

        RouteMetricsCalculator.calculate(results)
    }

    internal fun probePayload(nonce: String, sequence: Int): String {
        require(nonce.matches(HEX_NONCE_REGEX)) { "Invalid probe nonce" }
        require(sequence in 0..999) { "Invalid probe sequence" }
        return "$V2_PREFIX$nonce/$sequence"
    }

    private fun randomNonceHex(): String {
        val bytes = ByteArray(NONCE_BYTES)
        SECURE_RANDOM.nextBytes(bytes)
        return bytes.joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }

    companion object {
        const val MAGIC = "BOOSTLAB/PROBE/1"
        const val V2_PREFIX = "BOOSTLAB/PROBE/2/"
        const val DEFAULT_PORT = 51821
        const val DEFAULT_SAMPLES = 8
        const val DEFAULT_TIMEOUT_MS = 700
        const val DEFAULT_GAP_MS = 120L

        private const val NONCE_BYTES = 8
        private val SECURE_RANDOM = SecureRandom()
        private val HEX_NONCE_REGEX = Regex("^[0-9a-f]{16}$")
    }
}
