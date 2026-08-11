package com.example.rsq.mesh.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rsq.data.model.Priority
import com.example.rsq.mesh.domain.MeshTransport
import com.example.rsq.mesh.domain.NodeIdentityProvider
import com.example.rsq.mesh.model.MeshMessage
import com.example.rsq.mesh.model.MeshMessageType
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class MeshTestViewModel(
    private val transport: MeshTransport,
    private val identityProvider: NodeIdentityProvider
) : ViewModel() {

    val nodeId: String = identityProvider.getNodeId()
    
    val connectedPeerCount: StateFlow<Int> = transport.observeConnectedPeerCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _lastReceivedMessage = MutableStateFlow<MeshMessage?>(null)
    val lastReceivedMessage: StateFlow<MeshMessage?> = _lastReceivedMessage.asStateFlow()

    private val _statusMessage = MutableStateFlow<String>("Ready")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private var isStarted = false

    init {
        viewModelScope.launch {
            transport.observeIncomingMessages().collect { message ->
                _lastReceivedMessage.value = message
            }
        }
    }

    fun startMesh() {
        if (isStarted) return
        isStarted = true
        transport.start()
        _statusMessage.value = "Mesh transport started"
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
            
            val result = transport.sendMessage(message)
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
