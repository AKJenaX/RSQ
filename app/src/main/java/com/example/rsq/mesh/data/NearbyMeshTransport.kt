package com.example.rsq.mesh.data

import android.content.Context
import android.util.Log
import com.example.rsq.mesh.domain.MeshTransport
import com.example.rsq.mesh.domain.NodeIdentityProvider
import com.example.rsq.mesh.model.MeshDiagnostics
import com.example.rsq.mesh.model.MeshMessage
import com.example.rsq.mesh.model.MeshTransportStatus
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json

/**
 * Implementation of [MeshTransport] using Google Nearby Connections.
 * Handles peer discovery and connection management using P2P_CLUSTER strategy.
 */
class NearbyMeshTransport(
    private val context: Context,
    private val nodeIdentityProvider: NodeIdentityProvider
) : MeshTransport {

    private val connectionsClient = Nearby.getConnectionsClient(context.applicationContext)
    private val localNodeId = nodeIdentityProvider.getNodeId()

    // Scope for emitting incoming messages
    private var transportScope: CoroutineScope? = null

    // Precise tracking of radio status
    private var isAdvertising = false
    private var isDiscovering = false

    // Internal tracking of peer states
    private enum class EndpointState {
        DISCOVERED,
        CONNECTING,
        CONNECTED
    }

    private val peerStates = mutableMapOf<String, EndpointState>()
    private val peerNodeIds = mutableMapOf<String, String>() // endpointId -> nodeId

    // Flow for future message observation
    private val _incomingMessages = MutableSharedFlow<MeshMessage>()
    private val _connectedPeerCount = MutableStateFlow(0)
    
    // Diagnostic State
    private val _diagnostics = MutableStateFlow(MeshDiagnostics())

    companion object {
        private const val TAG = "NearbyMeshTransport"
        private const val RSQ_NEARBY_SERVICE_ID = "com.example.rsq.MESH_SERVICE"
        private val STRATEGY = Strategy.P2P_CLUSTER
    }

    override fun start() {
        if ((_diagnostics.value.status != MeshTransportStatus.STOPPED) && 
            (_diagnostics.value.status != MeshTransportStatus.ERROR)) {
            Log.d(TAG, "Transport already starting or running (status: ${_diagnostics.value.status})")
            return
        }
        
        Log.i(TAG, "Starting Nearby Mesh Transport for Node: $localNodeId")
        updateStatus(MeshTransportStatus.STARTING)
        
        transportScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        
        startAdvertising()
        startDiscovery()
    }

    override fun stop() {
        Log.i(TAG, "Stopping Nearby Mesh Transport")
        updateStatus(MeshTransportStatus.STOPPED)
        
        transportScope?.cancel()
        transportScope = null
        
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        
        isAdvertising = false
        isDiscovering = false
        peerStates.clear()
        peerNodeIds.clear()
        
        // Reset full diagnostics on stop
        _diagnostics.value = MeshDiagnostics()
        _connectedPeerCount.value = 0
    }

    private fun startAdvertising() {
        if (_diagnostics.value.status == MeshTransportStatus.STOPPED) return

        Log.d(TAG, "Requesting Nearby advertising start...")
        val advertisingOptions = AdvertisingOptions.Builder()
            .setStrategy(STRATEGY)
            .build()

        connectionsClient.startAdvertising(
            localNodeId,
            RSQ_NEARBY_SERVICE_ID,
            connectionLifecycleCallback,
            advertisingOptions
        ).addOnSuccessListener {
            if (_diagnostics.value.status != MeshTransportStatus.STOPPED) {
                Log.i(TAG, "Advertising started successfully")
                isAdvertising = true
                updateDiagnostics { it.copy(isAdvertising = true) }
                checkLifecycleReady()
            } else {
                Log.w(TAG, "Advertising started after transport was stopped; stopping immediately")
                connectionsClient.stopAdvertising()
            }
        }.addOnFailureListener { e ->
            if (_diagnostics.value.status != MeshTransportStatus.STOPPED) {
                val errorMsg = "Advertising failed: ${e.message}"
                Log.e(TAG, errorMsg, e)
                isAdvertising = false
                updateDiagnostics { it.copy(isAdvertising = false, lastError = errorMsg) }
                updateStatus(MeshTransportStatus.ERROR)
            }
        }
    }

    private fun startDiscovery() {
        if (_diagnostics.value.status == MeshTransportStatus.STOPPED) return

        Log.d(TAG, "Requesting Nearby discovery start...")
        val discoveryOptions = DiscoveryOptions.Builder()
            .setStrategy(STRATEGY)
            .build()

        connectionsClient.startDiscovery(
            RSQ_NEARBY_SERVICE_ID,
            endpointDiscoveryCallback,
            discoveryOptions
        ).addOnSuccessListener {
            if (_diagnostics.value.status != MeshTransportStatus.STOPPED) {
                Log.i(TAG, "Discovery started successfully")
                isDiscovering = true
                updateDiagnostics { it.copy(isDiscovering = true) }
                checkLifecycleReady()
            } else {
                Log.w(TAG, "Discovery started after transport was stopped; stopping immediately")
                connectionsClient.stopDiscovery()
            }
        }.addOnFailureListener { e ->
            if (_diagnostics.value.status != MeshTransportStatus.STOPPED) {
                val errorMsg = "Discovery failed: ${e.message}"
                Log.e(TAG, errorMsg, e)
                isDiscovering = false
                updateDiagnostics { it.copy(isDiscovering = false, lastError = errorMsg) }
                updateStatus(MeshTransportStatus.ERROR)
            }
        }
    }

    private fun checkLifecycleReady() {
        if (isAdvertising && isDiscovering && _diagnostics.value.status == MeshTransportStatus.STARTING) {
            Log.i(TAG, "Mesh Transport is now READY")
            updateStatus(MeshTransportStatus.READY)
        } else if (isAdvertising && _diagnostics.value.status == MeshTransportStatus.STARTING) {
            updateStatus(MeshTransportStatus.ADVERTISING)
        } else if (isDiscovering && _diagnostics.value.status == MeshTransportStatus.STARTING) {
            updateStatus(MeshTransportStatus.DISCOVERING)
        }
    }

    override fun discoverPeers() {
        if (_diagnostics.value.status == MeshTransportStatus.STOPPED) {
            Log.w(TAG, "Cannot discover peers while transport is stopped")
            return
        }
        
        if (isDiscovering) {
            Log.v(TAG, "Discovery already active")
            return
        }
        
        startDiscovery()
    }

    override suspend fun sendMessage(message: MeshMessage): Result<Unit> {
        if ((_diagnostics.value.status != MeshTransportStatus.READY) && 
            (_diagnostics.value.status != MeshTransportStatus.ADVERTISING) &&
            (_diagnostics.value.status != MeshTransportStatus.DISCOVERING)) {
            return Result.failure(IllegalStateException("Transport is not active (status: ${_diagnostics.value.status})"))
        }

        val connectedEndpoints = peerStates.filter { it.value == EndpointState.CONNECTED }.keys
        if (connectedEndpoints.isEmpty()) {
            return Result.failure(IllegalStateException("No connected peers"))
        }

        return try {
            val json = Json.encodeToString(message)
            val payload = Payload.fromBytes(json.toByteArray(Charsets.UTF_8))
            
            connectionsClient.sendPayload(connectedEndpoints.toList(), payload).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message", e)
            updateDiagnostics { it.copy(lastError = "Send failed: ${e.message}") }
            Result.failure(e)
        }
    }

    override fun observeIncomingMessages(): Flow<MeshMessage> {
        return _incomingMessages.asSharedFlow()
    }

    override fun observeConnectedPeerCount(): Flow<Int> {
        return _connectedPeerCount.asStateFlow()
    }

    override fun observeDiagnostics(): Flow<MeshDiagnostics> {
        return _diagnostics.asStateFlow()
    }

    /**
     * Callback for connection lifecycle events.
     */
    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            if (_diagnostics.value.status == MeshTransportStatus.STOPPED) {
                Log.w(TAG, "Rejecting connection initiated from $endpointId after stop()")
                connectionsClient.rejectConnection(endpointId)
                return
            }

            val eventMsg = "Connection initiated: $endpointId (${info.endpointName})"
            Log.d(TAG, eventMsg)
            updateDiagnostics { it.copy(lastConnectionEvent = eventMsg) }
            
            peerStates[endpointId] = EndpointState.CONNECTING
            peerNodeIds[endpointId] = info.endpointName

            // Automatically accept connections from other RSQ nodes.
            connectionsClient.acceptConnection(endpointId, payloadCallback)
                .addOnFailureListener { e ->
                    val failMsg = "Failed to accept connection from $endpointId: ${e.message}"
                    Log.e(TAG, failMsg, e)
                    peerStates.remove(endpointId)
                    updateDiagnostics { it.copy(lastError = failMsg) }
                }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (_diagnostics.value.status == MeshTransportStatus.STOPPED) {
                Log.w(TAG, "Ignoring connection result for $endpointId after stop()")
                connectionsClient.disconnectFromEndpoint(endpointId)
                return
            }

            val statusMsg = "Code: ${result.status.statusCode} Msg: ${result.status.statusMessage}"
            
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    val nodeId = peerNodeIds[endpointId] ?: "unknown_node"
                    val eventMsg = "Connected to node: $nodeId"
                    Log.i(TAG, eventMsg)
                    peerStates[endpointId] = EndpointState.CONNECTED
                    updateDiagnostics { it.copy(lastConnectionEvent = eventMsg) }
                    updateConnectedPeerCount()
                }
                else -> {
                    val eventMsg = "Connection failed: $statusMsg"
                    Log.w(TAG, "Connection failed or rejected for $endpointId: $statusMsg")
                    peerStates.remove(endpointId)
                    peerNodeIds.remove(endpointId)
                    updateDiagnostics { it.copy(lastConnectionEvent = eventMsg) }
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            val eventMsg = "Disconnected: $endpointId"
            Log.i(TAG, eventMsg)
            peerStates.remove(endpointId)
            peerNodeIds.remove(endpointId)
            updateDiagnostics { it.copy(lastConnectionEvent = eventMsg) }
            updateConnectedPeerCount()
        }
    }

    /**
     * Callback for endpoint discovery events.
     */
    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (_diagnostics.value.status == MeshTransportStatus.STOPPED) return

            val eventMsg = "Found: $endpointId (${info.endpointName})"
            Log.d(TAG, eventMsg)
            updateDiagnostics { it.copy(lastDiscoveredEndpoint = eventMsg) }
            
            // Check if we are already connected or connecting
            if (peerStates.containsKey(endpointId)) {
                Log.d(TAG, "Already connecting or connected to $endpointId, ignoring discovery")
                return
            }

            peerStates[endpointId] = EndpointState.DISCOVERED
            peerNodeIds[endpointId] = info.endpointName

            // Attempt to connect to the discovered peer
            Log.d(TAG, "Requesting connection to $endpointId")
            peerStates[endpointId] = EndpointState.CONNECTING
            connectionsClient.requestConnection(
                localNodeId,
                endpointId,
                connectionLifecycleCallback
            ).addOnFailureListener { e ->
                val failMsg = "Request connection failed for $endpointId: ${e.message}"
                Log.e(TAG, failMsg, e)
                peerStates.remove(endpointId)
                peerNodeIds.remove(endpointId)
                updateDiagnostics { it.copy(lastError = failMsg) }
            }
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(TAG, "Lost discovery of endpoint: $endpointId")
            if (peerStates[endpointId] == EndpointState.DISCOVERED) {
                peerStates.remove(endpointId)
                peerNodeIds.remove(endpointId)
            }
        }
    }

    /**
     * Stub for payload handling.
     */
    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type != Payload.Type.BYTES) {
                Log.v(TAG, "Ignoring non-byte payload from $endpointId")
                return
            }

            val bytes = payload.asBytes() ?: return
            val json = String(bytes, Charsets.UTF_8)

            try {
                val message = Json.decodeFromString<MeshMessage>(json)
                validateMessage(message)
                
                Log.d(TAG, "Received MeshMessage: ${message.id} from $endpointId")
                
                transportScope?.launch {
                    _incomingMessages.emit(message)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to deserialize or validate MeshMessage from $endpointId", e)
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // No-op
        }
    }

    private fun validateMessage(message: MeshMessage) {
        require(message.id.isNotBlank()) { "Message ID must not be blank" }
        require(message.senderNodeId.isNotBlank()) { "Sender Node ID must not be blank" }
        require(message.originNodeId.isNotBlank()) { "Origin Node ID must not be blank" }
        require(message.timestamp > 0) { "Invalid timestamp" }
        require(message.ttl >= 0) { "Invalid TTL" }
    }

    private fun updateConnectedPeerCount() {
        val count = peerStates.count { it.value == EndpointState.CONNECTED }
        _connectedPeerCount.value = count
        updateDiagnostics { it.copy(connectedPeerCount = count) }
    }

    private fun updateStatus(status: MeshTransportStatus) {
        updateDiagnostics { it.copy(status = status) }
    }

    private fun updateDiagnostics(update: (MeshDiagnostics) -> MeshDiagnostics) {
        _diagnostics.value = update(_diagnostics.value)
    }
}
