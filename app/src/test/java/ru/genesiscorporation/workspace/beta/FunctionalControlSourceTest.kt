package ru.genesiscorporation.workspace.beta

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class FunctionalControlSourceTest {
    @Test
    fun productionSourcesContainNoKnownDeadControlOrMockPatterns() {
        val sourceRoot = locateProductionSourceRoot()
        val forbidden = linkedMapOf(
            "empty onClick callback" to Regex("""onClick\s*=\s*\{\s*}"""),
            "empty clickable callback" to Regex("""\.clickable\s*\{\s*}"""),
            "default empty click callback" to Regex(
                """onClick\s*:\s*\(\)\s*->\s*Unit\s*=\s*\{\s*}""",
            ),
            "unfinished TODO implementation" to Regex("""\bTODO\s*\("""),
            "not-implemented exception" to Regex("""\bNotImplementedError\b"""),
            "mock production model" to Regex("""\bisMock[A-Za-z]*\b"""),
            "demo production component" to Regex("""\bDemo[A-Z][A-Za-z]*\b"""),
            "placeholder loading label" to Regex(""""Loading""""),
            "unavailable placeholder copy" to Regex("""пока\s+недоступ""", RegexOption.IGNORE_CASE),
        )

        val failures = mutableListOf<String>()
        Files.walk(sourceRoot).use { paths ->
            paths
                .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".kt") }
                .forEach { path ->
                    val text = path.readText()
                    forbidden.forEach { (label, pattern) ->
                        pattern.findAll(text).forEach { match ->
                            val line = text.take(match.range.first).count { it == '\n' } + 1
                            failures += "${sourceRoot.relativize(path)}:$line: $label"
                        }
                    }
                }
        }

        assertTrue(
            "Production controls must be implemented or hidden:\n${failures.joinToString("\n")}",
            failures.isEmpty(),
        )
    }

    private fun locateProductionSourceRoot(): Path {
        val workingDirectory = Path.of(System.getProperty("user.dir"))
        return sequenceOf(
            workingDirectory.resolve("src/main/java"),
            workingDirectory.resolve("app/src/main/java"),
        ).firstOrNull(Files::isDirectory)
            ?: error("Could not locate Android production sources from $workingDirectory")
    }
}
