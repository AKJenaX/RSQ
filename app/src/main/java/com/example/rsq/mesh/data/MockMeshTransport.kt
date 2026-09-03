package com.example.rsq.mesh.data

import android.util.Log
import com.example.rsq.mesh.domain.MeshTransport
import com.example.rsq.mesh.domain.NodeIdentityProvider
import com.example.rsq.mesh.model.MeshDiagnostics
import com.example.rsq.mesh.model.MeshMessage
import com.example.rsq.mesh.model.MeshMessageType
import com.example.rsq.mesh.model.MeshTransportStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * DEBUG/TEST-only Transport that simulates peer discovery and message exchange on Android Emulators
 * where physical Bluetooth/Wi-Fi Direct radio capabilities are unavailable.
 */
class MockMeshTransport(
    private val nodeIdentityProvider: NodeIdentityProvider
) : MeshTransport {

    companion object {
        private const val TAG = "MockMeshTransport"
        private const val MOCK_PEER_ID = "MOCK-PEER-001"
    }

    private val localNodeId = nodeIdentityProvider.getNodeId()
    private var scope: CoroutineScope? = null

    private val _incomingMessages = MutableSharedFlow<MeshMessage>()
    private val _connectedPeerCount = MutableStateFlow(0)
    private val _diagnostics = MutableStateFlow(
        MeshDiagnostics(
            status = MeshTransportStatus.STOPPED,
            isMockMode = true,
            transportName = "MESH TEST MODE — SIMULATED PEER"
        )
    )

    override fun start() {
        if (_diagnostics.value.status != MeshTransportStatus.STOPPED) return

        Log.i(TAG, "Starting Mock Mesh Transport (Emulator Test Mode) for node $localNodeId")
        val job = SupervisorJob()
        scope = CoroutineScope(Dispatchers.Main + job)

        updateDiagnostics {
            it.copy(
                status = MeshTransportStatus.STARTING,
                lastConnectionEvent = "Simulating advertising & discovery..."
            )
        }

        // Simulate discovery and connection lifecycle with small realistic delay
        scope?.launch {
            delay(500)
            updateDiagnostics {
                it.copy(
                    status = MeshTransportStatus.ADVERTISING,
                    isAdvertising = true,
                    isDiscovering = true
                )
            }
            delay(500)
            updateDiagnostics {
                it.copy(
                    status = MeshTransportStatus.READY,
                    lastDiscoveredEndpoint = MOCK_PEER_ID,
                    lastConnectionEvent = "Connected to simulated peer $MOCK_PEER_ID",
                    connectedPeerCount = 1
                )
            }
            _connectedPeerCount.value = 1
            Log.i(TAG, "Mock Mesh Transport is READY. Connected to simulated peer: $MOCK_PEER_ID")
        }
    }

    override fun stop() {
        Log.i(TAG, "Stopping Mock Mesh Transport")
        scope?.cancel()
        scope = null
        _connectedPeerCount.value = 0
        _diagnostics.value = MeshDiagnostics(
            status = MeshTransportStatus.STOPPED,
            isMockMode = true,
            transportName = "MESH TEST MODE — SIMULATED PEER"
        )
    }

    override fun discoverPeers() {
        if (_diagnostics.value.status == MeshTransportStatus.STOPPED) {
            start()
        }
    }

    override suspend fun sendMessage(message: MeshMessage): Result<Unit> {
        if (_connectedPeerCount.value == 0) {
            return Result.failure(IllegalStateException("No connected peers in mock transport"))
        }

        Log.d(TAG, "Simulating send of message ${message.id} from $localNodeId to $MOCK_PEER_ID")

        scope?.launch {
            delay(300) // Small simulated network transmission delay
            updateDiagnostics {
                it.copy(lastConnectionEvent = "Sent message ${message.id} to $MOCK_PEER_ID")
            }

            // Simulate simulated peer acknowledging or relaying an echo message after a short delay
            delay(500)
            val echoMessage = MeshMessage(
                id = "ECHO-${message.id.take(8)}",
                senderNodeId = MOCK_PEER_ID,
                originNodeId = MOCK_PEER_ID,
                messageType = MeshMessageType.ACKNOWLEDGEMENT,
                timestamp = System.currentTimeMillis(),
                latitude = message.latitude,
                longitude = message.longitude,
                priority = message.priority,
                payload = "Ack from $MOCK_PEER_ID for msg: ${message.id.take(8)}",
                ttl = 1,
                title = "Simulated Peer ACK",
                description = "Mock peer received packet"
            )
            Log.d(TAG, "Simulated peer $MOCK_PEER_ID emitting ACK for ${message.id}")
            _incomingMessages.emit(echoMessage)
        }

        return Result.success(Unit)
    }

    override fun observeIncomingMessages(): Flow<MeshMessage> = _incomingMessages.asSharedFlow()

    override fun observeConnectedPeerCount(): Flow<Int> = _connectedPeerCount.asStateFlow()

    override fun observeDiagnostics(): Flow<MeshDiagnostics> = _diagnostics.asStateFlow()

    private fun updateDiagnostics(update: (MeshDiagnostics) -> MeshDiagnostics) {
        _diagnostics.value = update(_diagnostics.value)
    }
}
