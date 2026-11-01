import groovy.json.JsonOutput
import java.util.Properties

tasks.register("verifyFirebaseReleaseConfig") {
    group = "verification"
    description = "Exercises the real Google services tasks with isolated synthetic configuration."
    notCompatibleWithConfigurationCache("Launches isolated Gradle builds")
    doLast {
        val source = projectDir.resolve("../../..").canonicalFile
        val fixture = temporaryDir.resolve("project").apply { deleteRecursively(); mkdirs() }
        for (name in listOf("build.gradle.kts", "settings.gradle.kts", "gradle.properties",
            "gradle/libs.versions.toml", "gradle/gradle-daemon-jvm.properties")) {
            source.resolve(name).copyTo(fixture.resolve(name).apply { parentFile.mkdirs() })
        }
        val buildScript = providers.gradleProperty("firebaseBuildScript").orNull?.let(::File)
            ?: source.resolve("app/build.gradle.kts")
        buildScript.copyTo(fixture.resolve("app/build.gradle.kts").apply { parentFile.mkdirs() })
        val local = Properties().apply {
            source.resolve("local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
        }
        local.getProperty("sdk.dir")?.let {
            Properties().apply { setProperty("sdk.dir", it) }.also { sdk ->
                fixture.resolve("local.properties").outputStream().use { out -> sdk.store(out, null) }
            }
        }
        fixture.resolve("app/src/main/AndroidManifest.xml").apply {
            parentFile.mkdirs()
            writeText("""<manifest xmlns:android="http://schemas.android.com/apk/res/android"><application/></manifest>""")
        }
        val configFile = fixture.resolve("app/google-services.json")
        val overrideFile = fixture.resolve("app/src/exordosRelease/google-services.json")
        val gradleCommand = File(requireNotNull(gradle.gradleHomeDir), "bin/gradle").path
        val number = "123456789"
        fun client(packageName: String, key: String = "A" + "x".repeat(38),
            appId: String = "1:$number:android:cafebabecafebabe") = mapOf(
            "client_info" to mapOf("mobilesdk_app_id" to appId,
                "android_client_info" to mapOf("package_name" to packageName)),
            "api_key" to listOf(mapOf("current_key" to key)),
        )
        fun config(vararg clients: Map<String, Any>) = JsonOutput.toJson(mapOf(
            "project_info" to mapOf("project_number" to number, "project_id" to "cassi-firebase-fixture"),
            "client" to clients.toList(), "configuration_version" to "1",
        ))
        val good = config(client("com.exordos.workspace"))
        val placeholder = config(client("com.exordos.workspace", "cassi-synthetic-key"))
        fun runCase(name: String, success: Boolean, variant: String = "ExordosRelease", setup: () -> Unit) {
            configFile.delete()
            overrideFile.delete()
            setup()
            val process = ProcessBuilder(gradleCommand, "--offline", "--no-daemon", "--console=plain",
                "--max-workers=1", "-Dorg.gradle.jvmargs=-Xmx512m", "-p", fixture.path,
                ":app:process${variant}GoogleServices").redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            val passed = process.waitFor() == 0
            temporaryDir.resolve("$name.txt").writeText(output)
            check(passed == success) { "$name: expected success=$success; see isolated build output" }
            if (!success) check("Release requires a valid Firebase Android configuration" in output) {
                "$name failed outside the release configuration guard"
            }
            check("CASSI_CONFIG_VALUE_MUST_NOT_LEAK" !in output) { "$name exposed configuration contents" }
            logger.lifecycle("Firebase configuration regression: $name PASS")
        }
        runCase("placeholder-key", false) { configFile.writeText(placeholder) }
        runCase("valid-exordos-client", true) { configFile.writeText(good) }
        runCase("missing-file", false) {}
        runCase("malformed-json-redacted", false) {
            configFile.writeText("{ CASSI_CONFIG_VALUE_MUST_NOT_LEAK")
        }
        runCase("wrong-app-id-project", false) {
            configFile.writeText(config(client("com.exordos.workspace", appId = "1:987654321:android:cafebabe")))
        }
        runCase("unrelated-client-does-not-break-exordos", true) {
            configFile.writeText(config(client("com.exordos.workspace"),
                client("com.example.unrelated", "cassi-synthetic-key")))
        }
        runCase("matching-exordos-client-is-checked", false) {
            configFile.writeText(config(client("com.example.unrelated"),
                client("com.exordos.workspace", "cassi-synthetic-key")))
        }
        runCase("invalid-variant-overrides-valid-root", false) {
            configFile.writeText(good); overrideFile.parentFile.mkdirs(); overrideFile.writeText(placeholder)
        }
        runCase("valid-variant-overrides-invalid-root", true) {
            configFile.writeText(placeholder); overrideFile.parentFile.mkdirs(); overrideFile.writeText(good)
        }
        runCase("invalid-first-key-is-not-skipped", false) {
            configFile.writeText(good.replace("\"api_key\":[", "\"api_key\":[{\"current_key\":\"cassi-synthetic-key\"},"))
        }
        runCase("debug-synthetic-config-still-supported", true, "ExordosDebug") {
            configFile.writeText(placeholder)
        }
    }
}
