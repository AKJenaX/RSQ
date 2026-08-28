package com.example.rsq.storage.model

data class UploadState(
    val isUploading: Boolean = false,
    val downloadUrl: String? = null,
    val error: String? = null
)
