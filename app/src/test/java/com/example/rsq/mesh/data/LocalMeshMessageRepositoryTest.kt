package com.example.rsq.mesh.data

import android.content.Context
import android.content.SharedPreferences
import com.example.rsq.data.model.Priority
import com.example.rsq.mesh.model.MeshMessage
import com.example.rsq.mesh.model.MeshMessageType
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

class LocalMeshMessageRepositoryTest {

    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor
    private lateinit var repository: LocalMeshMessageRepository

    private val prefData = mutableMapOf<String, Any>()
    private val stringSets = mutableMapOf<String, Set<String>>()

    @Before
    fun setup() {
        mockContext = mock()
        mockPrefs = mock()
        mockEditor = mock()

        whenever(mockContext.getSharedPreferences(any(), eq(Context.MODE_PRIVATE))).thenReturn(mockPrefs)
        whenever(mockPrefs.edit()).thenReturn(mockEditor)

        whenever(mockEditor.putString(any(), anyOrNull())).thenAnswer {
            val key = it.arguments[0] as String
            val value = it.arguments[1] as String?
            if (value != null) prefData[key] = value else prefData.remove(key)
            mockEditor
        }
        whenever(mockEditor.putLong(any(), any())).thenAnswer {
            prefData[it.arguments[0] as String] = it.arguments[1] as Long
            mockEditor
        }
        whenever(mockEditor.putInt(any(), any())).thenAnswer {
            prefData[it.arguments[0] as String] = it.arguments[1] as Int
            mockEditor
        }
        whenever(mockEditor.putStringSet(any(), anyOrNull())).thenAnswer {
            @Suppress("UNCHECKED_CAST")
            val key = it.arguments[0] as String
            val value = it.arguments[1] as Set<String>?
            if (value != null) stringSets[key] = value else stringSets.remove(key)
            mockEditor
        }
        whenever(mockEditor.apply()).thenAnswer { Unit }
        whenever(mockEditor.commit()).thenReturn(true)

        whenever(mockPrefs.getString(any(), anyOrNull())).thenAnswer {
            val key = it.arguments[0] as String
            val def = it.arguments[1] as String?
            prefData[key] as? String ?: def
        }
        whenever(mockPrefs.getLong(any(), any())).thenAnswer {
            val key = it.arguments[0] as String
            val def = it.arguments[1] as Long
            prefData[key] as? Long ?: def
        }
        whenever(mockPrefs.getInt(any(), any())).thenAnswer {
            val key = it.arguments[0] as String
            val def = it.arguments[1] as Int
            prefData[key] as? Int ?: def
        }
        whenever(mockPrefs.getStringSet(any(), anyOrNull())).thenAnswer {
            val key = it.arguments[0] as String
            @Suppress("UNCHECKED_CAST")
            val def = it.arguments[1] as Set<String>?
            stringSets[key] ?: def
        }
        whenever(mockPrefs.contains(any())).thenAnswer {
            prefData.containsKey(it.arguments[0] as String)
        }

        repository = LocalMeshMessageRepository(mockContext)
    }

    @Test
    fun `Save and reload a message with a 72-hour expiration and verify the timestamp is unchanged`() {
        val customExp = System.currentTimeMillis() + 72 * 60 * 60 * 1000L
        val original = MeshMessage(
            id = "test-72",
            senderNodeId = "A",
            originNodeId = "A",
            messageType = MeshMessageType.SOS,
            timestamp = System.currentTimeMillis(),
            latitude = 0.0,
            longitude = 0.0,
            priority = Priority.HIGH,
            payload = "Payload",
            ttl = 3,
            expirationTimestamp = customExp
        )

        repository.saveMessage(original)

        val pending = repository.getPendingMessages()
        assertEquals(1, pending.size)
        assertEquals(customExp, pending[0].expirationTimestamp)
    }

    @Test
    fun `Verify existing mesh duplicate suppression and message identity behavior remain unchanged`() {
        val original = MeshMessage(
            id = "dup-1",
            senderNodeId = "A",
            originNodeId = "A",
            messageType = MeshMessageType.SOS,
            timestamp = System.currentTimeMillis(),
            latitude = 0.0,
            longitude = 0.0,
            priority = Priority.HIGH,
            payload = "P",
            ttl = 3
        )

        repository.saveMessage(original)
        assertEquals(1, repository.getPendingMessages().size)
        assertEquals(true, repository.hasMessage("dup-1"))

        repository.saveMessage(original.copy(ttl = 2)) // Duplicate id
        assertEquals(1, repository.getPendingMessages().size) // Should still be 1 (set ensures uniqueness)
    }
}
