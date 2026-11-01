import com.diffplug.spotless.LineEnding
import java.nio.ByteBuffer

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    base
    alias(libs.plugins.spotless)
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias { libs.plugins.firebase } apply false
    alias(libs.plugins.crashlytics) apply false
}

// Respect Git ignores, including local credentials/configuration and build outputs.
val repositoryTextFiles = providers.exec {
    commandLine("git", "ls-files", "-z", "--cached", "--others", "--exclude-standard")
}.standardOutput.asText.map { paths ->
    paths.split('\u0000').filter { it.isNotEmpty() }.map(::file).filter { candidate ->
        candidate.isFile && candidate.readBytes().let { bytes ->
            0.toByte() !in bytes && runCatching {
                Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes))
            }.isSuccess
        }
    }
}

spotless {
    format("text") {
        target(repositoryTextFiles)
        lineEndings = LineEnding.PRESERVE
        endWithNewline()
    }
}

subprojects {
    tasks.matching { it.name == "check" || it.name.startsWith("lint") }.configureEach {
        dependsOn(rootProject.tasks.named("spotlessCheck"))
    }
}
