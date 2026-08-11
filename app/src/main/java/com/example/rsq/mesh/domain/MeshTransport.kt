package com.example.rsq.mesh.domain

import com.example.rsq.mesh.model.MeshMessage
import kotlinx.coroutines.flow.Flow

/**
 * Interface defining the essential operations for any offline transport layer
 * (e.g., Bluetooth, Wi-Fi Direct, Nearby Connections).
 */
interface MeshTransport {
    /**
     * Initializes and starts the mesh transport layer.
     */
    fun start()

    /**
     * Stops the mesh transport layer and releases resources.
     */
    fun stop()

    /**
     * Initiates a search for nearby peers in the mesh network.
     */
    fun discoverPeers()

    /**
     * Transmits a [MeshMessage] to reachable peers.
     */
    suspend fun sendMessage(message: MeshMessage): Result<Unit>

    /**
     * Provides a stream of incoming messages received from the mesh network.
     */
    fun observeIncomingMessages(): Flow<MeshMessage>
}
