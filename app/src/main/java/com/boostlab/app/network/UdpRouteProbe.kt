package com.boostlab.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
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
        val payload = MAGIC.toByteArray(Charsets.UTF_8)
        val results = MutableList<Long?>(samples) { null }

        DatagramSocket().use { socket ->
            socket.soTimeout = timeoutMs

            repeat(samples) { index ->
                val started = System.nanoTime()
                socket.send(DatagramPacket(payload, payload.size, address, port))

                try {
                    val replyBuffer = ByteArray(128)
                    val reply = DatagramPacket(replyBuffer, replyBuffer.size)
                    socket.receive(reply)

                    val body = String(reply.data, reply.offset, reply.length, Charsets.UTF_8)
                    if (
                        body == MAGIC &&
                        reply.address == address &&
                        reply.port == port
                    ) {
                        val elapsedMs = (System.nanoTime() - started) / 1_000_000L
                        results[index] = max(1L, elapsedMs)
                    }
                } catch (_: SocketTimeoutException) {
                    // A timeout is represented by null and becomes packet loss.
                }

                if (index != samples - 1) {
                    delay(gapMs)
                }
            }
        }

        RouteMetricsCalculator.calculate(results)
    }

    companion object {
        const val MAGIC = "BOOSTLAB/PROBE/1"
        const val DEFAULT_PORT = 51821
        const val DEFAULT_SAMPLES = 8
        const val DEFAULT_TIMEOUT_MS = 700
        const val DEFAULT_GAP_MS = 120L
    }
}
