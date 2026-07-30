package ru.genesiscorporation.workspace.beta.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AvatarValidationTest {
    @Test
    fun `detects only server-supported image signatures`() {
        assertEquals(
            "image/png",
            detectAvatarMime(
                byteArrayOf(
                    0x89.toByte(),
                    0x50,
                    0x4E,
                    0x47,
                    0x0D,
                    0x0A,
                    0x1A,
                    0x0A,
                ),
            ),
        )
        assertEquals(
            "image/jpeg",
            detectAvatarMime(
                byteArrayOf(
                    0xFF.toByte(),
                    0xD8.toByte(),
                    0xFF.toByte(),
                    0x00,
                ),
            ),
        )
        assertEquals(
            "image/gif",
            detectAvatarMime("GIF89a".encodeToByteArray()),
        )
        assertEquals(
            "image/webp",
            detectAvatarMime("RIFF0000WEBP".encodeToByteArray()),
        )
        assertNull(detectAvatarMime("<svg></svg>".encodeToByteArray()))
        assertNull(detectAvatarMime(byteArrayOf()))
    }

    @Test
    fun `normalizes declared image mime without accepting arbitrary files`() {
        assertEquals("image/jpeg", normalizeAvatarMime("image/jpg"))
        assertEquals("image/png", normalizeAvatarMime(" IMAGE/PNG; charset=binary "))
        assertEquals("unsupported", normalizeAvatarMime("application/octet-stream"))
        assertNull(normalizeAvatarMime(null))
        assertNull(normalizeAvatarMime(" "))
    }
}
