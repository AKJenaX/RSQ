package com.example.rsq.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rsq.data.model.SOSMessage
import com.example.rsq.data.repository.MessageRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class MessageViewModel : ViewModel() {
    
    val messages: StateFlow<List<SOSMessage>> = MessageRepository.messages
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    fun sendSOS(message: SOSMessage) {
        MessageRepository.sendSOS(message)
    }
}
