package ru.genesiscorporation.workspace.beta.modules.about

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AboutScreenModelTest {
    @Test
    fun versionAndBuildLabelsExposeExactBuildIdentity() {
        assertEquals(
            "Версия 1.0.6 (15)",
            formatVersionLabel(
                AppBuildDetails(
                    versionName = "1.0.6",
                    versionCode = 15,
                    buildType = "debug",
                ),
            ),
        )
        assertEquals("Тип сборки: отладочная", formatBuildTypeLabel("DEBUG"))
        assertEquals("Тип сборки: релизная", formatBuildTypeLabel(" release "))
    }

    @Test
    fun missingAndCustomBuildValuesRemainExplicit() {
        assertEquals(
            "Версия не указана (0)",
            formatVersionLabel(
                AppBuildDetails(
                    versionName = " ",
                    versionCode = 0,
                    buildType = "",
                ),
            ),
        )
        assertEquals("Тип сборки: не указана", formatBuildTypeLabel(" "))
        assertEquals("Тип сборки: benchmark", formatBuildTypeLabel("Benchmark"))
    }

    @Test
    fun librarySearchMatchesNameCoordinatesVersionAndLicenseWithoutLocaleDrift() {
        val values = listOf(
            "Ktor HTTP Client",
            "io.ktor:ktor-client-core",
            "3.4.0",
            "Apache License 2.0",
        )

        assertTrue(matchesLibrarySearch("", values))
        assertTrue(matchesLibrarySearch("  KTOR  ", values))
        assertTrue(matchesLibrarySearch("client-core", values))
        assertTrue(matchesLibrarySearch("3.4", values))
        assertTrue(matchesLibrarySearch("apache", values))
        assertFalse(matchesLibrarySearch("mit", values))
    }

    @Test
    fun libraryCountDistinguishesLoadingFullAndFilteredStates() {
        assertEquals(
            "Формируем список из текущей сборки…",
            libraryCountLabel(null, null, hasQuery = false),
        )
        assertEquals(
            "Компонентов в текущей сборке: 42",
            libraryCountLabel(42, 42, hasQuery = false),
        )
        assertEquals(
            "Найдено 3 из 42",
            libraryCountLabel(42, 3, hasQuery = true),
        )
    }

    @Test
    fun missingLicenseCopyIsExplicitAndKeepsAnAvailableSource() {
        assertEquals(
            "Полный текст лицензии не включён в метаданные сборки.",
            missingLicenseTextMessage(null),
        )
        assertEquals(
            "Полный текст лицензии не включён в метаданные сборки.\n" +
                "Источник: https://example.test/license",
            missingLicenseTextMessage(" https://example.test/license "),
        )
        val hostileSource = "https://example.test/\n" + "x".repeat(600)
        val rendered = missingLicenseTextMessage(hostileSource)
        assertFalse(rendered.substringAfter("Источник:").contains('\n'))
        assertEquals(
            512,
            rendered.substringAfter("Источник: ").length,
        )
    }
}
