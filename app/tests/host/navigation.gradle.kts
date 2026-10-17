import com.sun.net.httpserver.HttpServer
import groovy.json.JsonOutput
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

// Same Kotlin/JDK loopback transport and synthetic session as the existing release fixtures.
tasks.register("runNavigationFixture") {
    group = "verification"
    description = "Runs private/public group, direct and self conversations for optimized navigation checks."
    notCompatibleWithConfigurationCache("Runs a long-lived local HTTP fixture")
    doLast {
        val root = file(providers.gradleProperty("navigationArtifacts").get()).apply { mkdirs() }
        val port = providers.gradleProperty("navigationPort").orNull?.toInt() ?: 19590
        val viewer = "00000000-0000-4000-8000-000000000057"
        val author = "00000000-0000-4000-8000-000000000058"
        val time = "2026-10-17T10:00:00Z"
        fun id(kind: Int, index: Int) = "00000000-0000-4000-800$kind-${index.toString().padStart(12, '0')}"
        fun person(uuid: String, name: String) = mapOf("uuid" to uuid, "first_name" to name,
            "last_name" to "", "username" to name.lowercase(), "status" to "active", "avatar" to "")
        val names = listOf("Private group", "Public group", "Stored direct", "Stored self", "Encrypted private group")
        val streams = names.mapIndexed { index, name -> mapOf(
            "uuid" to id(0, index), "name" to name, "private" to (index != 1),
            "direct_user_uuid" to when (index) { 2 -> author; 3 -> viewer; else -> null },
            "color" to 0x6258e8, "updated_at" to time, "unread_count" to if (index == 1) 2 else 1,
            "active_unread_count" to if (index == 1) 2 else 1,
            "passive_unread_count" to 0, "default_topic_uuid" to id(1, index), "encryption" to (index == 4),
            "owner" to if (index == 3) viewer else author, "project_id" to "project", "user_uuid" to viewer,
            "role" to if (index == 3) "owner" else "member",
            "notification_mode" to "all_messages") }
        val topics = names.mapIndexed { index, _ -> mapOf(
            "uuid" to id(1, index), "stream_uuid" to id(0, index), "name" to "Topic ${index + 1}",
            "color" to 0x6258e8, "updated_at" to time, "is_default" to true, "is_done" to false,
            "unread_count" to 1, "active_unread_count" to 1, "notification_mode" to "default") } + listOf(mapOf(
            "uuid" to id(1, 99), "stream_uuid" to id(0, 1), "name" to "Mention topic",
            "color" to 0x6258e8, "updated_at" to time, "is_default" to false, "is_done" to false,
            "unread_count" to 1, "active_unread_count" to 1, "notification_mode" to "default"))
        val messages = names.mapIndexed { index, _ -> mapOf(
            "uuid" to id(2, index), "created_at" to time, "updated_at" to time,
            "stream_uuid" to id(0, index), "topic_uuid" to id(1, index), "project_id" to "project",
            "user_uuid" to viewer, "author_uuid" to if (index == 3) viewer else author,
            "is_own" to (index == 3), "mentioned" to true, "reactions" to emptyMap<String, Int>(), "read" to false,
            "payload" to if (index == 4) mapOf("kind" to "e2ee", "content" to "opaque-fixture",
                "key_uuid" to id(3, index)) else mapOf("kind" to "markdown", "content" to "Synthetic navigation message ${index + 1}")) } + listOf(mapOf(
            "uuid" to id(2, 99), "created_at" to time, "updated_at" to time,
            "stream_uuid" to id(0, 1), "topic_uuid" to id(1, 99), "project_id" to "project",
            "user_uuid" to viewer, "author_uuid" to author, "is_own" to false, "mentioned" to true,
            "reactions" to emptyMap<String, Int>(), "read" to false,
            "payload" to mapOf("kind" to "markdown", "content" to "Mention outside default")))
        val drafts = (0..3).map { index -> mapOf(
            "uuid" to id(4, index), "stream_uuid" to id(0, index), "topic_uuid" to id(1, index),
            "updated_at" to time, "revision" to 1,
            "payload" to mapOf("kind" to "markdown", "content" to "Synthetic draft ${index + 1}")) }
        val bindings = names.flatMapIndexed { index, _ -> (if (index == 3) listOf(viewer to "owner") else listOf(viewer to "member", author to "owner")).map { (user, role) ->
            mapOf("uuid" to "$user-$index", "stream_uuid" to id(0, index), "user_uuid" to user, "role" to role) } }
        val executor = Executors.newCachedThreadPool { task -> Thread(task, "cassi-navigation-fixture").apply {
            isDaemon = true
            contextClassLoader = JsonOutput::class.java.classLoader
        } }
        val stopped = CountDownLatch(1)
        val shutdown = Thread({ stopped.countDown() }, "cassi-navigation-fixture-shutdown")
        Runtime.getRuntime().addShutdownHook(shutdown)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
        server.executor = executor
        val logLock = Any()
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
                val body: Any = when {
                    "/server_settings/" in path -> mapOf("email_auth_enabled" to true, "realm_name" to "Navigation fixture",
                        "realm_url" to "http://127.0.0.1:19557", "realm_icon" to "", "meet_url" to "https://meet.invalid")
                    "/api/core/v1/iam/projects/" in path -> listOf(mapOf("uuid" to "project", "name" to "Navigation fixture", "description" to ""))
                    "/me/" in path -> person(viewer, "Viewer")
                    "/users/" in path -> listOf(person(viewer, "Viewer"), person(author, "Alice"))
                    "/epoch/" in path -> mapOf("epoch_version" to 1, "epoch_generation" to 1)
                    "/actions/read" in path -> emptyMap<String, String>()
                    "/drafts/" in path -> filtered(drafts)
                    "/stream_bindings/" in path -> filtered(bindings)
                    "/stream_topics/" in path -> filtered(topics)
                    "/streams/" in path -> filtered(streams)
                    path.endsWith("/messages/") -> filtered(messages)
                    else -> emptyList<Any>()
                }
                val bytes = JsonOutput.toJson(body).toByteArray(Charsets.UTF_8)
                exchange.responseHeaders.set("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.write(bytes)
                synchronized(logLock) {
                    root.resolve("navigation-network.jsonl").appendText(JsonOutput.toJson(mapOf(
                        "path" to path, "method" to exchange.requestMethod, "status" to 200,
                        "stream_uuid" to query["stream_uuid"], "topic_uuid" to query["topic_uuid"])) + "\n")
                }
            } catch (error: Exception) {
                logger.error("Synthetic navigation fixture request failed", error)
                runCatching { exchange.sendResponseHeaders(500, -1) }
            } finally { exchange.close() }
        }
        server.start()
        logger.lifecycle("Navigation loopback fixture ready")
        try { stopped.await() } finally {
            server.stop(0)
            executor.shutdownNow()
            runCatching { Runtime.getRuntime().removeShutdownHook(shutdown) }
        }
    }
}
