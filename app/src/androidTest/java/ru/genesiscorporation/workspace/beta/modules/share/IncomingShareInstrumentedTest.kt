package ru.genesiscorporation.workspace.beta.modules.share

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IncomingShareInstrumentedTest {
    @Test
    fun sendTextCombinesSubjectAndBody() {
        val savedRequestId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        val request = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "Release")
            .putExtra(Intent.EXTRA_TEXT, "Body")
            .toIncomingShareRequestOrNull(savedRequestId)

        assertNotNull(request)
        assertEquals(savedRequestId, request?.requestId)
        assertEquals("Release\n\nBody", request?.text)
        assertTrue(request?.attachmentUris.orEmpty().isEmpty())
        assertNull(request?.validationError)
    }

    @Test
    fun sendMultipleDeduplicatesClipAndExtraUris() {
        val first = Uri.parse("content://provider/first")
        val second = Uri.parse("content://provider/second")
        val request = Intent(Intent.ACTION_SEND_MULTIPLE)
            .setType("image/png")
            .putParcelableArrayListExtra(
                Intent.EXTRA_STREAM,
                arrayListOf(first, second, first),
            )
            .toIncomingShareRequestOrNull()

        assertEquals(listOf(first, second), request?.attachmentUris)
        assertNull(request?.validationError)
    }

    @Test
    fun tooManyAttachmentsAreRejectedWithoutSilentlyDroppingTheCondition() {
        val uris = ArrayList(
            (0..MAX_INCOMING_ATTACHMENTS).map {
                Uri.parse("content://provider/file-$it")
            },
        )
        val request = Intent(Intent.ACTION_SEND_MULTIPLE)
            .setType("*/*")
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            .toIncomingShareRequestOrNull()

        assertNotNull(request?.validationError)
        assertEquals(MAX_INCOMING_ATTACHMENTS, request?.attachmentUris?.size)
    }

    @Test
    fun unrelatedIntentIsNotConsumedAsShare() {
        assertNull(
            Intent(Intent.ACTION_VIEW)
                .setData(Uri.parse("https://workspace.exordos.com"))
                .toIncomingShareRequestOrNull(),
        )
    }
}
