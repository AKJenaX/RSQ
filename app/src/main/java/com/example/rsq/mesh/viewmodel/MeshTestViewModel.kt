package com.example.rsq.mesh.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rsq.data.model.Priority
import com.example.rsq.mesh.domain.MeshRelayEngine
import com.example.rsq.mesh.domain.MeshTransport
import com.example.rsq.mesh.domain.NodeIdentityProvider
import com.example.rsq.mesh.model.MeshDiagnostics
import com.example.rsq.mesh.model.MeshMessage
import com.example.rsq.mesh.model.MeshMessageType
import com.example.rsq.mesh.model.MeshRelayEvent
import com.example.rsq.mesh.model.MeshTransportStatus
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class MeshTestViewModel(
    private val transport: MeshTransport,
    private val identityProvider: NodeIdentityProvider,
    private val relayEngine: MeshRelayEngine
) : ViewModel() {

    val nodeId: String = identityProvider.getNodeId()
    
    val connectedPeerCount: StateFlow<Int> = transport.observeConnectedPeerCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val diagnostics: StateFlow<MeshDiagnostics> = transport.observeDiagnostics()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MeshDiagnostics())

    private val _lastReceivedMessage = MutableStateFlow<MeshMessage?>(null)
    val lastReceivedMessage: StateFlow<MeshMessage?> = _lastReceivedMessage.asStateFlow()

    private val _relayEvents = MutableStateFlow<List<MeshRelayEvent>>(emptyList())
    val relayEvents: StateFlow<List<MeshRelayEvent>> = _relayEvents.asStateFlow()

    private val _statusMessage = MutableStateFlow<String>("Ready")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private var isStarted = false

    init {
        viewModelScope.launch {
            relayEngine.processedMessages.collect { message ->
                _lastReceivedMessage.value = message
            }
        }

        viewModelScope.launch {
            relayEngine.relayEvents.collect { event ->
                _relayEvents.value = (listOf(event) + _relayEvents.value).take(20)
            }
        }
        
        // Synchronize legacy status message with diagnostic status
        viewModelScope.launch {
            diagnostics.map { it.status }.distinctUntilChanged().collect { status ->
                _statusMessage.value = when (status) {
                    MeshTransportStatus.STOPPED -> "Stopped"
                    MeshTransportStatus.STARTING -> "Starting..."
                    MeshTransportStatus.ADVERTISING -> "Advertising..."
                    MeshTransportStatus.DISCOVERING -> "Discovering..."
                    MeshTransportStatus.READY -> "Ready / Running"
                    MeshTransportStatus.ERROR -> "Error"
                }
            }
        }
    }

    fun startMesh() {
        if (isStarted) return
        isStarted = true
        transport.start()
    }

    fun sendTestMessage() {
        viewModelScope.launch {
            val message = MeshMessage(
                id = UUID.randomUUID().toString(),
                senderNodeId = nodeId,
                originNodeId = nodeId,
                messageType = MeshMessageType.SOS,
                timestamp = System.currentTimeMillis(),
                latitude = 12.9716,
                longitude = 77.5946,
                priority = Priority.HIGH,
                payload = "RSQ mesh connectivity test",
                ttl = 3
            )
            
            val result = relayEngine.broadcastMessage(message)
            if (result.isSuccess) {
                _statusMessage.value = "Message transmission initiated"
            } else {
                _statusMessage.value = "Error: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        transport.stop()
    }
}
