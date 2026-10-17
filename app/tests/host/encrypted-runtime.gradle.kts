import com.sun.net.httpserver.HttpServer
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

// Reuses the synthetic session seeded by AvatarFixtureInstrumentation, including its device URL.
tasks.register("runEncryptedRuntimeFixture") {
    group = "verification"
    description = "Runs an encrypted-chat request/reopen fixture for the optimized APK."
    notCompatibleWithConfigurationCache("Runs a long-lived local HTTP fixture")
    doLast {
        val root = file(providers.gradleProperty("encryptedRuntimeArtifacts").get()).apply { mkdirs() }
        val modeFile = root.resolve("runtime-mode.txt").also { if (!it.exists()) it.writeText("fail") }
        val viewer = "00000000-0000-4000-8000-000000000057"
        val owner = "00000000-0000-4000-8000-000000000058"
        val stream = "00000000-0000-4000-8000-000000000069"
        val topic = "00000000-0000-4000-8001-000000000069"
        val key = "00000000-0000-4000-8002-000000000069"
        val time = "2026-10-16T10:00:00Z"
        fun person(id: String) = mapOf("uuid" to id, "first_name" to "Synthetic", "last_name" to "User",
            "username" to "cassi-fixture", "status" to "active", "avatar" to "")
        fun message(id: String, payload: Any?, author: String, index: Int) = mapOf(
            "uuid" to id, "created_at" to "2026-10-16T10:00:${index.toString().padStart(2, '0')}Z",
            "updated_at" to time, "stream_uuid" to stream, "topic_uuid" to topic, "project_id" to "project",
            "user_uuid" to viewer, "author_uuid" to author, "is_own" to (author == viewer),
            "reactions" to emptyMap<String, Int>(), "read" to true, "payload" to payload)
        fun placement(canonical: String): String {
            val namespace = UUID.fromString(topic)
            val bytes = ByteBuffer.allocate(16).putLong(namespace.mostSignificantBits).putLong(namespace.leastSignificantBits).array()
            val digest = MessageDigest.getInstance("SHA-1").digest(bytes + UUID.fromString(canonical).toString().toByteArray())
            digest[6] = ((digest[6].toInt() and 15) or 80).toByte()
            digest[8] = ((digest[8].toInt() and 63) or 128).toByte()
            return ByteBuffer.wrap(digest).let { UUID(it.long, it.long).toString() }
        }
        val messages = CopyOnWriteArrayList<Map<String, Any?>>()
        val payloadPath = providers.gradleProperty("encryptedRuntimePayloads").orNull
        val historicalKey = if (payloadPath == null) key else "77777777-7777-4777-8777-777777777777"
        messages += message(UUID.randomUUID().toString(), mapOf("kind" to "e2ee", "content" to "opaque-history", "key_uuid" to historicalKey), owner, 0)
        payloadPath?.let { path ->
            val payloads = JsonSlurper().parse(file(path)) as List<*>
            check(payloads.size == 2)
            payloads.forEachIndexed { index, payload ->
                check(payload is Map<*, *> && payload["kind"] == "e2ee" && payload["key_uuid"] == key)
                messages += message(UUID.randomUUID().toString(), payload, if (index == 0) viewer else owner, index + 1)
            }
            messages += message(UUID.randomUUID().toString(), mapOf("kind" to "e2ee", "content" to "invalid-ciphertext", "key_uuid" to key), viewer, 3)
        }
        val lock = Any()
        var firstBody: String? = null
        var attempts = 0
        val executor = Executors.newCachedThreadPool { task -> Thread(task, "cassi-encrypted-runtime-fixture").apply {
            isDaemon = true
            contextClassLoader = JsonOutput::class.java.classLoader
        } }
        val port = providers.gradleProperty("encryptedRuntimePort").orNull?.toInt() ?: 19567
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
        server.executor = executor
        server.createContext("/") { exchange ->
            try {
                val path = exchange.requestURI.path
                val requestedIds = exchange.requestURI.rawQuery.orEmpty().split('&').filter { it.substringBefore('=') == "uuid" }
                    .map { URLDecoder.decode(it.substringAfter('='), "UTF-8") }
                var status = 200
                val body: Any = when {
                    "/server_settings/" in path -> mapOf("email_auth_enabled" to true, "realm_name" to "Encrypted runtime fixture",
                        "realm_url" to "http://127.0.0.1:19557", "realm_icon" to "", "meet_url" to "https://meet.invalid")
                    "/api/core/v1/iam/projects/" in path -> listOf(mapOf("uuid" to "project", "name" to "Runtime fixture", "description" to ""))
                    "/me/" in path -> person(viewer)
                    "/users/" in path -> listOf(person(viewer), person(owner))
                    "/epoch/" in path -> mapOf("epoch_version" to 1, "epoch_generation" to 1)
                    "/stream_bindings/" in path -> listOf(viewer to "member", owner to "owner").map { (user, role) ->
                        mapOf("uuid" to user, "stream_uuid" to stream, "user_uuid" to user, "role" to role) }
                    "/stream_topics/" in path -> listOf(mapOf("uuid" to topic, "stream_uuid" to stream, "name" to "General",
                        "color" to 0, "updated_at" to time, "is_default" to true, "is_done" to false,
                        "unread_count" to 0, "active_unread_count" to 0, "notification_mode" to "all_messages"))
                    "/streams/" in path -> listOf(mapOf("uuid" to stream, "name" to "Encrypted runtime fixture", "private" to true,
                        "color" to 0, "updated_at" to time, "unread_count" to 0, "active_unread_count" to 0, "passive_unread_count" to 0,
                        "default_topic_uuid" to topic, "encryption" to true, "current_encryption_key_uuid" to key,
                        "owner" to owner, "project_id" to "project", "user_uuid" to viewer, "role" to "member", "notification_mode" to "all_messages"))
                    path.endsWith("/messages/") && exchange.requestMethod == "POST" -> synchronized(lock) {
                        val raw = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
                        val request = JsonSlurper().parseText(raw) as Map<*, *>
                        val payload = request["payload"] as Map<*, *>
                        check(payload["kind"] == "key_request")
                        val mode = modeFile.readText().trim()
                        check(mode in setOf("fail", "reject", "pending"))
                        attempts++
                        if (firstBody == null) firstBody = raw
                        root.resolve("runtime-attempts.jsonl").appendText(JsonOutput.toJson(mapOf(
                            "attempt" to attempts, "mode" to mode, "matches_first_request" to (raw == firstBody))) + "\n")
                        if (mode == "fail") { status = 503; emptyMap<String, String>() }
                        else {
                            val sent = message(placement(request["uuid"] as String), payload, viewer, messages.size)
                            messages += sent
                            if (mode == "reject") messages += message(UUID.randomUUID().toString(), mapOf(
                                "kind" to "key_reject", "key_uuid" to payload["key_uuid"], "request_message_uuid" to sent["uuid"],
                                "sender_device_id" to "synthetic-owner-device", "recipient_device_id" to payload["device_id"]), owner, messages.size)
                            sent
                        }
                    }
                    path.endsWith("/messages/") -> messages.filter { requestedIds.isEmpty() || it["uuid"] in requestedIds }
                    else -> emptyList<Any>()
                }
                synchronized(lock) {
                    root.resolve("runtime-network.jsonl").appendText(JsonOutput.toJson(mapOf("path" to path, "method" to exchange.requestMethod, "status" to status)) + "\n")
                }
                val bytes = JsonOutput.toJson(body).toByteArray(Charsets.UTF_8)
                exchange.responseHeaders.set("Content-Type", "application/json")
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.write(bytes)
            } catch (error: Exception) {
                logger.error("Encrypted runtime fixture request failed", error)
                exchange.sendResponseHeaders(500, -1)
            } finally { exchange.close() }
        }
        server.start()
        logger.lifecycle("Encrypted runtime loopback fixture ready")
        try { CountDownLatch(1).await() } finally { server.stop(0); executor.shutdownNow() }
    }
}
