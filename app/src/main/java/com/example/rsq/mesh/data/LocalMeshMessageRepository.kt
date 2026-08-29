package com.example.rsq.mesh.data

import android.content.Context
import android.content.SharedPreferences
import com.example.rsq.data.model.Priority
import com.example.rsq.mesh.domain.MeshMessageRepository
import com.example.rsq.mesh.model.MeshMessage
import com.example.rsq.mesh.model.MeshMessageType

/**
 * Implementation of [MeshMessageRepository] using Android SharedPreferences.
 * Suitable for lightweight offline persistence without adding Room.
 */
class LocalMeshMessageRepository(context: Context) : MeshMessageRepository {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun saveMessage(message: MeshMessage) {
        val editor = prefs.edit()

        // Add to tracking sets
        val allIds = getAllIds().toMutableSet()
        val pendingIds = getPendingIds().toMutableSet()

        val isNew = allIds.add(message.id)
        if (isNew) {
            pendingIds.add(message.id)
        }

        editor.putStringSet(KEY_ALL_IDS, allIds)
        editor.putStringSet(KEY_PENDING_IDS, pendingIds)

        // Save message fields
        val prefix = "msg_${message.id}_"
        editor.putString("${prefix}sender", message.senderNodeId)
        editor.putString("${prefix}origin", message.originNodeId)
        editor.putString("${prefix}type", message.messageType.name)
        editor.putLong("${prefix}timestamp", message.timestamp)
        editor.putString("${prefix}lat", message.latitude?.toString())
        editor.putString("${prefix}lng", message.longitude?.toString())
        editor.putString("${prefix}priority", message.priority.name)
        editor.putString("${prefix}payload", message.payload)
        editor.putInt("${prefix}ttl", message.ttl)
        
        // Unified fields
        editor.putString("${prefix}title", message.title)
        editor.putString("${prefix}description", message.description)
        editor.putString("${prefix}acc", message.accuracy?.toString())

        editor.apply()
    }

    override fun getPendingMessages(): List<MeshMessage> {
        val pendingIds = getPendingIds()
        return pendingIds.mapNotNull { id -> readMessage(id) }
            .sortedBy { it.timestamp }
    }

    override fun markMessageDelivered(messageId: String) {
        val pendingIds = getPendingIds().toMutableSet()
        if (pendingIds.remove(messageId)) {
            prefs.edit().putStringSet(KEY_PENDING_IDS, pendingIds).apply()
        }
    }

    override fun hasMessage(messageId: String): Boolean {
        return getAllIds().contains(messageId)
    }

    private fun readMessage(id: String): MeshMessage? {
        val prefix = "msg_${id}_"

        val sender = prefs.getString("${prefix}sender", null) ?: return null
        val origin = prefs.getString("${prefix}origin", null) ?: return null
        val typeStr = prefs.getString("${prefix}type", null) ?: return null
        val priorityStr = prefs.getString("${prefix}priority", null) ?: return null

        val messageType = try { MeshMessageType.valueOf(typeStr) } catch (e: Exception) { MeshMessageType.SOS }
        val priority = try { Priority.valueOf(priorityStr) } catch (e: Exception) { Priority.MEDIUM }

        val timestamp = prefs.getLong("${prefix}timestamp", 0L)
        val latStr = prefs.getString("${prefix}lat", null)
        val lngStr = prefs.getString("${prefix}lng", null)
        val payload = prefs.getString("${prefix}payload", "") ?: ""
        val ttl = prefs.getInt("${prefix}ttl", 0)

        // Unified fields
        val title = prefs.getString("${prefix}title", "") ?: ""
        val description = prefs.getString("${prefix}description", "") ?: ""
        val accStr = prefs.getString("${prefix}acc", null)

        return MeshMessage(
            id = id,
            senderNodeId = sender,
            originNodeId = origin,
            messageType = messageType,
            timestamp = timestamp,
            latitude = latStr?.toDoubleOrNull(),
            longitude = lngStr?.toDoubleOrNull(),
            priority = priority,
            payload = payload,
            ttl = ttl,
            title = title,
            description = description,
            accuracy = accStr?.toFloatOrNull()
        )
    }

    private fun getAllIds(): Set<String> {
        return prefs.getStringSet(KEY_ALL_IDS, emptySet()) ?: emptySet()
    }

    private fun getPendingIds(): Set<String> {
        return prefs.getStringSet(KEY_PENDING_IDS, emptySet()) ?: emptySet()
    }

    companion object {
        private const val PREFS_NAME = "mesh_message_prefs"
        private const val KEY_ALL_IDS = "all_message_ids"
        private const val KEY_PENDING_IDS = "pending_message_ids"
    }
}
