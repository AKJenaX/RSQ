package com.example.rsq.mesh.model

import kotlinx.serialization.Serializable

/**
 * Diagnostic UI State representing an offline media transfer item on MeshTestScreen.
 * Exposes exact byte counts, transfer payload IDs, and SHA-256 hashes for transparent on-screen verification.
 */
@Serializable
data class MediaTransferUiState(
    val reportId: String = "",
    val mediaId: String = "",
    val filename: String = "",
    val mimeType: String = "",
    val payloadId: Long? = null,
    val endpointId: String? = null,
    val expectedSizeBytes: Long = 0L,
    val actualSizeBytes: Long? = null,
    val bytesTransferred: Long = 0L,
    val expectedSha256: String = "",
    val actualSha256: String = "",
    val sizeMatchStatus: String = "PENDING", // YES, NO, ERROR, PENDING
    val checksumMatchStatus: String = "NOT_VERIFIED", // VERIFIED, FAILED, NOT_VERIFIED, ERROR
    val status: String = "IDLE", // ANNOUNCED, RECEIVING, COMPLETED, CHECKSUM VERIFIED, PERSISTED, FAILED, CANCELED
    val lastTransferStatus: String = "PENDING",
    val saved: Boolean = false,
    val localFilePath: String? = null,
    val failureReason: String? = null
)
