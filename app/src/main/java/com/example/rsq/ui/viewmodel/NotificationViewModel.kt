package com.example.rsq.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rsq.data.model.Notification
import com.example.rsq.data.repository.NotificationRepository
import com.example.rsq.data.repository.NotificationRepositoryImpl
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class NotificationViewModel(
    private val repository: NotificationRepository = NotificationRepositoryImpl()
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<List<Notification>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<Notification>>> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            delay(1500)
            repository.getNotifications().collect { list ->
                if (list.isEmpty()) {
                    _uiState.value = UiState.Empty
                } else {
                    _uiState.value = UiState.Success(list)
                }
            }
        }
    }

    fun markAsRead(id: String) {
        println("Marked notification $id as read")
    }
}
