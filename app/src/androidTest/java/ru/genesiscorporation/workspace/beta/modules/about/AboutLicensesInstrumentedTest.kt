package ru.genesiscorporation.workspace.beta.modules.about

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mikepenz.aboutlibraries.Libs
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.genesiscorporation.workspace.beta.R

@RunWith(AndroidJUnit4::class)
class AboutLicensesInstrumentedTest {
    @Test
    fun generatedRuntimeLicenseCatalogIsBundledAndReadableOffline() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val catalogJson = context.resources
            .openRawResource(R.raw.aboutlibraries)
            .bufferedReader()
            .use { it.readText() }
        val libraries = Libs.Builder()
            .withJson(catalogJson)
            .build()

        assertTrue("The generated catalog must not be blank", catalogJson.isNotBlank())
        assertTrue(
            "The installed build must identify its runtime components",
            libraries.libraries.isNotEmpty(),
        )
        assertTrue(
            "At least one bundled component must expose a license",
            libraries.libraries.any { it.licenses.isNotEmpty() },
        )
        assertTrue(
            "At least one license must include readable offline text",
            libraries.libraries
                .flatMap { it.licenses }
                .any { !it.licenseContent.isNullOrBlank() },
        )
    }
}
