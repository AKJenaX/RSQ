package com.example.rsq.reporting.model

/**
 * Represents the synchronization state of a [Report] with the cloud backend.
 */
enum class SyncStatus {
    /**
     * Report exists only on the local device.
     */
    LOCAL_ONLY,

    /**
     * Report is currently being uploaded to Firebase.
     */
    SYNCING,

    /**
     * Report has been successfully uploaded to Firestore and Storage.
     */
    SYNCED,

    /**
     * Permanent failure in synchronization after retries.
     */
    FAILED
}
