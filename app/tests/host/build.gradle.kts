import com.sun.net.httpserver.HttpServer
import groovy.json.JsonOutput
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import javax.imageio.ImageIO

apply(from = "encrypted-runtime.gradle.kts")

// Host-only synthetic origins for the optimized APK. Uses the project's Gradle JDK.
tasks.register("runAvatarFixture") {
    group = "verification"
    description = "Runs synthetic loopback API/image origins for avatar release checks."
    notCompatibleWithConfigurationCache("Runs a long-lived local HTTP fixture")

    doLast {
        val root = file(providers.gradleProperty("avatarFixtureArtifacts").get()).absoluteFile
        root.mkdirs()
        val modeFile = root.resolve("release-mode.txt")
        if (!modeFile.exists()) modeFile.writeText("external")
        val image = BufferedImage(48, 48, BufferedImage.TYPE_INT_RGB).apply {
            for (y in 0 until height) for (x in 0 until width) setRGB(x, y, 0x226ed7)
        }
        val png = ByteArrayOutputStream().use { output ->
            check(ImageIO.write(image, "png", output))
            output.toByteArray()
        }
        val imageIds = mapOf(
            "image" to "00000000-0000-4000-8000-000000000051",
            "redirect_port" to "00000000-0000-4000-8000-000000000052",
            "redirect_host" to "00000000-0000-4000-8000-000000000053",
            "same_origin" to "00000000-0000-4000-8000-000000000054",
        )
        val modes = imageIds.keys + setOf("external", "fallback")
        val logLock = Any()
        val executor = Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "cassi-avatar-fixture").apply {
                isDaemon = true
                contextClassLoader = JsonOutput::class.java.classLoader
            }
        }
        val servers = mutableListOf<HttpServer>()
        val stopped = CountDownLatch(1)
        val shutdown = Thread({ stopped.countDown() }, "cassi-avatar-fixture-shutdown")
        Runtime.getRuntime().addShutdownHook(shutdown)
        try {
            for (port in listOf(19557, 19558)) {
                val server = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
                servers += server
                server.executor = executor
                server.createContext("/") { exchange ->
                    try {
                        val mode = modeFile.readText().trim()
                        require(mode in modes) { "Unknown avatar fixture mode" }
                        val path = exchange.requestURI.path
                        val authorization = exchange.requestHeaders.getFirst("Authorization")
                        val event = mapOf(
                            "server" to port, "mode" to mode, "path" to path,
                            "auth_present" to !authorization.isNullOrEmpty(),
                            "auth_matches_fixture" to (authorization == "Bearer cassi-avatar-57-access"),
                        )
                        synchronized(logLock) {
                            root.resolve("release-network.jsonl").appendText(JsonOutput.toJson(event) + "\n")
                        }
                        if (path.endsWith(".png") || "/actions/download" in path) {
                            val redirect = if ("/actions/download" in path) when (mode) {
                                "redirect_port" -> "http://127.0.0.1:19558/redirect_port.png"
                                "redirect_host" -> "http://localhost:19558/redirect_host.png"
                                "same_origin" -> "/same-origin.png"
                                else -> null
                            } else null
                            if (redirect != null) {
                                exchange.responseHeaders.set("Location", redirect)
                                exchange.sendResponseHeaders(302, -1)
                            } else {
                                exchange.responseHeaders.set("Content-Type", "image/png")
                                exchange.responseHeaders.set("Cache-Control", "no-store")
                                exchange.sendResponseHeaders(200, png.size.toLong())
                                exchange.responseBody.write(png)
                            }
                        } else {
                            val avatar = when {
                                mode == "external" -> "urn:url:http://127.0.0.1:19558/external/:image:/avatar.png"
                                mode in imageIds -> "urn:image:${imageIds.getValue(mode)}"
                                else -> ""
                            }
                            val user = mapOf(
                                "first_name" to "Example", "last_name" to "User", "username" to "cassi-fixture",
                                "uuid" to "00000000-0000-4000-8000-000000000057", "status" to "active", "avatar" to avatar,
                            )
                            val body = when {
                                "/api/core/v1/iam/projects/" in path -> listOf(mapOf(
                                    "uuid" to "project", "name" to "Avatar security fixture", "description" to "",
                                ))
                                "/server_settings/" in path -> mapOf(
                                    "email_auth_enabled" to true, "realm_name" to "Avatar fixture",
                                    "realm_url" to "http://127.0.0.1:19557", "realm_icon" to "",
                                    "meet_url" to "https://meet.invalid",
                                )
                                "/me/" in path -> user
                                "/users/" in path -> listOf(user)
                                "/epoch/" in path -> mapOf("epoch_version" to 1, "epoch_generation" to 1)
                                else -> emptyList<Any>()
                            }
                            val data = JsonOutput.toJson(body).toByteArray(Charsets.UTF_8)
                            exchange.responseHeaders.set("Content-Type", "application/json")
                            exchange.sendResponseHeaders(200, data.size.toLong())
                            exchange.responseBody.write(data)
                        }
                    } catch (failure: Exception) {
                        logger.error("Synthetic avatar fixture request failed", failure)
                        exchange.sendResponseHeaders(500, -1)
                    } finally {
                        exchange.close()
                    }
                }
                server.start()
            }
            logger.lifecycle("Synthetic loopback fixture ready")
            stopped.await()
        } finally {
            servers.forEach { it.stop(0) }
            executor.shutdownNow()
            runCatching { Runtime.getRuntime().removeShutdownHook(shutdown) }
        }
    }
}
