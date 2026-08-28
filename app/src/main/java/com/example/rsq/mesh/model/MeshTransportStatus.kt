package com.example.rsq.mesh.model

enum class MeshTransportStatus {
    STOPPED,
    STARTING,
    ADVERTISING,
    DISCOVERING,
    READY,
    ERROR
}

data class MeshDiagnostics(
    val status: MeshTransportStatus = MeshTransportStatus.STOPPED,
    val isAdvertising: Boolean = false,
    val isDiscovering: Boolean = false,
    val lastDiscoveredEndpoint: String? = null,
    val lastConnectionEvent: String? = null,
    val lastError: String? = null,
    val connectedPeerCount: Int = 0
)
