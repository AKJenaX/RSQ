package com.example.rsq.mesh.data

import android.content.Context
import android.util.Log
import com.example.rsq.mesh.domain.MeshTransport
import com.example.rsq.mesh.domain.NodeIdentityProvider
import com.example.rsq.mesh.model.MeshMessage
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
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

    // Internal state for transport lifecycle
    private enum class TransportLifecycle {
        STOPPED,
        STARTING,
        RUNNING
    }

    private var lifecycleState = TransportLifecycle.STOPPED

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

    companion object {
        private const val TAG = "NearbyMeshTransport"
        private const val RSQ_NEARBY_SERVICE_ID = "com.example.rsq.MESH_SERVICE"
        private val STRATEGY = Strategy.P2P_CLUSTER
    }

    override fun start() {
        if (lifecycleState != TransportLifecycle.STOPPED) {
            Log.d(TAG, "Transport already starting or running (state: $lifecycleState)")
            return
        }
        
        Log.i(TAG, "Starting Nearby Mesh Transport for Node: $localNodeId")
        lifecycleState = TransportLifecycle.STARTING
        
        transportScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        
        startAdvertising()
        startDiscovery()
    }

    override fun stop() {
        Log.i(TAG, "Stopping Nearby Mesh Transport")
        lifecycleState = TransportLifecycle.STOPPED
        
        transportScope?.cancel()
        transportScope = null
        
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        
        isAdvertising = false
        isDiscovering = false
        peerStates.clear()
        peerNodeIds.clear()
    }

    private fun startAdvertising() {
        if (lifecycleState == TransportLifecycle.STOPPED) return

        val advertisingOptions = AdvertisingOptions.Builder()
            .setStrategy(STRATEGY)
            .build()

        connectionsClient.startAdvertising(
            localNodeId,
            RSQ_NEARBY_SERVICE_ID,
            connectionLifecycleCallback,
            advertisingOptions
        ).addOnSuccessListener {
            if (lifecycleState != TransportLifecycle.STOPPED) {
                Log.i(TAG, "Advertising started successfully")
                isAdvertising = true
                checkLifecycleRunning()
            } else {
                Log.w(TAG, "Advertising started after transport was stopped; stopping immediately")
                connectionsClient.stopAdvertising()
            }
        }.addOnFailureListener { e ->
            if (lifecycleState != TransportLifecycle.STOPPED) {
                Log.e(TAG, "Advertising failed to start", e)
                isAdvertising = false
            }
        }
    }

    private fun startDiscovery() {
        if (lifecycleState == TransportLifecycle.STOPPED) return

        val discoveryOptions = DiscoveryOptions.Builder()
            .setStrategy(STRATEGY)
            .build()

        connectionsClient.startDiscovery(
            RSQ_NEARBY_SERVICE_ID,
            endpointDiscoveryCallback,
            discoveryOptions
        ).addOnSuccessListener {
            if (lifecycleState != TransportLifecycle.STOPPED) {
                Log.i(TAG, "Discovery started successfully")
                isDiscovering = true
                checkLifecycleRunning()
            } else {
                Log.w(TAG, "Discovery started after transport was stopped; stopping immediately")
                connectionsClient.stopDiscovery()
            }
        }.addOnFailureListener { e ->
            if (lifecycleState != TransportLifecycle.STOPPED) {
                Log.e(TAG, "Discovery failed to start", e)
                isDiscovering = false
            }
        }
    }

    private fun checkLifecycleRunning() {
        // Simple heuristic: transport is RUNNING if both operations have reported success
        if (isAdvertising && isDiscovering && lifecycleState == TransportLifecycle.STARTING) {
            Log.i(TAG, "Mesh Transport is now fully RUNNING")
            lifecycleState = TransportLifecycle.RUNNING
        }
    }

    override fun discoverPeers() {
        if (lifecycleState == TransportLifecycle.STOPPED) {
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
        if (lifecycleState != TransportLifecycle.RUNNING) {
            return Result.failure(IllegalStateException("Transport is not running"))
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
            Result.failure(e)
        }
    }

    override fun observeIncomingMessages(): Flow<MeshMessage> {
        return _incomingMessages.asSharedFlow()
    }

    /**
     * Callback for connection lifecycle events.
     */
    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            if (lifecycleState == TransportLifecycle.STOPPED) {
                Log.w(TAG, "Rejecting connection initiated from $endpointId after stop()")
                connectionsClient.rejectConnection(endpointId)
                return
            }

            Log.d(TAG, "Connection initiated: $endpointId (${info.endpointName})")
            
            peerStates[endpointId] = EndpointState.CONNECTING
            peerNodeIds[endpointId] = info.endpointName

            // Automatically accept connections from other RSQ nodes.
            connectionsClient.acceptConnection(endpointId, payloadCallback)
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to accept connection from $endpointId", e)
                    peerStates.remove(endpointId)
                }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (lifecycleState == TransportLifecycle.STOPPED) {
                Log.w(TAG, "Ignoring connection result for $endpointId after stop()")
                connectionsClient.disconnectFromEndpoint(endpointId)
                return
            }

            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    val nodeId = peerNodeIds[endpointId] ?: "unknown_node"
                    Log.i(TAG, "Successfully connected to node: $nodeId (endpoint: $endpointId)")
                    peerStates[endpointId] = EndpointState.CONNECTED
                }
                else -> {
                    Log.w(TAG, "Connection failed or rejected for $endpointId: ${result.status.statusMessage}")
                    peerStates.remove(endpointId)
                    peerNodeIds.remove(endpointId)
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.i(TAG, "Disconnected from endpoint: $endpointId")
            peerStates.remove(endpointId)
            peerNodeIds.remove(endpointId)
        }
    }

    /**
     * Callback for endpoint discovery events.
     */
    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (lifecycleState == TransportLifecycle.STOPPED) return

            Log.d(TAG, "Discovered RSQ endpoint: $endpointId (${info.endpointName})")
            
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
                Log.e(TAG, "Failed to request connection to $endpointId", e)
                peerStates.remove(endpointId)
                peerNodeIds.remove(endpointId)
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
}
