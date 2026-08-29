package com.example.rsq.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rsq.data.model.Donation
import com.example.rsq.data.model.DonationSummary
import com.example.rsq.data.repository.DonationRepository
import com.example.rsq.data.repository.DonationRepositoryImpl
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class DonationViewModel(
    private val repository: DonationRepository = DonationRepositoryImpl()
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<Pair<DonationSummary, List<Donation>>>>(UiState.Loading)
    val uiState: StateFlow<UiState<Pair<DonationSummary, List<Donation>>>> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading

            combine(
                repository.getDonationSummary(),
                repository.getRecentDonations()
            ) { summary, donations ->
                summary to donations
            }.collect { (summary, donations) ->
                if (donations.isEmpty() && summary.totalAmount == 0.0) {
                    _uiState.value = UiState.Empty
                } else {
                    _uiState.value = UiState.Success(summary to donations)
                }
            }
        }
    }

    fun makeDonation(amount: Double, donorName: String) {
        viewModelScope.launch {
            val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            val newDonation = Donation(
                id = "D-${System.currentTimeMillis().toString().takeLast(6)}",
                donorName = donorName,
                amount = amount,
                date = sdf.format(Date()),
                status = "Completed"
            )
            repository.addDonation(newDonation)
        }
    }
}
