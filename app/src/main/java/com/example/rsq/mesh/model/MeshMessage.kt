package com.example.rsq.mesh.model

import com.example.rsq.data.model.Priority
import kotlinx.serialization.Serializable

/**
 * Represents a packet of information traveling through the mesh network.
 * 
 * @property id Unique identifier for the message.
 * @property senderNodeId ID of the node that most recently transmitted this message.
 * @property originNodeId ID of the node that originally created this message.
 * @property messageType The category of the message (SOS, Relay, etc).
 * @property timestamp Time the message was created.
 * @property latitude Optional latitude of the origin node at the time of creation.
 * @property longitude Optional longitude of the origin node at the time of creation.
 * @property priority Urgency level of the message.
 * @property payload The actual content/data being transmitted.
 * @property ttl Time-To-Live; limits the number of times a message can be re-broadcasted.
 */
@Serializable
data class MeshMessage(
    val id: String,
    val senderNodeId: String,
    val originNodeId: String,
    val messageType: MeshMessageType,
    val timestamp: Long,
    val latitude: Double?,
    val longitude: Double?,
    val priority: Priority,
    val payload: String,
    val ttl: Int
)
