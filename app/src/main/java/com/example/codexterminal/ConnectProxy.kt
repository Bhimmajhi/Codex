package com.example.codexterminal

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

private const val MAX_HEADER_LINE = 8192

/**
 * A local HTTP CONNECT proxy listening on 127.0.0.1.
 *
 * The static Codex binary cannot read Android's DNS settings because there is no
 * /etc/resolv.conf on Android. Codex sends its HTTPS traffic through this proxy instead.
 * The proxy resolves each hostname with Android's resolver and copies bytes in both
 * directions without decrypting them, so Codex still verifies TLS certificates itself.
 */
class ConnectProxy {

    private val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())

    val port: Int
        get() = server.localPort

    init {
        thread(isDaemon = true, name = "connect-proxy-accept") { acceptLoop() }
    }

    fun close() {
        server.close()
    }

    private fun acceptLoop() {
        while (!server.isClosed) {
            val client = try {
                server.accept()
            } catch (error: IOException) {
                return
            }
            thread(isDaemon = true, name = "connect-proxy-client") {
                try {
                    handle(client)
                } catch (error: Exception) {
                    // A broken client connection must not crash the app.
                }
            }
        }
    }

    private fun handle(client: Socket) {
        client.use { socket ->
            val input = socket.getInputStream()
            val requestLine = readLine(input) ?: return

            var header = readLine(input)
            while (!header.isNullOrEmpty()) {
                header = readLine(input)
            }

            val parts = requestLine.split(" ")
            if (parts.size < 2 || parts[0] != "CONNECT") {
                writeText(socket, "HTTP/1.1 405 Method Not Allowed\r\nContent-Length: 0\r\n\r\n")
                return
            }

            val authority = parseAuthority(parts[1])
            if (authority == null) {
                writeText(socket, "HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\n\r\n")
                return
            }
            val (host, targetPort) = authority

            val remote = try {
                Socket(InetAddress.getByName(host), targetPort)
            } catch (error: IOException) {
                writeText(socket, "HTTP/1.1 502 Bad Gateway\r\nContent-Length: 0\r\n\r\n")
                return
            }

            remote.use { upstream ->
                writeText(socket, "HTTP/1.1 200 Connection Established\r\n\r\n")
                thread(isDaemon = true, name = "connect-proxy-uplink") {
                    pipe(input, upstream.getOutputStream())
                    shutdownOutputQuietly(upstream)
                }
                pipe(upstream.getInputStream(), socket.getOutputStream())
            }
        }
    }

    private fun parseAuthority(authority: String): Pair<String, Int>? {
        val colon = authority.lastIndexOf(':')
        if (colon <= 0) return null
        val port = authority.substring(colon + 1).toIntOrNull() ?: return null
        return Pair(authority.substring(0, colon), port)
    }

    /** Reads one header line without buffering, so no bytes after the headers are lost. */
    private fun readLine(input: InputStream): String? {
        val line = StringBuilder()
        var value = input.read()
        while (value != -1 && value != '\n'.code) {
            line.append(value.toChar())
            if (line.length > MAX_HEADER_LINE) {
                throw IOException("Header line is too long")
            }
            value = input.read()
        }
        if (value == -1 && line.isEmpty()) return null
        return line.toString().removeSuffix("\r")
    }

    private fun writeText(socket: Socket, text: String) {
        val out = socket.getOutputStream()
        out.write(text.toByteArray(Charsets.ISO_8859_1))
        out.flush()
    }

    private fun pipe(from: InputStream, to: OutputStream) {
        val buffer = ByteArray(16 * 1024)
        try {
            while (true) {
                val count = from.read(buffer)
                if (count == -1) break
                to.write(buffer, 0, count)
                to.flush()
            }
        } catch (error: IOException) {
            // Either side closed the connection.
        }
    }

    private fun shutdownOutputQuietly(socket: Socket) {
        try {
            socket.shutdownOutput()
        } catch (error: IOException) {
            // Already closed.
        }
    }
}
