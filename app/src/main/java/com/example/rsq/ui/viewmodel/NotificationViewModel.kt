package com.example.rsq.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rsq.data.model.Notification
import com.example.rsq.data.repository.NotificationRepository
import com.example.rsq.data.repository.NotificationRepositoryImpl
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class NotificationViewModel(
    private val recipientId: String,
    private val repository: NotificationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<List<Notification>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<Notification>>> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                repository.getNotifications(recipientId).collect { list ->
                    if (list.isEmpty()) {
                        _uiState.value = UiState.Empty
                    } else {
                        _uiState.value = UiState.Success(list)
                    }
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Unable to load notifications")
            }
        }
    }

    fun markAsRead(id: String) {
        viewModelScope.launch {
            repository.markAsRead(id)
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            repository.markAllAsRead(recipientId)
        }
    }
}
