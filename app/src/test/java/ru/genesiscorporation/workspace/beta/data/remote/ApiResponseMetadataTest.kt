package ru.genesiscorporation.workspace.beta.data.remote

import io.ktor.http.Headers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApiResponseMetadataTest {
    @Test
    fun `pagination marker is retained without retaining sensitive headers`() {
        val metadata = apiResponseMetadata(
            Headers.build {
                append(
                    "x-pagination-marker",
                    "11111111-1111-4111-8111-111111111111",
                )
                append("ETag", "\"2\"")
                append("Set-Cookie", "must-not-leave-the-response")
            },
        )

        assertEquals(
            ApiResponseMetadata(
                nextPageMarker = "11111111-1111-4111-8111-111111111111",
                entityTag = "\"2\"",
            ),
            metadata,
        )
    }

    @Test
    fun `missing pagination header produces empty metadata`() {
        assertNull(
            apiResponseMetadata(Headers.Empty).nextPageMarker,
        )
        assertNull(apiResponseMetadata(Headers.Empty).entityTag)
    }

    @Test
    fun `weak and malformed entity tags are discarded`() {
        for (value in listOf("W/\"1\"", "\"0\"", "\"01\"", "1")) {
            assertNull(
                apiResponseMetadata(
                    Headers.build { append("ETag", value) },
                ).entityTag,
            )
        }
    }
}
