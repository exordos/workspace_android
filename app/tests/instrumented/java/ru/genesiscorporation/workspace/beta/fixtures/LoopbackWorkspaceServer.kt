/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ru.genesiscorporation.workspace.beta.fixtures

import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.Executors

/** Bounded loopback HTTP transport shared by instrumented contract and screen tests. */
class LoopbackWorkspaceServer(private val handle: (Request) -> Response) : Closeable {
    data class Request(val method: String, val path: String, val headers: Map<String, String>, val body: String,
        val query: Map<String, List<String>> = emptyMap())
    data class Response(val body: String, val status: Int = 200)
    private val socket = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
    private val workers = Executors.newCachedThreadPool()
    val url = "http://127.0.0.1:${socket.localPort}"

    init {
        workers.execute {
            while (!socket.isClosed) {
                val connection = try { socket.accept() } catch (_: java.net.SocketException) { break }
                workers.execute { connection.use { runCatching { serve(it) } } }
            }
        }
    }

    private fun serve(connection: Socket) {
        connection.soTimeout = 10_000
        val input = connection.getInputStream().buffered()
        fun line() = buildString {
            while (true) {
                val next = input.read()
                check(next >= 0)
                if (next == 10) break
                if (next != 13) append(next.toChar())
                check(length < 8192)
            }
        }
        val start = line().split(' ')
        val headers = buildMap {
            while (true) {
                val value = line()
                if (value.isEmpty()) break
                check(size < 100)
                put(value.substringBefore(':').lowercase(), value.substringAfter(':').trim())
            }
        }
        val length = headers["content-length"]?.toInt() ?: 0
        check(length in 0..1_048_576)
        val body = ByteArray(length)
        var offset = 0
        while (offset < body.size) {
            val read = input.read(body, offset, body.size - offset)
            check(read > 0)
            offset += read
        }
        val query = start[1].substringAfter('?', "").split('&').filter { '=' in it }.groupBy(
            { URLDecoder.decode(it.substringBefore('='), "UTF-8") },
            { URLDecoder.decode(it.substringAfter('='), "UTF-8") })
        val response = handle(Request(start[0], start[1].substringBefore('?'), headers, body.decodeToString(), query))
        val bytes = response.body.toByteArray()
        connection.getOutputStream().apply {
            write("HTTP/1.1 ${response.status} Result\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
            write(bytes)
            flush()
        }
    }

    override fun close() { socket.close(); workers.shutdownNow() }
}
