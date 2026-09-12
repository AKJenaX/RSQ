package com.example.rsq.mesh.domain

import android.util.Log
import com.example.rsq.mesh.model.MeshMessage
import com.example.rsq.mesh.model.MeshRelayEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Domain engine responsible for multi-hop message relay, deduplication, and persistence.
 * Coordinates between the transport layer, media storage, and local repositories.
 */
class MeshRelayEngine(
    private val transport: MeshTransport,
    private val repository: MeshMessageRepository,
    private val identityProvider: NodeIdentityProvider,
    scope: CoroutineScope
) {
    companion object {
        private const val TAG = "MeshRelayEngine"
    }

    private val _processedMessages = MutableSharedFlow<MeshMessage>()
    val processedMessages: Flow<MeshMessage> = _processedMessages.asSharedFlow()

    private val _relayEvents = MutableSharedFlow<MeshRelayEvent>()
    val relayEvents: Flow<MeshRelayEvent> = _relayEvents.asSharedFlow()

    // Map tracking relayed messages to enable multi-hop media forwarding
    private val relayedMessagesMap = mutableMapOf<String, MeshMessage>()

    init {
        scope.launch {
            transport.observeIncomingMessages().collect { message ->
                handleIncomingMessage(message)
            }
        }
    }

    suspend fun broadcastMessage(message: MeshMessage, mediaFiles: List<File> = emptyList()): Result<Unit> {
        val localNodeId = identityProvider.getNodeId()

        emitEvent(message, "OUTBOUND", localNodeId, null)

        repository.saveMessage(message)

        return transport.sendMessage(message, mediaFiles)
    }

    suspend fun onMediaFileVerified(reportId: String, mediaId: String, savedFile: File) {
        val localNodeId = identityProvider.getNodeId()
        val relayedMsg = relayedMessagesMap[reportId]
        if (relayedMsg != null && relayedMsg.ttl >= 0) {
            logInfo("Multi-hop forwarding verified media file $mediaId for report $reportId (TTL: ${relayedMsg.ttl})")
            emitCustomEvent(
                messageId = reportId,
                action = "MEDIA RELAYED",
                senderNodeId = localNodeId,
                originNodeId = relayedMsg.originNodeId,
                ttlBefore = relayedMsg.ttl
            )
            transport.sendMessage(relayedMsg, listOf(savedFile))
        } else {
            logDebug("No active multi-hop relay required for media $mediaId (reportId=$reportId)")
        }
    }

    private suspend fun handleIncomingMessage(message: MeshMessage) {
        val localNodeId = identityProvider.getNodeId()

        // 1. Emit RECEIVED event
        emitEvent(message, "RECEIVED", localNodeId, null)

        // 2. Check for duplicates
        if (repository.hasMessage(message.id)) {
            emitEvent(message, "DUPLICATE_DISCARDED", localNodeId, null)

            logDebug("Duplicate message discarded: ${message.id}")
            return
        }

        // 3. Persist new message
        logInfo("New mesh message received: ${message.id}")

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

            // Cache the relayed message structure for multi-hop media forwarding
            relayedMessagesMap[message.id] = relayedMessage

            logInfo("Relaying message: ${message.id} (new ttl: ${relayedMessage.ttl})")

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
                logError("Relay failed for message ${message.id}: ${result.exceptionOrNull()?.message}")
            }
        } else {
            emitEvent(
                message,
                "TTL_EXPIRED",
                localNodeId,
                null
            )

            logDebug("TTL exhausted for message: ${message.id}")
        }
    }

    suspend fun emitCustomEvent(
        messageId: String,
        action: String,
        senderNodeId: String = "",
        originNodeId: String = "",
        ttlBefore: Int = 3
    ) {
        val localNodeId = identityProvider.getNodeId()
        _relayEvents.emit(
            MeshRelayEvent(
                messageId = messageId,
                nodeId = localNodeId,
                senderNodeId = senderNodeId,
                originNodeId = originNodeId,
                ttlBefore = ttlBefore,
                ttlAfter = null,
                action = action,
                timestamp = System.currentTimeMillis()
            )
        )
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

    private fun logInfo(msg: String) {
        try {
            Log.i(TAG, msg)
        } catch (e: Throwable) {
            println("$TAG: $msg")
        }
    }

    private fun logDebug(msg: String) {
        try {
            Log.d(TAG, msg)
        } catch (e: Throwable) {
            println("$TAG: $msg")
        }
    }

    private fun logError(msg: String) {
        try {
            Log.e(TAG, msg)
        } catch (e: Throwable) {
            println("$TAG: $msg")
        }
    }
}
