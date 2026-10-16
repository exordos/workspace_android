import com.google.gms.googleservices.GoogleServicesTask
import com.google.gson.JsonParser
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.firebase)
}

android {
    namespace = "ru.genesiscorporation.workspace.beta"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    flavorDimensions += "brand"
    productFlavors {
        create("genesis") {
            dimension = "brand"
            applicationId = "ru.genesiscorporation.workspace.beta"
        }
        create("exordos") {
            dimension = "brand"
            applicationId = "com.exordos.workspace"
        }
    }

    defaultConfig {
        minSdk = 30
        targetSdk = 36
        versionCode = 35
        versionName = "1.3.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        getByName("test").setRoot("tests/unit")
        getByName("androidTest").setRoot("tests/instrumented")
        if (providers.gradleProperty("firebaseLiveSmoke").orNull == "true") {
            getByName("androidTest").kotlin.srcDir("tests/live/java")
        }
    }
}

// Validate the exact file/client selected by the Google services plugin before packaging.
tasks.withType<GoogleServicesTask>().configureEach {
    if (name.endsWith("ReleaseGoogleServices")) {
        doFirst {
            val invalid = "Release requires a valid Firebase Android configuration for ${applicationId.get()}. " +
                "Download google-services.json from Firebase Console; see docs/development.md."
            val valid = runCatching {
                val config = googleServicesJsonFiles.get().first { it.isFile }
                val json = JsonParser.parseString(config.readText()).asJsonObject
                val project = json.getAsJsonObject("project_info")
                val number = project.get("project_number").asString
                val client = json.getAsJsonArray("client").first {
                    it.asJsonObject.getAsJsonObject("client_info")
                        .getAsJsonObject("android_client_info").get("package_name").asString == applicationId.get()
                }.asJsonObject
                val appId = client.getAsJsonObject("client_info").get("mobilesdk_app_id").asString
                val apiKey = client.getAsJsonArray("api_key").firstNotNullOf {
                    it.asJsonObject.get("current_key")?.takeUnless { key -> key.isJsonNull }?.asString
                }
                number.matches(Regex("[0-9]+")) &&
                    project.get("project_id").asString.isNotBlank() &&
                    appId.matches(Regex("1:$number:android:[0-9a-f]+")) &&
                    apiKey.matches(Regex("A[\\w-]{38}"))
            }.getOrDefault(false)
            // Do not include parser exceptions or configuration values in build output.
            if (!valid) throw GradleException(invalid)
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.navigation.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.security.crypto)
    implementation("com.google.crypto.tink:tink-android:1.8.0")
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.serialization)
    implementation(libs.ktor.client.serialization)
    implementation(libs.ktor.client.negotiation)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.client.websockets)
    implementation(libs.jetbrains.kotlin.serialization.json)
    implementation(libs.androidx.data.store.preferencies)
    implementation(libs.jitsi.meet)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.properties)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.cloud.messaging)
    implementation(libs.androidx.material3)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.coil.network)
    implementation(libs.compose.markdown)
    implementation(libs.androidx.app.compat)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.fellbaum.jemoji)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

tasks.register<JavaCompile>("compileEmojiReleaseSmoke") {
    source("tests/release/EmojiReleaseInstrumentation.java",
        "tests/release/FirebaseNotificationReleaseInstrumentation.java",
        "tests/instrumented/java/com/exordos/workspace/cassi/releasetest/AvatarFixtureInstrumentation.java")
    classpath = files(providers.provider { androidComponents.sdkComponents.bootClasspath.get() })
    destinationDirectory.set(layout.buildDirectory.dir("emoji-release-smoke/classes"))
    sourceCompatibility = "11"
    targetCompatibility = "11"
    options.encoding = "UTF-8"
}

tasks.register("buildEmojiReleaseSmoke") {
    val androidJar = providers.provider {
        androidComponents.sdkComponents.bootClasspath.get().single { it.asFile.name == "android.jar" }.asFile
    }
    val buildTools = providers.provider {
        androidComponents.sdkComponents.sdkDirectory.get().dir("build-tools/${android.buildToolsVersion}").asFile
    }
    val compileSmoke = tasks.named<JavaCompile>("compileEmojiReleaseSmoke")
    group = "verification"
    description = "Builds an unsigned framework-only test APK for the optimized Exordos release."
    dependsOn(compileSmoke)
    val output = layout.buildDirectory.file("outputs/emoji-release-smoke/emoji-release-smoke-unsigned.apk")
    inputs.file("build.gradle.kts")
    inputs.dir(compileSmoke.flatMap { it.destinationDirectory })
    inputs.file(androidJar)
    inputs.dir(buildTools)
    outputs.file(output)

    doLast {
        val work = temporaryDir
        work.deleteRecursively()
        work.mkdirs()
        val dex = work.resolve("dex").apply { mkdirs() }
        val manifest = work.resolve("AndroidManifest.xml")
        manifest.writeText("""
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="com.exordos.workspace.cassi.releasetest">
                <uses-sdk android:minSdkVersion="30" android:targetSdkVersion="36"/>
                <application android:testOnly="true" android:debuggable="false"/>
                <instrumentation android:name="com.exordos.workspace.cassi.releasetest.EmojiReleaseInstrumentation"
                    android:targetPackage="com.exordos.workspace"/>
                <instrumentation android:name="com.exordos.workspace.cassi.releasetest.AvatarFixtureInstrumentation"
                    android:targetPackage="com.exordos.workspace"/>
                <instrumentation android:name="com.exordos.workspace.cassi.releasetest.FirebaseNotificationReleaseInstrumentation"
                    android:targetPackage="com.exordos.workspace"/>
            </manifest>
        """.trimIndent(), Charsets.UTF_8)
        val tools = buildTools.get()
        val executableSuffix = if (System.getProperty("os.name").startsWith("Windows")) ".exe" else ""
        fun runTool(vararg arguments: String) {
            check(ProcessBuilder(arguments.toList()).inheritIO().start().waitFor() == 0) {
                "Release smoke build tool failed: ${arguments.first()}"
            }
        }
        val classes = compileSmoke.get().destinationDirectory.get().asFile
            .walkTopDown().filter { it.extension == "class" }.map { it.path }.sorted().toList()
        runTool(File(System.getProperty("java.home"), "bin/java$executableSuffix").path,
            "-cp", tools.resolve("lib/d8.jar").path, "com.android.tools.r8.D8",
            "--lib", androidJar.get().path, "--min-api", "30", "--output", dex.path,
            *classes.toTypedArray())
        val linked = work.resolve("linked.apk")
        runTool(tools.resolve("aapt2$executableSuffix").path, "link", "-I", androidJar.get().path,
            "--manifest", manifest.path, "-o", linked.path)
        val raw = work.resolve("raw.apk")
        ZipFile(linked).use { original ->
            ZipOutputStream(raw.outputStream()).use { apk ->
                original.entries().asSequence().forEach { entry ->
                    apk.putNextEntry(ZipEntry(entry))
                    original.getInputStream(entry).use { it.copyTo(apk) }
                    apk.closeEntry()
                }
                dex.listFiles().orEmpty().filter { it.extension == "dex" }.forEach { file ->
                    apk.putNextEntry(ZipEntry(file.name))
                    file.inputStream().use { it.copyTo(apk) }
                    apk.closeEntry()
                }
            }
        }
        output.get().asFile.parentFile.mkdirs()
        runTool(tools.resolve("zipalign$executableSuffix").path, "-f", "-P", "16", "4",
            raw.path, output.get().asFile.path)
        logger.lifecycle("Unsigned release smoke APK: ${output.get().asFile}")
    }
}
