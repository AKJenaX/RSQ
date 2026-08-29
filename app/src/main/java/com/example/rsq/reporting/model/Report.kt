package com.example.rsq.reporting.model

data class Report(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val description: String = "",
    val severity: String = "MEDIUM",
    val status: ReportStatus = ReportStatus.OPEN,
    val timestamp: Long = System.currentTimeMillis(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    val imageUrl: String? = null,
    val imageUrls: List<String> = emptyList(),
    val isOffline: Boolean = false,
    val aiScore: Float = 0f,
    val detectedHazards: List<String> = emptyList(),
    val recommendedResources: List<String> = emptyList()
)
