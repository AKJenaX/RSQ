package com.example.rsq.reporting.model

data class Report(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
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
    val recommendedResources: List<String> = emptyList(),
    val expirationTimestamp: Long = timestamp + 24 * 60 * 60 * 1000L,
    val originUserId: String = "",
    val originUserName: String = "",
    val originCreatedAt: Long = 0L,
    val relayDeviceId: String = "",
    val relayUserId: String = "",
    val receivedViaRelay: Boolean = false
) {
    val effectiveOriginUserId: String
        get() = if (originUserId.isNotBlank()) originUserId else userId

    val effectiveOriginUserName: String
        get() = if (originUserName.isNotBlank()) originUserName else userName
}
