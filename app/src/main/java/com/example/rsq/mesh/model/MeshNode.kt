package com.example.rsq.mesh.model

/**
 * Represents a device participating in the mesh network.
 *
 * @property nodeId Unique identifier for the mesh participant.
 * @property role The designated role of the node (e.g., Victim, Volunteer).
 * @property lastSeen Timestamp of the last successful interaction with this node.
 * @property latitude Last known latitude of the node.
 * @property longitude Last known longitude of the node.
 */
data class MeshNode(
    val nodeId: String,
    val role: String,
    val lastSeen: Long,
    val latitude: Double?,
    val longitude: Double?
)
