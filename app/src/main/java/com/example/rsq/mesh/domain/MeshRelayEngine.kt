package com.example.rsq.mesh.domain

import com.example.rsq.mesh.model.MeshMessage
import com.example.rsq.mesh.model.MeshRelayEvent
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

    private val _relayEvents = MutableSharedFlow<MeshRelayEvent>()

    /**
     * Flow of diagnostic events occurring during message processing and relaying.
     */
    val relayEvents: Flow<MeshRelayEvent> = _relayEvents.asSharedFlow()

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
        val localNodeId = identityProvider.getNodeId()

        emitEvent(message, "OUTBOUND", localNodeId, null)

        repository.saveMessage(message)

        return transport.sendMessage(message)
    }

    private suspend fun handleIncomingMessage(message: MeshMessage) {
        val localNodeId = identityProvider.getNodeId()

        // 1. Emit RECEIVED event
        emitEvent(message, "RECEIVED", localNodeId, null)

        // 2. Check for duplicates
        if (repository.hasMessage(message.id)) {
            emitEvent(message, "DUPLICATE_DISCARDED", localNodeId, null)

            println(
                "MeshRelayEngine: Duplicate message discarded: ${message.id}"
            )

            return
        }

        // 3. Persist new message
        println(
            "MeshRelayEngine: New mesh message received: ${message.id}"
        )

        repository.saveMessage(message)

        emitEvent(message, "PERSISTED", localNodeId, null)

        // 4. Emit for local consumption
        _processedMessages.emit(message)

        // 5. Evaluate relay logic
        if (message.ttl > 0) {

            // Preserve message ID and origin.
            // Update sender to this node and decrement TTL.
            val relayedMessage = message.copy(
                senderNodeId = localNodeId,
                ttl = message.ttl - 1
            )

            println(
                "MeshRelayEngine: Relaying message: " +
                        "${message.id} (new ttl: ${relayedMessage.ttl})"
            )

            // Actually attempt the transmission first.
            val result = transport.sendMessage(relayedMessage)

            if (result.isSuccess) {
                // Only report RELAYED after Nearby Connections
                // successfully accepts the transmission.
                emitEvent(
                    message,
                    "RELAYED",
                    localNodeId,
                    relayedMessage.ttl
                )

                // Mark the original message as delivered/processed
                // after successful relay.
                repository.markMessageDelivered(message.id)
            } else {
                println(
                    "MeshRelayEngine: Relay failed for message " +
                            "${message.id}: ${result.exceptionOrNull()?.message}"
                )
            }
        } else {
            emitEvent(
                message,
                "TTL_EXPIRED",
                localNodeId,
                null
            )

            println(
                "MeshRelayEngine: TTL exhausted for message: ${message.id}"
            )
        }
    }

    private suspend fun emitEvent(
        message: MeshMessage,
        action: String,
        nodeId: String,
        ttlAfter: Int?
    ) {
        _relayEvents.emit(
            MeshRelayEvent(
                messageId = message.id,
                nodeId = nodeId,
                senderNodeId = message.senderNodeId,
                originNodeId = message.originNodeId,
                ttlBefore = message.ttl,
                ttlAfter = ttlAfter,
                action = action,
                timestamp = System.currentTimeMillis()
            )
        )
    }
}