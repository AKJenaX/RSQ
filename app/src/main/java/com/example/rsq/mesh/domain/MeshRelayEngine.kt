package com.example.rsq.mesh.domain

import com.example.rsq.mesh.model.MeshMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Domain engine responsible for multi-hop message relay, deduplication, and persistence.
 * This component coordinates between the transport layer and local storage.
 */
class MeshRelayEngine(
    private val transport: MeshTransport,
    private val repository: MeshMessageRepository,
    private val identityProvider: NodeIdentityProvider,
    scope: CoroutineScope
) {
    private val _processedMessages = MutableSharedFlow<MeshMessage>()
    
    /**
     * Flow of new, unique messages received from the mesh network.
     * Subscribers will not see duplicates.
     */
    val processedMessages: Flow<MeshMessage> = _processedMessages.asSharedFlow()

    init {
        scope.launch {
            transport.observeIncomingMessages().collect { message ->
                handleIncomingMessage(message)
            }
        }
    }

    /**
     * Broadcasts a new message from this node and saves it to prevent self-relaying.
     */
    suspend fun broadcastMessage(message: MeshMessage): Result<Unit> {
        repository.saveMessage(message)
        return transport.sendMessage(message)
    }

    private suspend fun handleIncomingMessage(message: MeshMessage) {
        // 1. Check for duplicates
        if (repository.hasMessage(message.id)) {
            // Log that duplicate was discarded (using println for domain/test compatibility)
            println("MeshRelayEngine: Duplicate message discarded: ${message.id}")
            return
        }

        // 2. Persist new message
        println("MeshRelayEngine: New mesh message received: ${message.id}")
        repository.saveMessage(message)

        // 3. Emit for local consumption
        _processedMessages.emit(message)

        // 4. Evaluate relay logic
        if (message.ttl > 0) {
            val localNodeId = identityProvider.getNodeId()
            
            // Create relayed copy: preserve ID and origin, update sender and decrement TTL
            val relayedMessage = message.copy(
                senderNodeId = localNodeId,
                ttl = message.ttl - 1
            )
            
            println("MeshRelayEngine: Relaying message: ${message.id} (new ttl: ${relayedMessage.ttl})")
            val result = transport.sendMessage(relayedMessage)
            if (result.isSuccess) {
                // Mark as processed in repository
                repository.markMessageDelivered(message.id)
            }
        } else {
            println("MeshRelayEngine: TTL exhausted for message: ${message.id}")
        }
    }
}
