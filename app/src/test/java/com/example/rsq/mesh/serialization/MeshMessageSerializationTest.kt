package com.example.rsq.mesh.serialization

import com.example.rsq.data.model.Priority
import com.example.rsq.mesh.model.MeshMessage
import com.example.rsq.mesh.model.MeshMessageType
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class MeshMessageSerializationTest {

    @Test
    fun `MeshMessage should survive round trip serialization`() {
        val original = MeshMessage(
            id = "msg-123",
            senderNodeId = "node-a",
            originNodeId = "node-origin",
            messageType = MeshMessageType.SOS,
            timestamp = 1625097600000L,
            latitude = 12.3456,
            longitude = 78.9012,
            priority = Priority.HIGH,
            payload = "Help needed at main square",
            ttl = 3
        )

        val json = Json.encodeToString(original)
        val deserialized = Json.decodeFromString<MeshMessage>(json)

        assertEquals(original, deserialized)
    }

    @Test
    fun `MeshMessage with null coordinates should survive round trip serialization`() {
        val original = MeshMessage(
            id = "msg-456",
            senderNodeId = "node-b",
            originNodeId = "node-origin-2",
            messageType = MeshMessageType.REPORT_RELAY,
            timestamp = 1625097600001L,
            latitude = null,
            longitude = null,
            priority = Priority.MEDIUM,
            payload = "Power outage reported",
            ttl = 5
        )

        val json = Json.encodeToString(original)
        val deserialized = Json.decodeFromString<MeshMessage>(json)

        assertEquals(original, deserialized)
    }

    @Test
    fun `MeshMessage should handle all MeshMessageType values`() {
        MeshMessageType.entries.forEach { type ->
            val original = MeshMessage(
                id = "msg-${type.name}",
                senderNodeId = "node-x",
                originNodeId = "node-y",
                messageType = type,
                timestamp = System.currentTimeMillis(),
                latitude = 0.0,
                longitude = 0.0,
                priority = Priority.LOW,
                payload = "Testing type ${type.name}",
                ttl = 1
            )

            val json = Json.encodeToString(original)
            val deserialized = Json.decodeFromString<MeshMessage>(json)

            assertEquals(original, deserialized)
        }
    }

    @Test
    fun `MeshMessage should handle all Priority values`() {
        Priority.entries.forEach { priority ->
            val original = MeshMessage(
                id = "msg-${priority.name}",
                senderNodeId = "node-x",
                originNodeId = "node-y",
                messageType = MeshMessageType.ACKNOWLEDGEMENT,
                timestamp = System.currentTimeMillis(),
                latitude = 0.0,
                longitude = 0.0,
                priority = priority,
                payload = "Testing priority ${priority.name}",
                ttl = 1
            )

            val json = Json.encodeToString(original)
            val deserialized = Json.decodeFromString<MeshMessage>(json)

            assertEquals(original, deserialized)
        }
    }

    @Test
    fun `Decode a mesh message containing an unknown JSON field without throwing a serialization exception`() {
        // Simulates an older RSQ client using `ignoreUnknownKeys = true` decoding a message from a newer app version.
        val meshJson = Json { ignoreUnknownKeys = true }
        val jsonWithUnknownField = """
            {
                "id": "msg-123",
                "senderNodeId": "node-a",
                "originNodeId": "node-origin",
                "messageType": "SOS",
                "timestamp": 1625097600000,
                "latitude": 12.3456,
                "longitude": 78.9012,
                "priority": "HIGH",
                "payload": "Help",
                "ttl": 3,
                "future_unknown_field": "some_value"
            }
        """.trimIndent()
        
        val deserialized = meshJson.decodeFromString<MeshMessage>(jsonWithUnknownField)
        assertEquals("msg-123", deserialized.id)
    }

    @Test
    fun `Decode a legacy mesh message that does not contain expirationTimestamp`() {
        val meshJson = Json { ignoreUnknownKeys = true }
        val legacyJson = """
            {
                "id": "msg-legacy",
                "senderNodeId": "node-a",
                "originNodeId": "node-origin",
                "messageType": "SOS",
                "timestamp": 1625097600000,
                "latitude": 12.3456,
                "longitude": 78.9012,
                "priority": "HIGH",
                "payload": "Help",
                "ttl": 3
            }
        """.trimIndent()
        
        val deserialized = meshJson.decodeFromString<MeshMessage>(legacyJson)
        assertEquals("msg-legacy", deserialized.id)
        assertEquals(1625097600000L + 24 * 60 * 60 * 1000L, deserialized.expirationTimestamp)
    }

    @Test(expected = Exception::class)
    fun `Malformed JSON should throw exception during deserialization`() {
        val malformedJson = "{ \"id\": \"msg-1\", \"invalid_field\": true }"
        Json.decodeFromString<MeshMessage>(malformedJson)
    }
}
