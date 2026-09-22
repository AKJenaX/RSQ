package com.example.rsq.nearby.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rsq.nearby.data.NearbyRepository
import com.example.rsq.nearby.model.NearbyReadiness
import com.example.rsq.nearby.model.NearbyState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NearbyViewModel(
    private val repository: NearbyRepository
) : ViewModel() {

    val nearbyState: StateFlow<NearbyState> = repository.nearbyState

    val nearbyReadiness: StateFlow<NearbyState> = repository.observeNearbyReadiness()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = NearbyState(readiness = NearbyReadiness.NOT_DETERMINED)
        )

    fun startDetection() {
        viewModelScope.launch {
            repository.startDetection()
        }
    }
}
