package com.example.rsq.reporting.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.rsq.reporting.model.SyncStatus

@Entity(tableName = "reports")
data class ReportEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val description: String,
    val severity: String,
    val status: String, // String representation of ReportStatus
    val timestamp: Long,
    val latitude: Double?,
    val longitude: Double?,
    val imageUrl: String?,
    val localImagePath: String?,
    val isOffline: Boolean,
    val syncStatus: SyncStatus,
    val aiScore: Float,
    val detectedHazards: List<String>,
    val recommendedResources: List<String>
)
