package com.example.rsq.data.repository

import android.util.Log
import com.example.rsq.data.model.SOSMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object MessageRepository {
    private val _messages = MutableStateFlow<List<SOSMessage>>(emptyList())
    val messages: StateFlow<List<SOSMessage>> = _messages.asStateFlow()

    fun sendSOS(message: SOSMessage) {
        val currentList = _messages.value.toMutableList()
        currentList.add(message)
        _messages.value = currentList
        Log.d("MessageRepository", "Message relayed across network: ${message.id}")
    }

    fun getMessages(): List<SOSMessage> {
        return _messages.value
    }
}
