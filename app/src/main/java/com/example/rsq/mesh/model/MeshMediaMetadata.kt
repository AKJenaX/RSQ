package com.example.rsq.mesh.model

import kotlinx.serialization.Serializable

/**
 * Metadata descriptor for an evidence photo attached to an offline mesh report.
 */
@Serializable
data class MeshMediaMetadata(
    val mediaId: String,
    val reportId: String,
    val filename: String,
    val mimeType: String,
    val sizeBytes: Long,
    val nearbyPayloadId: Long,
    val checksum: String
)
