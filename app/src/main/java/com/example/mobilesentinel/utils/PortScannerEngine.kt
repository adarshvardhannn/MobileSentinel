package com.example.mobilesentinel.utils

import com.example.mobilesentinel.data.database.entities.PortResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PortScannerEngine @Inject constructor() {

    val serviceMap: Map<Int, String> = mapOf(
        21 to "FTP",
        22 to "SSH",
        23 to "Telnet",
        25 to "SMTP",
        53 to "DNS",
        80 to "HTTP",
        110 to "POP3",
        143 to "IMAP",
        443 to "HTTPS",
        3306 to "MySQL",
        3389 to "RDP",
        5432 to "PostgreSQL",
        5555 to "ADB",
        5900 to "VNC",
        6666 to "IRC",
        8080 to "HTTP-ALT",
        8443 to "HTTPS-ALT",
        4444 to "Metasploit",
        1337 to "Hacker",
        31337 to "BackOrifice",
        9001 to "Tor",
        6667 to "IRC"
    )

    val riskyPorts: Set<Int> = setOf(
        4444, 5555, 1337, 31337, 9001, 6666, 6667, 7777, 23, 3389
    )

    suspend fun scanPort(host: String, port: Int, timeoutMs: Int = 500): PortResult {
        return withContext(Dispatchers.IO) {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                socket.close()
                PortResult(
                    sessionId = 0,
                    port = port,
                    protocol = "TCP",
                    status = "OPEN",
                    serviceName = serviceMap[port] ?: "Unknown",
                    riskFlag = port in riskyPorts
                )
            } catch (e: Exception) {
                PortResult(
                    sessionId = 0,
                    port = port,
                    protocol = "TCP",
                    status = "CLOSED",
                    serviceName = serviceMap[port] ?: "Unknown",
                    riskFlag = port in riskyPorts
                )
            }
        }
    }

    suspend fun scanRange(
        host: String,
        start: Int,
        end: Int,
        onProgress: (scanned: Int, total: Int) -> Unit
    ): List<PortResult> {
        val ports = (start..end).toList()
        val total = ports.size
        val results = mutableListOf<PortResult>()
        var scanned = 0

        coroutineScope {
            ports.chunked(50).forEach { chunk ->
                val chunkResults = chunk.map { port ->
                    async { scanPort(host, port) }
                }.awaitAll()

                results.addAll(chunkResults)
                scanned += chunk.size
                onProgress(scanned, total)
            }
        }

        return results.filter { it.status == "OPEN" }
    }
}

