package com.example.rsq.mesh.model

/**
 * Represents a diagnostic event occurring within the [MeshRelayEngine].
 */
data class MeshRelayEvent(
    val messageId: String,
    val nodeId: String, // The local node recording the event
    val senderNodeId: String,
    val originNodeId: String,
    val ttlBefore: Int,
    val ttlAfter: Int?,
    val action: String,
    val timestamp: Long = System.currentTimeMillis()
)
