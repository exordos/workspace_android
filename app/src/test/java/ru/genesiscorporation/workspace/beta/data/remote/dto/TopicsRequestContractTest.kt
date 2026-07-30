package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.properties.Properties
import kotlinx.serialization.properties.encodeToStringMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalSerializationApi::class)
class TopicsRequestContractTest {
    @Test
    fun `stream topic request sends exact stream UUID`() {
        val params = Properties.encodeToStringMap(
            TopicsRequest("11111111-1111-4111-8111-111111111111").data,
        )

        assertEquals(
            "11111111-1111-4111-8111-111111111111",
            params["stream_uuid"],
        )
    }

    @Test
    fun `all topics request omits stream filter`() {
        val params = Properties.encodeToStringMap(TopicsRequest().data)

        assertFalse(params.containsKey("stream_uuid"))
    }
}
