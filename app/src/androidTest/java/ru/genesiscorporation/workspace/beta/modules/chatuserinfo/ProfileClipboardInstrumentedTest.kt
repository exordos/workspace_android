package ru.genesiscorporation.workspace.beta.modules.chatuserinfo

import android.content.ClipboardManager
import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.genesiscorporation.workspace.beta.MainActivity

@RunWith(AndroidJUnit4::class)
class ProfileClipboardInstrumentedTest {
    @Test
    fun copyWritesTheExactPlainTextAndBoundedLabel() {
        val value = "profile-value@example.test"

        withForegroundClipboard { context, clipboard ->
            clipboard.clearPrimaryClip()
            try {
                assertTrue(
                    copyPlainProfileText(
                        context = context,
                        label = "L".repeat(80),
                        value = value,
                    ),
                )
                val clip = clipboard.primaryClip
                assertEquals(1, clip?.itemCount)
                assertEquals("L".repeat(64), clip?.description?.label?.toString())
                assertEquals(
                    value,
                    clip?.getItemAt(0)?.coerceToText(context)?.toString(),
                )
            } finally {
                clipboard.clearPrimaryClip()
            }
        }
    }

    @Test
    fun blankValueIsRejectedWithoutReplacingClipboard() {
        withForegroundClipboard { context, clipboard ->
            clipboard.clearPrimaryClip()
            assertFalse(copyPlainProfileText(context, "Email", "   "))
            assertFalse(clipboard.hasPrimaryClip())
        }
    }

    private fun withForegroundClipboard(
        block: (Context, ClipboardManager) -> Unit,
    ) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                block(
                    activity,
                    activity.getSystemService(ClipboardManager::class.java),
                )
            }
        }
    }
}
