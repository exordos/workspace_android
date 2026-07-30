package ru.genesiscorporation.workspace.beta.data.remote

import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BoundedResponseBodyTest {
    @Test
    fun acceptsBodyAtExactLimit() = runBlocking {
        val body = byteArrayOf(1, 2, 3, 4)

        assertArrayEquals(
            body,
            readBytesWithLimit(ByteReadChannel(body), maxBytes = body.size),
        )
    }

    @Test
    fun rejectsBodyBeyondLimitWithoutBufferingRemainder() = runBlocking {
        val body = ByteArray(128 * 1024) { 7 }

        assertNull(
            readBytesWithLimit(ByteReadChannel(body), maxBytes = 1024),
        )
    }
}
