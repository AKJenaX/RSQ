package com.example.rsq.mesh.model

import com.example.rsq.data.model.Priority
import kotlinx.serialization.Serializable

/**
 * Represents a packet of information traveling through the mesh network.
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
    val ttl: Int,
    // Unified report fields for reliable offline transmission
    val title: String = "",
    val description: String = "",
    val accuracy: Float? = null,
    val expirationTimestamp: Long = timestamp + 24 * 60 * 60 * 1000L,
    val originUserId: String = "",
    val originUserName: String = "",
    val originCreatedAt: Long = 0L,
    // Attached evidence media metadata (up to 5 items)
    val mediaItems: List<MeshMediaMetadata> = emptyList()
)
