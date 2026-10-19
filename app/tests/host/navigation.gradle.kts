import com.sun.net.httpserver.HttpServer
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

// Same Kotlin/JDK loopback transport and synthetic session as the existing release fixtures.
tasks.register("runNavigationFixture") {
    group = "verification"
    description = "Runs public-topic and two-member private-chat mention navigation checks."
    notCompatibleWithConfigurationCache("Runs a long-lived local HTTP fixture")
    doLast {
        val root = file(providers.gradleProperty("navigationArtifacts").get()).apply { mkdirs() }
        val port = providers.gradleProperty("navigationPort").orNull?.toInt() ?: 19592
        val viewer = "00000000-0000-4000-8000-000000000057"
        val author = "00000000-0000-4000-8000-000000000058"
        val time = "2026-10-17T10:00:00Z"
        fun id(kind: Int, index: Int) = "00000000-0000-4000-800$kind-${index.toString().padStart(12, '0')}"
        fun person(uuid: String, name: String) = mapOf("uuid" to uuid, "first_name" to name,
            "last_name" to "", "username" to name.lowercase(), "status" to "active", "avatar" to "")
        val conversations = listOf(1 to "Public conversation", 2 to "Direct conversation")
        val streams = conversations.map { (index, name) -> mapOf(
            "uuid" to id(0, index), "name" to name, "private" to (index == 2),
            "direct_user_uuid" to if (index == 2) author else null,
            "color" to 0x6258e8, "updated_at" to time, "unread_count" to 2,
            "active_unread_count" to 2, "passive_unread_count" to 0,
            "default_topic_uuid" to id(1, index), "encryption" to false,
            "owner" to author, "project_id" to "project", "user_uuid" to viewer,
            "role" to "member", "notification_mode" to "all_messages") }
        val topics = conversations.map { (index, _) -> mapOf(
            "uuid" to id(1, index), "stream_uuid" to id(0, index), "name" to "Topic ${index + 1}",
            "color" to 0x6258e8, "last_message_uuid" to id(2, index), "updated_at" to time, "is_default" to true, "is_done" to false,
            "unread_count" to 1, "active_unread_count" to 1, "notification_mode" to "default") }
        val allTopics = topics + listOf(1 to 99, 2 to 98).map { (index, topic) -> mapOf(
            "uuid" to id(1, topic), "stream_uuid" to id(0, index),
            "name" to if (index == 1) "Mention topic" else "Private mention topic",
            "color" to 0x6258e8, "last_message_uuid" to id(2, topic), "updated_at" to time, "is_default" to false, "is_done" to false,
            "unread_count" to 1, "active_unread_count" to 1, "notification_mode" to "default") }
        val messages = conversations.map { (index, _) -> mapOf(
            "uuid" to id(2, index), "created_at" to time, "updated_at" to time,
            "stream_uuid" to id(0, index), "topic_uuid" to id(1, index), "project_id" to "project",
            "user_uuid" to viewer, "author_uuid" to author, "is_own" to false,
            "mentioned" to true, "reactions" to emptyMap<String, Int>(), "read" to false,
            "payload" to mapOf("kind" to "markdown", "content" to "Synthetic navigation message ${index + 1}")) }
        val allMessages = messages + listOf(1 to 99, 2 to 98).map { (index, topic) ->
            messages.first { it["stream_uuid"] == id(0, index) }.toMutableMap().apply {
                put("uuid", id(2, topic)); put("topic_uuid", id(1, topic))
                put("payload", mapOf("kind" to "markdown", "content" to
                    if (index == 1) "Mention outside default" else "Private mention outside default"))
            }
        }
        val bindings = conversations.flatMap { (index, _) -> listOf(viewer to "member", author to "owner").map { (user, role) ->
            mapOf("uuid" to "$user-$index", "stream_uuid" to id(0, index), "user_uuid" to user, "role" to role) } }
        val executor = ThreadPoolExecutor(4, 4, 0, TimeUnit.SECONDS, ArrayBlockingQueue(32), { task -> Thread(task, "cassi-navigation-fixture").apply {
            isDaemon = true
            contextClassLoader = JsonOutput::class.java.classLoader
        } }, ThreadPoolExecutor.AbortPolicy())
        val stopped = CountDownLatch(1)
        val shutdown = Thread({ stopped.countDown() }, "cassi-navigation-fixture-shutdown")
        Runtime.getRuntime().addShutdownHook(shutdown)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
        server.executor = executor
        val logLock = Any()
        val requestLog = root.resolve("navigation-network.jsonl")
        val maximumLogBytes = 2 * 1024 * 1024
        fun appendRecord(record: String) = synchronized(logLock) {
            val line = record + "\n"
            if (requestLog.length() + line.toByteArray(Charsets.UTF_8).size <= maximumLogBytes)
                requestLog.appendText(line)
        }
        server.createContext("/") { exchange ->
            try {
                val path = exchange.requestURI.path
                val query = exchange.requestURI.rawQuery.orEmpty().split('&').filter { '=' in it }.groupBy(
                    { URLDecoder.decode(it.substringBefore('='), "UTF-8") },
                    { URLDecoder.decode(it.substringAfter('='), "UTF-8") })
                fun filtered(rows: List<Map<String, Any?>>) = rows.filter { row ->
                    listOf("stream_uuid", "topic_uuid", "uuid").all { field ->
                        query[field].isNullOrEmpty() || row[field] in query.getValue(field)
                    }
                }
                val mode = root.resolve("navigation-mode.txt").takeIf { it.exists() }?.readText()?.trim().orEmpty()
                val authorization = exchange.requestHeaders.getFirst("Authorization")
                val recoveryToken = "cassi-navigation-$mode-refreshed"
                val status = when {
                    mode == "expire-enrichment" && path.endsWith("/messages/") && !query["uuid"].isNullOrEmpty() &&
                        authorization != "Bearer $recoveryToken" -> 401
                    mode == "expire-messages" && path.endsWith("/messages/") && query["mentioned"] == listOf("true") &&
                        authorization != "Bearer $recoveryToken" -> 401
                    mode == "expire-topics" && "/stream_topics/" in path && authorization != "Bearer $recoveryToken" -> 401
                    "/stream_topics/" in path && mode == "fail-topics" -> 503
                    path.endsWith("/messages/") && query["mentioned"] == listOf("true") && mode == "fail-messages" -> 503
                    else -> 200
                }
                val body: Any = when {
                    status != 200 -> emptyMap<String, String>()
                    path.endsWith("/actions/get_token/invoke") -> mapOf("access_token" to recoveryToken,
                        "refresh_token" to "cassi-navigation-$mode-refresh")
                    "/server_settings/" in path -> mapOf("email_auth_enabled" to true, "realm_name" to "Navigation fixture",
                        "realm_url" to "http://127.0.0.1:19557", "realm_icon" to "", "meet_url" to "https://meet.invalid")
                    "/api/core/v1/iam/projects/" in path -> listOf(mapOf("uuid" to "project", "name" to "Navigation fixture", "description" to ""))
                    "/me/" in path -> person(viewer, "Viewer")
                    "/users/" in path -> listOf(person(viewer, "Viewer"), person(author, "Alice"))
                    "/epoch/" in path -> mapOf("epoch_version" to 1, "epoch_generation" to "fixture")
                    "/actions/read" in path -> emptyMap<String, String>()
                    "/drafts/" in path -> emptyList<Any>()
                    "/stream_bindings/" in path -> filtered(bindings)
                    "/stream_topics/" in path -> filtered(if (mode == "cached-only") topics else allTopics)
                    "/streams/" in path -> filtered(streams)
                    path.endsWith("/messages/") -> filtered(allMessages)
                    else -> emptyList<Any>()
                }
                val bytes = JsonOutput.toJson(body).toByteArray(Charsets.UTF_8)
                exchange.responseHeaders.set("Content-Type", "application/json")
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.write(bytes)
                synchronized(logLock) {
                    appendRecord(JsonOutput.toJson(mapOf(
                        "path" to path, "method" to exchange.requestMethod, "status" to status,
                        "stream_uuid" to query["stream_uuid"], "topic_uuid" to query["topic_uuid"], "uuid" to query["uuid"], "mentioned" to query["mentioned"],
                        "auth_present" to !exchange.requestHeaders.getFirst("Authorization").isNullOrBlank(),
                        "auth_matches_fixture" to (authorization in setOf("Bearer cassi-avatar-57-access",
                            "Bearer cassi-navigation-expire-messages-refreshed", "Bearer cassi-navigation-expire-topics-refreshed",
                            "Bearer cassi-navigation-expire-enrichment-refreshed")),
                        "refreshed_fixture" to (authorization == "Bearer $recoveryToken"))))
                }
            } catch (error: Exception) {
                logger.error("Synthetic navigation fixture request failed", error)
                runCatching { exchange.sendResponseHeaders(500, -1) }
            } finally { exchange.close() }
        }
        server.start()
        logger.lifecycle("Navigation loopback fixture ready")
        try {
            if (providers.gradleProperty("navigationVerifyEpoch").orNull == "true") {
                val response = java.net.URI("http://127.0.0.1:$port/api/workspace/v1/epoch/").toURL().readText()
                val epoch = JsonSlurper().parseText(response) as Map<*, *>
                check(epoch["epoch_version"] is Number && epoch["epoch_generation"] is String &&
                    (epoch["epoch_generation"] as String).isNotBlank()) {
                    "Epoch HTTP response must match the Android numeric version/string generation contract"
                }
                logger.lifecycle("Navigation fixture Epoch HTTP contract passed")
            } else if (providers.gradleProperty("navigationVerifyLimits").orNull == "true") {
                check(!requestLog.exists()) { "Use a fresh verification artifact directory" }
                appendRecord("x".repeat(maximumLogBytes + 1))
                val logBounded = requestLog.length() <= maximumLogBytes
                val release = CountDownLatch(1)
                var rejected = 0
                val pool = executor as ThreadPoolExecutor
                try {
                    repeat(64) {
                        try { pool.execute { release.await(10, TimeUnit.SECONDS) } }
                        catch (_: RejectedExecutionException) { rejected++ }
                    }
                    check(logBounded && pool.largestPoolSize <= 4 && rejected > 0) {
                        "Fixture limits failed: logBounded=$logBounded workers=${pool.largestPoolSize} rejected=$rejected"
                    }
                } finally { release.countDown() }
                logger.lifecycle("Navigation fixture resource regression passed")
            } else stopped.await()
        } finally {
            server.stop(0)
            executor.shutdownNow()
            runCatching { Runtime.getRuntime().removeShutdownHook(shutdown) }
        }
    }
}
