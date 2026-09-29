package com.example.rsq.mesh.domain

import com.example.rsq.data.model.Priority
import com.example.rsq.mesh.model.MeshDiagnostics
import com.example.rsq.mesh.model.MeshMessage
import com.example.rsq.mesh.model.MeshMessageType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class TTLExpirationRelayTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeTransport: FakeMeshTransport
    private lateinit var fakeRepository: FakeMeshMessageRepository
    private lateinit var fakeIdentityProvider: FakeNodeIdentityProvider
    private lateinit var engine: MeshRelayEngine

    @Before
    fun setup() {
        fakeTransport = FakeMeshTransport()
        fakeRepository = FakeMeshMessageRepository()
        fakeIdentityProvider = FakeNodeIdentityProvider("local-node")
    }

    @Test
    fun `Expired message should NOT be persisted or relayed`() = runTest(testDispatcher) {
        engine = MeshRelayEngine(fakeTransport, fakeRepository, fakeIdentityProvider, backgroundScope)
        val now = System.currentTimeMillis()
        val expiredMsg = createMessage(id = "expired-1", expTimestamp = now - 5000L)

        fakeTransport.emitMessage(expiredMsg)

        assertFalse("Expired message should NOT be stored in repository", fakeRepository.hasMessage("expired-1"))
        assertTrue("Expired message should NOT be sent via transport", fakeTransport.sentMessages.isEmpty())
    }

    @Test
    fun `Message at exact expiration boundary (expirationTimestamp == currentTime) should NOT be relayed`() = runTest(testDispatcher) {
        engine = MeshRelayEngine(fakeTransport, fakeRepository, fakeIdentityProvider, backgroundScope)
        val now = System.currentTimeMillis()
        val boundaryMsg = createMessage(id = "boundary-1", expTimestamp = now)

        fakeTransport.emitMessage(boundaryMsg)

        assertFalse("Boundary message should NOT be stored in repository", fakeRepository.hasMessage("boundary-1"))
        assertTrue("Boundary message should NOT be sent via transport", fakeTransport.sentMessages.isEmpty())
    }

    @Test
    fun `Valid unexpired message SHOULD be persisted and relayed`() = runTest(testDispatcher) {
        engine = MeshRelayEngine(fakeTransport, fakeRepository, fakeIdentityProvider, backgroundScope)
        val now = System.currentTimeMillis()
        val validMsg = createMessage(id = "valid-1", expTimestamp = now + 50000L)

        fakeTransport.emitMessage(validMsg)

        assertTrue("Valid message SHOULD be stored in repository", fakeRepository.hasMessage("valid-1"))
        assertEquals(1, fakeTransport.sentMessages.size)
        assertEquals("valid-1", fakeTransport.sentMessages[0].id)
    }

    @Test
    fun `Legacy message with expirationTimestamp 0L SHOULD be persisted and relayed`() = runTest(testDispatcher) {
        engine = MeshRelayEngine(fakeTransport, fakeRepository, fakeIdentityProvider, backgroundScope)
        val legacyMsg = createMessage(id = "legacy-1", expTimestamp = 0L)

        fakeTransport.emitMessage(legacyMsg)

        assertTrue("Legacy message SHOULD be stored in repository", fakeRepository.hasMessage("legacy-1"))
        assertEquals(1, fakeTransport.sentMessages.size)
        assertEquals("legacy-1", fakeTransport.sentMessages[0].id)
    }

    private fun createMessage(id: String, expTimestamp: Long) = MeshMessage(
        id = id,
        senderNodeId = "remote-node",
        originNodeId = "remote-node",
        messageType = MeshMessageType.SOS,
        timestamp = System.currentTimeMillis() - 1000L,
        latitude = 0.0,
        longitude = 0.0,
        priority = Priority.HIGH,
        payload = "Emergency",
        ttl = 3,
        expirationTimestamp = expTimestamp
    )

    private class FakeMeshTransport : MeshTransport {
        val sentMessages = mutableListOf<MeshMessage>()
        private val incomingFlow = MutableSharedFlow<MeshMessage>(replay = 10)
        suspend fun emitMessage(msg: MeshMessage) = incomingFlow.emit(msg)
        override fun start() {}
        override fun stop() {}
        override fun discoverPeers() {}
        override suspend fun sendMessage(message: MeshMessage, mediaFiles: List<File>): Result<Unit> {
            sentMessages.add(message)
            return Result.success(Unit)
        }
        override fun observeIncomingMessages(): Flow<MeshMessage> = incomingFlow
        override fun observeConnectedPeerCount(): Flow<Int> = MutableSharedFlow()
        override fun observeDiagnostics(): Flow<MeshDiagnostics> = MutableSharedFlow()
    }

    private class FakeMeshMessageRepository : MeshMessageRepository {
        private val messages = mutableMapOf<String, MeshMessage>()
        override fun saveMessage(message: MeshMessage) { messages[message.id] = message }
        override fun getPendingMessages(): List<MeshMessage> = emptyList()
        override fun markMessageDelivered(messageId: String) {}
        override fun hasMessage(messageId: String): Boolean = messageId in messages
    }

    private class FakeNodeIdentityProvider(private val id: String) : NodeIdentityProvider {
        override fun getNodeId(): String = id
    }
}
