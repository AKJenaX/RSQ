package com.example.rsq.mesh.domain

import com.example.rsq.mesh.model.MeshMessage

/**
 * Domain interface for persisting mesh messages locally while offline.
 */
interface MeshMessageRepository {
    /**
     * Persists a message to local storage.
     * If a message with the same ID already exists, it should be updated or ignored.
     */
    fun saveMessage(message: MeshMessage)

    /**
     * Retrieves all messages that have not yet been marked as delivered.
     */
    fun getPendingMessages(): List<MeshMessage>

    /**
     * Marks a specific message as delivered so it is no longer returned in pending lists.
     */
    fun markMessageDelivered(messageId: String)

    /**
     * Checks if a message with the given ID already exists in local storage.
     * Used for duplicate detection.
     */
    fun hasMessage(messageId: String): Boolean
}
