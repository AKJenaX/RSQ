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

    @Test(expected = Exception::class)
    fun `Malformed JSON should throw exception during deserialization`() {
        val malformedJson = "{ \"id\": \"msg-1\", \"invalid_field\": true }"
        Json.decodeFromString<MeshMessage>(malformedJson)
    }
}
