package com.boostlab.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException

class LanGatewayDiscovery {
    suspend fun discover(
        port: Int = UdpRouteProbe.DEFAULT_PORT,
        timeoutMs: Int = DEFAULT_DISCOVERY_TIMEOUT_MS,
    ): List<String> = withContext(Dispatchers.IO) {
        require(port in 1..65535) { "Invalid UDP port" }
        require(timeoutMs in 200..5_000) { "Invalid discovery timeout" }

        val payload = UdpRouteProbe.MAGIC.toByteArray(Charsets.UTF_8)
        val discovered = linkedSetOf<String>()

        DatagramSocket().use { socket ->
            socket.broadcast = true

            for (address in broadcastAddresses()) {
                runCatching {
                    socket.send(
                        DatagramPacket(
                            payload,
                            payload.size,
                            address,
                            port,
                        ),
                    )
                }
            }

            val deadline = System.currentTimeMillis() + timeoutMs
            val buffer = ByteArray(128)

            while (System.currentTimeMillis() < deadline) {
                val remaining = (deadline - System.currentTimeMillis())
                    .coerceAtLeast(1L)
                    .coerceAtMost(RECEIVE_SLICE_MS.toLong())
                    .toInt()
                socket.soTimeout = remaining

                val reply = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(reply)
                } catch (_: SocketTimeoutException) {
                    continue
                }

                val body = String(
                    reply.data,
                    reply.offset,
                    reply.length,
                    Charsets.UTF_8,
                )

                if (body == UdpRouteProbe.MAGIC && reply.port == port) {
                    val host = reply.address.hostAddress
                    if (!host.isNullOrBlank()) {
                        discovered += host
                    }
                }
            }
        }

        discovered.sorted()
    }

    private fun broadcastAddresses(): Set<InetAddress> {
        val addresses = linkedSetOf<InetAddress>()

        runCatching {
            addresses += InetAddress.getByName("255.255.255.255")
        }

        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (!networkInterface.isUp || networkInterface.isLoopback) continue

                for (interfaceAddress in networkInterface.interfaceAddresses) {
                    interfaceAddress.broadcast?.let(addresses::add)
                }
            }
        }

        return addresses
    }

    companion object {
        private const val DEFAULT_DISCOVERY_TIMEOUT_MS = 1_200
        private const val RECEIVE_SLICE_MS = 200
    }
}
