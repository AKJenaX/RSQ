package com.example.rsq.mesh.data

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import com.example.rsq.mesh.domain.MeshTransport
import com.example.rsq.mesh.domain.NodeIdentityProvider
import com.example.rsq.mesh.model.MeshDiagnostics
import com.example.rsq.mesh.model.MeshMessage
import com.example.rsq.mesh.model.MeshTransportStatus
import com.example.rsq.mesh.service.MeshForegroundService
import com.example.rsq.reporting.data.LocalReportRepository
import com.example.rsq.reporting.data.local.LocalReportDatabase
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Implementation of [MeshTransport] using Google Nearby Connections.
 * Manages peer discovery, connection state machine per Node Identity, and multi-payload (BYTES + FILE) transmission using P2P_CLUSTER.
 */
class NearbyMeshTransport(
    private val context: Context,
    private val nodeIdentityProvider: NodeIdentityProvider,
    private val localReportRepository: LocalReportRepository? = null
) : MeshTransport {

    private val connectionsClient = Nearby.getConnectionsClient(context.applicationContext)
    private val localNodeId = nodeIdentityProvider.getNodeId()

    // Scope for emitting incoming messages
    private var transportScope: CoroutineScope? = null

    // Precise tracking of radio status
    private var isAdvertising = false
    private var isDiscovering = false

    // Pending media registry for receiver out-of-order handling
    val pendingMediaRegistry by lazy {
        val repo = localReportRepository ?: LocalReportRepository(
            LocalReportDatabase.getDatabase(context).reportDao()
        )
        PendingMediaRegistry(context.applicationContext, repo, CoroutineScope(Dispatchers.IO))
    }

    // Protect ParcelFileDescriptor lifetime from GC during asynchronous outgoing FILE transfers
    private val activeOutgoingPayloads = mutableMapOf<Long, Payload>()

    // Maps incoming payloadId -> Received Uri
    private val incomingPayloadUris = mutableMapOf<Long, Uri>()

    // Reference-counted active FILE transfers to pause discovery scanning during multi-file streaming
    private var activeFileTransferCount = 0

    // Authoritative Connection State Machine per Remote NODE Identity (endpointName)
    enum class NodeConnectionState {
        DISCOVERED,
        CONNECTING,
        CONNECTED,
        DISCONNECTING,
        DISCONNECTED,
        UNKNOWN
    }

    enum class ConnectionDirection {
        INITIATED_BY_US,
        INITIATED_BY_REMOTE,
        UNKNOWN
    }

    data class NodeSession(
        val nodeId: String,
        val endpointId: String,
        val state: NodeConnectionState,
        val direction: ConnectionDirection,
        val requestCount: Int = 1,
        val lastTransitionTimestamp: Long = System.currentTimeMillis()
    )

    // Authoritative node session tracking: nodeId -> NodeSession
    private val nodeSessions = mutableMapOf<String, NodeSession>()

    // Reverse lookup mapping: Nearby endpointId -> remote nodeId
    private val endpointToNodeMap = mutableMapOf<String, String>()

    // Legacy peerStates map for compatibility
    private val peerStates = mutableMapOf<String, EndpointState>()
    private enum class EndpointState { DISCOVERED, CONNECTING, CONNECTED }

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

        val hwError = checkHardwareAndPermissions()
        if (hwError != null) {
            Log.e(TAG, "Cannot start Nearby Mesh Transport: $hwError")
            updateDiagnostics { it.copy(lastError = hwError) }
            updateStatus(MeshTransportStatus.ERROR)
            return
        }

        Log.i(TAG, "Starting Nearby Mesh Transport for Node: $localNodeId")
        updateStatus(MeshTransportStatus.STARTING)

        transportScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        // Optionally start Foreground Service if context permits
        try {
            MeshForegroundService.startService(context)
        } catch (e: Exception) {
            Log.w(TAG, "Could not start MeshForegroundService: ${e.message}")
        }

        startAdvertising()
        startDiscovery()
    }

    private fun checkHardwareAndPermissions(): String? {
        val pm = context.packageManager
        if (!pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH) &&
            !pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            return "Device lacks Bluetooth hardware capability"
        }

        @Suppress("DEPRECATION")
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            return "Bluetooth is turned off"
        }

        return null
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
        activeFileTransferCount = 0
        activeOutgoingPayloads.clear()
        nodeSessions.clear()
        endpointToNodeMap.clear()
        peerStates.clear()
        incomingPayloadUris.clear()

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

    @Synchronized
    private fun onFileTransferStarted() {
        activeFileTransferCount++
        if (activeFileTransferCount == 1 && isDiscovering) {
            Log.i(TAG, "PAUSING_DISCOVERY: Active FILE transfers started ($activeFileTransferCount). Pausing discovery scanning.")
            connectionsClient.stopDiscovery()
        }
    }

    @Synchronized
    private fun onFileTransferEnded() {
        if (activeFileTransferCount > 0) {
            activeFileTransferCount--
        }
        if (activeFileTransferCount == 0 && !isDiscovering && (_diagnostics.value.status == MeshTransportStatus.READY || _diagnostics.value.status == MeshTransportStatus.DISCOVERING)) {
            Log.i(TAG, "RESUMING_DISCOVERY: All FILE transfers finished ($activeFileTransferCount). Resuming discovery scanning.")
            startDiscovery()
        }
    }

    override suspend fun sendMessage(message: MeshMessage, mediaFiles: List<File>): Result<Unit> {
        if ((_diagnostics.value.status != MeshTransportStatus.READY) &&
            (_diagnostics.value.status != MeshTransportStatus.ADVERTISING) &&
            (_diagnostics.value.status != MeshTransportStatus.DISCOVERING)) {
            return Result.failure(IllegalStateException("Transport is not active (status: ${_diagnostics.value.status})"))
        }

        val connectedSessions = nodeSessions.values.filter { it.state == NodeConnectionState.CONNECTED }
        val connectedEndpoints = connectedSessions.map { it.endpointId }
        if (connectedEndpoints.isEmpty()) {
            return Result.failure(IllegalStateException("No connected peers"))
        }

        return try {
            // Prepare media payloads if evidence images exist
            val (metadataList, payloadMap) = if (mediaFiles.isNotEmpty()) {
                OfflineMediaManager.prepareMediaPayloads(message.id, mediaFiles, message.mediaItems)
            } else {
                Pair(emptyList(), emptyMap())
            }

            val finalMessage = if (metadataList.isNotEmpty()) {
                message.copy(mediaItems = metadataList)
            } else {
                message
            }

            val json = Json.encodeToString(finalMessage)
            val bytesPayload = Payload.fromBytes(json.toByteArray(Charsets.UTF_8))

            // Phase 1: Send report metadata (BYTES payload)
            Log.i(TAG, "MESH_BYTES_SEND_START: reportId=${finalMessage.id}, payloadId=${bytesPayload.id}, endpoints=$connectedEndpoints")
            connectionsClient.sendPayload(connectedEndpoints, bytesPayload).await()
            Log.i(TAG, "MESH_BYTES_SEND_ACCEPTED: reportId=${finalMessage.id}, payloadId=${bytesPayload.id}, endpoints=$connectedEndpoints")

            // Phase 2: Stream actual image files (FILE payloads)
            payloadMap.forEach { (payloadId, filePayload) ->
                val size = filePayload.asFile()?.getSize() ?: 0L
                activeOutgoingPayloads[payloadId] = filePayload // Protect ParcelFileDescriptor lifetime from GC

                val now = System.currentTimeMillis()
                Log.i(TAG, "MEDIA_FILE_CREATED: reportId=${finalMessage.id}, mediaId=${finalMessage.mediaItems.find { it.nearbyPayloadId == payloadId }?.mediaId ?: "unknown"}, size=$size, payloadId=$payloadId, timestamp=$now")
                Log.i(TAG, "MEDIA_FILE_SEND_CALLED: reportId=${finalMessage.id}, payloadId=$payloadId, endpoints=$connectedEndpoints, timestamp=$now")

                onFileTransferStarted()

                try {
                    connectionsClient.sendPayload(connectedEndpoints, filePayload).await()
                    Log.i(TAG, "MEDIA_FILE_SEND_ACCEPTED: reportId=${finalMessage.id}, payloadId=$payloadId, endpoints=$connectedEndpoints, timestamp=${System.currentTimeMillis()}")
                } catch (e: Exception) {
                    Log.e(TAG, "MEDIA_FILE_SEND_FAILED: reportId=${finalMessage.id}, payloadId=$payloadId, error=${e.message}, timestamp=${System.currentTimeMillis()}")
                    activeOutgoingPayloads.remove(payloadId)
                    onFileTransferEnded()
                    throw e
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message or media payloads", e)
            if (e is ApiException) {
                Log.w(TAG, "ApiErrorCode=${e.statusCode} during send. Cleaning up stale endpoints...")
                connectedEndpoints.forEach { ep ->
                    val remoteNode = endpointToNodeMap.remove(ep)
                    peerStates.remove(ep)
                    if (remoteNode != null && nodeSessions[remoteNode]?.endpointId == ep) {
                        nodeSessions[remoteNode] = NodeSession(remoteNode, ep, NodeConnectionState.DISCONNECTED, ConnectionDirection.UNKNOWN)
                    }
                }
                updateSessionsSummary()
                updateConnectedPeerCount()
            }
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
        @Synchronized
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            val remoteNodeId = info.endpointName
            val currentState = nodeSessions[remoteNodeId]?.state ?: NodeConnectionState.DISCONNECTED

            Log.i(TAG, "CONNECTION_INITIATED: nodeId=$remoteNodeId, endpointId=$endpointId, currentState=$currentState")

            if (_diagnostics.value.status == MeshTransportStatus.STOPPED) {
                Log.w(TAG, "Rejecting connection initiated from $endpointId ($remoteNodeId) after stop()")
                connectionsClient.rejectConnection(endpointId)
                return
            }

            val eventMsg = "Connection initiated: $endpointId ($remoteNodeId)"
            updateDiagnostics { it.copy(lastConnectionEvent = eventMsg) }

            endpointToNodeMap[endpointId] = remoteNodeId
            peerStates[endpointId] = EndpointState.CONNECTING
            val reqCount = (nodeSessions[remoteNodeId]?.requestCount ?: 0) + 1
            nodeSessions[remoteNodeId] = NodeSession(remoteNodeId, endpointId, NodeConnectionState.CONNECTING, ConnectionDirection.INITIATED_BY_REMOTE, reqCount)
            updateSessionsSummary()

            // Automatically accept connections from other RSQ nodes.
            connectionsClient.acceptConnection(endpointId, payloadCallback)
                .addOnFailureListener { e ->
                    val failMsg = "Failed to accept connection from $endpointId ($remoteNodeId): ${e.message}"
                    Log.e(TAG, failMsg, e)
                    if (nodeSessions[remoteNodeId]?.endpointId == endpointId && nodeSessions[remoteNodeId]?.state != NodeConnectionState.CONNECTED) {
                        nodeSessions[remoteNodeId] = NodeSession(remoteNodeId, endpointId, NodeConnectionState.DISCONNECTED, ConnectionDirection.UNKNOWN)
                        peerStates.remove(endpointId)
                        updateSessionsSummary()
                    }
                    updateDiagnostics { it.copy(lastError = failMsg) }
                }
        }

        @Synchronized
        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            val remoteNodeId = endpointToNodeMap[endpointId] ?: "unknown_node"
            val previousState = nodeSessions[remoteNodeId]?.state ?: NodeConnectionState.DISCONNECTED

            if (_diagnostics.value.status == MeshTransportStatus.STOPPED) {
                Log.w(TAG, "Ignoring connection result for $endpointId ($remoteNodeId) after stop()")
                connectionsClient.disconnectFromEndpoint(endpointId)
                return
            }

            val statusCode = result.status.statusCode
            val statusMsg = "Code: $statusCode Msg: ${result.status.statusMessage}"

            if (statusCode == ConnectionsStatusCodes.STATUS_OK) {
                val reqCount = nodeSessions[remoteNodeId]?.requestCount ?: 1
                val dir = nodeSessions[remoteNodeId]?.direction ?: ConnectionDirection.UNKNOWN
                nodeSessions[remoteNodeId] = NodeSession(remoteNodeId, endpointId, NodeConnectionState.CONNECTED, dir, reqCount)
                peerStates[endpointId] = EndpointState.CONNECTED

                Log.i(TAG, "CONNECTION_RESULT: nodeId=$remoteNodeId, endpointId=$endpointId, status=STATUS_OK, previousState=$previousState, newState=CONNECTED")
                val eventMsg = "Connected to node: $remoteNodeId"
                updateDiagnostics { it.copy(lastConnectionEvent = eventMsg) }
                updateSessionsSummary()
                updateConnectedPeerCount()
            } else {
                Log.w(TAG, "CONNECTION_RESULT: nodeId=$remoteNodeId, endpointId=$endpointId, status=$statusMsg, previousState=$previousState, newState=DISCONNECTED")
                val eventMsg = "Connection failed: $statusMsg"
                if (nodeSessions[remoteNodeId]?.endpointId == endpointId) {
                    nodeSessions[remoteNodeId] = NodeSession(remoteNodeId, endpointId, NodeConnectionState.DISCONNECTED, ConnectionDirection.UNKNOWN)
                    peerStates.remove(endpointId)
                    updateSessionsSummary()
                }
                updateDiagnostics { it.copy(lastConnectionEvent = eventMsg) }
            }
        }

        @Synchronized
        override fun onDisconnected(endpointId: String) {
            val remoteNodeId = endpointToNodeMap.remove(endpointId) ?: "unknown_node"
            val previousState = nodeSessions[remoteNodeId]?.state ?: NodeConnectionState.UNKNOWN
            Log.i(TAG, "CONNECTION_DISCONNECTED: nodeId=$remoteNodeId, endpointId=$endpointId, previousState=$previousState")

            peerStates.remove(endpointId)
            if (nodeSessions[remoteNodeId]?.endpointId == endpointId) {
                nodeSessions[remoteNodeId] = NodeSession(remoteNodeId, endpointId, NodeConnectionState.DISCONNECTED, ConnectionDirection.UNKNOWN)
                updateSessionsSummary()
            }

            val eventMsg = "Disconnected: $endpointId ($remoteNodeId)"
            updateDiagnostics { it.copy(lastConnectionEvent = eventMsg) }
            updateConnectedPeerCount()
        }
    }

    /**
     * Callback for endpoint discovery events.
     */
    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        @Synchronized
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (_diagnostics.value.status == MeshTransportStatus.STOPPED) return

            val remoteNodeId = info.endpointName
            val eventMsg = "Found: $endpointId ($remoteNodeId)"
            Log.d(TAG, eventMsg)
            updateDiagnostics { it.copy(lastDiscoveredEndpoint = eventMsg) }

            endpointToNodeMap[endpointId] = remoteNodeId

            val existingSession = nodeSessions[remoteNodeId]
            if (existingSession != null && (existingSession.state == NodeConnectionState.CONNECTED || existingSession.state == NodeConnectionState.CONNECTING)) {
                Log.i(TAG, "SKIP_DUPLICATE_DISCOVERY: Remote node $remoteNodeId is already in state ${existingSession.state} (endpointId: ${existingSession.endpointId}). Ignoring duplicate discovery of endpoint $endpointId.")
                return
            }

            // Deterministic connection ownership: lower nodeId string value initiates outgoing request
            val shouldWeInitiate = (localNodeId < remoteNodeId)
            if (!shouldWeInitiate) {
                Log.i(TAG, "DETERMINISTIC_OWNERSHIP: Local nodeId ($localNodeId) > Remote nodeId ($remoteNodeId). Skipping outgoing requestConnection for $remoteNodeId ($endpointId). Waiting for remote node to initiate.")
                nodeSessions[remoteNodeId] = NodeSession(remoteNodeId, endpointId, NodeConnectionState.DISCOVERED, ConnectionDirection.INITIATED_BY_REMOTE)
                updateSessionsSummary()
                return
            }

            // ATOMIC RESERVATION BEFORE CALLING requestConnection
            val reqCount = (existingSession?.requestCount ?: 0) + 1
            nodeSessions[remoteNodeId] = NodeSession(remoteNodeId, endpointId, NodeConnectionState.CONNECTING, ConnectionDirection.INITIATED_BY_US, reqCount)
            peerStates[endpointId] = EndpointState.CONNECTING
            updateSessionsSummary()

            val connectingNodeIds = nodeSessions.filter { it.value.state == NodeConnectionState.CONNECTING }.keys
            val connectedEndpointIds = nodeSessions.filter { it.value.state == NodeConnectionState.CONNECTED }.map { it.value.endpointId }

            Log.i(TAG, "REQUEST_CONNECTION_ATTEMPT: nodeId=$remoteNodeId, endpointId=$endpointId, localNodeId=$localNodeId, currentNodeState=${existingSession?.state ?: "DISCONNECTED"}, existingEndpointForNode=${existingSession?.endpointId}, connectedEndpointIds=$connectedEndpointIds, connectingNodeIds=$connectingNodeIds")

            connectionsClient.requestConnection(
                localNodeId,
                endpointId,
                connectionLifecycleCallback
            ).addOnSuccessListener {
                Log.i(TAG, "REQUEST_CONNECTION_RESULT: nodeId=$remoteNodeId, endpointId=$endpointId, statusCode=SUCCESS")
            }.addOnFailureListener { e ->
                val apiException = e as? ApiException
                val statusCode = apiException?.statusCode ?: -1

                Log.i(TAG, "REQUEST_CONNECTION_RESULT: nodeId=$remoteNodeId, endpointId=$endpointId, statusCode=$statusCode, message=${e.message}")

                if (statusCode == ConnectionsStatusCodes.STATUS_ALREADY_CONNECTED_TO_ENDPOINT) {
                    Log.i(TAG, "STATUS_ALREADY_CONNECTED_TO_ENDPOINT (8003) handled cleanly for $remoteNodeId ($endpointId). Active connection preserved.")
                    // Do NOT disconnect, do NOT mark error in diagnostics
                } else {
                    val failMsg = "Request connection failed for $endpointId ($remoteNodeId): ${e.message}"
                    Log.w(TAG, failMsg)
                    if (nodeSessions[remoteNodeId]?.endpointId == endpointId && nodeSessions[remoteNodeId]?.state != NodeConnectionState.CONNECTED) {
                        nodeSessions[remoteNodeId] = NodeSession(remoteNodeId, endpointId, NodeConnectionState.DISCONNECTED, ConnectionDirection.UNKNOWN)
                        peerStates.remove(endpointId)
                        updateSessionsSummary()
                    }
                    updateDiagnostics { it.copy(lastError = failMsg) }
                }
            }
        }

        @Synchronized
        override fun onEndpointLost(endpointId: String) {
            val remoteNodeId = endpointToNodeMap[endpointId] ?: "unknown_node"
            Log.d(TAG, "Lost discovery of endpoint: $endpointId ($remoteNodeId)")
            if (nodeSessions[remoteNodeId]?.state == NodeConnectionState.DISCOVERED) {
                nodeSessions.remove(remoteNodeId)
                endpointToNodeMap.remove(endpointId)
                peerStates.remove(endpointId)
                updateSessionsSummary()
            }
        }
    }

    /**
     * Payload handling for BYTES and FILE payloads.
     */
    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val timestamp = System.currentTimeMillis()
            val remoteNodeId = endpointToNodeMap[endpointId] ?: endpointId
            when (payload.type) {
                Payload.Type.BYTES -> {
                    Log.i(TAG, "MESH_BYTES_RECEIVED: endpointId=$endpointId ($remoteNodeId), payloadId=${payload.id}, timestamp=$timestamp")
                    val bytes = payload.asBytes() ?: return
                    val json = String(bytes, Charsets.UTF_8)

                    try {
                        val message = Json.decodeFromString<MeshMessage>(json)
                        validateMessage(message)

                        Log.i(TAG, "MESH_MESSAGE_DECODED: reportId=${message.id}, originNodeId=${message.originNodeId}, mediaItems=${message.mediaItems.size}")

                        if (message.mediaItems.isNotEmpty()) {
                            pendingMediaRegistry.registerMetadata(message.mediaItems)
                        }

                        transportScope?.launch {
                            _incomingMessages.emit(message)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to deserialize or validate MeshMessage from $endpointId ($remoteNodeId)", e)
                    }
                }
                Payload.Type.FILE -> {
                    val filePayload = payload.asFile()
                    val uri = filePayload?.asUri()

                    Log.i(TAG, "MEDIA_FILE_RECEIVED: payloadId=${payload.id}, type=FILE, endpointId=$endpointId ($remoteNodeId), uri=$uri, timestamp=$timestamp")
                    Log.i(TAG, "FILE_TRANSFER_STATE: endpointId=$endpointId ($remoteNodeId), payloadId=${payload.id}, status=RECEIVED_START, bytes=0, uri=$uri, timestamp=$timestamp")

                    onFileTransferStarted()

                    if (uri != null) {
                        incomingPayloadUris[payload.id] = uri
                    }
                }
                else -> Log.v(TAG, "Ignoring payload type ${payload.type} from $endpointId")
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            val timestamp = System.currentTimeMillis()
            val remoteNodeId = endpointToNodeMap[endpointId] ?: endpointId

            // Clean up active outgoing payload reference on sender side when complete/failed
            if (activeOutgoingPayloads.containsKey(update.payloadId)) {
                if (update.status == PayloadTransferUpdate.Status.SUCCESS ||
                    update.status == PayloadTransferUpdate.Status.FAILURE ||
                    update.status == PayloadTransferUpdate.Status.CANCELED) {
                    activeOutgoingPayloads.remove(update.payloadId)
                    Log.i(TAG, "SENDER_FILE_TRANSFER_FINISHED: payloadId=${update.payloadId}, status=${update.status}, endpointId=$endpointId, timestamp=$timestamp")
                    onFileTransferEnded()
                }
            }

            when (update.status) {
                PayloadTransferUpdate.Status.IN_PROGRESS -> {
                    Log.d(TAG, "MEDIA_TRANSFER_UPDATE: payloadId=${update.payloadId}, endpointId=$endpointId ($remoteNodeId), status=IN_PROGRESS, bytesTransferred=${update.bytesTransferred}, totalBytes=${update.totalBytes}, timestamp=$timestamp")
                    Log.d(TAG, "FILE_TRANSFER_STATE: endpointId=$endpointId ($remoteNodeId), payloadId=${update.payloadId}, status=IN_PROGRESS, bytes=${update.bytesTransferred}/${update.totalBytes}, timestamp=$timestamp")
                    pendingMediaRegistry.onTransferProgress(update.payloadId, update.bytesTransferred, update.totalBytes, endpointId)
                }
                PayloadTransferUpdate.Status.SUCCESS -> {
                    Log.i(TAG, "MEDIA_TRANSFER_UPDATE: payloadId=${update.payloadId}, endpointId=$endpointId ($remoteNodeId), status=SUCCESS, bytesTransferred=${update.totalBytes}, totalBytes=${update.totalBytes}, timestamp=$timestamp")
                    Log.i(TAG, "FILE_TRANSFER_STATE: endpointId=$endpointId ($remoteNodeId), payloadId=${update.payloadId}, status=SUCCESS, bytes=${update.totalBytes}/${update.totalBytes}, timestamp=$timestamp")
                    val uri = incomingPayloadUris.remove(update.payloadId)
                    if (uri != null) {
                        pendingMediaRegistry.onFilePayloadCompleted(update.payloadId, uri, update.totalBytes, endpointId)
                    }
                    onFileTransferEnded()
                }
                PayloadTransferUpdate.Status.FAILURE, PayloadTransferUpdate.Status.CANCELED -> {
                    val statusName = if (update.status == PayloadTransferUpdate.Status.CANCELED) "CANCELED" else "FAILURE"
                    Log.e(TAG, "MEDIA_TRANSFER_UPDATE: payloadId=${update.payloadId}, endpointId=$endpointId ($remoteNodeId), status=$statusName (${update.status}), bytesTransferred=${update.bytesTransferred}, totalBytes=${update.totalBytes}, timestamp=$timestamp")
                    Log.e(TAG, "FILE_TRANSFER_STATE: endpointId=$endpointId ($remoteNodeId), payloadId=${update.payloadId}, status=$statusName (${update.status}), bytes=${update.bytesTransferred}/${update.totalBytes}, timestamp=$timestamp")
                    incomingPayloadUris.remove(update.payloadId)
                    pendingMediaRegistry.onTransferFailed(update.payloadId, update.status, update.totalBytes, endpointId)
                    onFileTransferEnded()
                }
            }
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
        val count = nodeSessions.count { it.value.state == NodeConnectionState.CONNECTED }
        _connectedPeerCount.value = count
        updateDiagnostics { it.copy(connectedPeerCount = count) }
    }

    private fun updateSessionsSummary() {
        val nodeSummary = nodeSessions.values.joinToString("\n") { session ->
            "Node: ${session.nodeId.take(8)}... | EP: ${session.endpointId} | State: ${session.state} | Dir: ${session.direction}"
        }.ifEmpty { "None" }

        val activePayloadsSummary = if (activeOutgoingPayloads.isNotEmpty() || incomingPayloadUris.isNotEmpty()) {
            "Outgoing Active: ${activeOutgoingPayloads.keys.joinToString()} | Incoming Active URIs: ${incomingPayloadUris.keys.joinToString()}"
        } else {
            "None"
        }

        updateDiagnostics {
            it.copy(
                activeNodeSessionsSummary = nodeSummary,
                activeFilePayloadsSummary = activePayloadsSummary
            )
        }
    }

    private fun updateStatus(status: MeshTransportStatus) {
        updateDiagnostics { it.copy(status = status) }
    }

    private fun updateDiagnostics(update: (MeshDiagnostics) -> MeshDiagnostics) {
        _diagnostics.value = update(_diagnostics.value)
    }
}
