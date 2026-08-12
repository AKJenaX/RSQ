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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MeshRelayEngineTest {

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
    fun `New message should be persisted and emitted`() = runTest(testDispatcher) {
        engine = MeshRelayEngine(fakeTransport, fakeRepository, fakeIdentityProvider, backgroundScope)
        val message = createTestMessage("msg-1", ttl = 3)
        
        fakeTransport.emitMessage(message)
        
        assertTrue("Message should be in repository", fakeRepository.hasMessage("msg-1"))
    }

    @Test
    fun `Duplicate message should not be emitted or relayed`() = runTest(testDispatcher) {
        engine = MeshRelayEngine(fakeTransport, fakeRepository, fakeIdentityProvider, backgroundScope)
        val message = createTestMessage("msg-1", ttl = 3)
        fakeRepository.saveMessage(message)
        
        fakeTransport.emitMessage(message)
        
        assertEquals("Should not relay duplicate", 0, fakeTransport.sentMessages.size)
    }

    @Test
    fun `Message with TTL 0 should not be relayed`() = runTest(testDispatcher) {
        engine = MeshRelayEngine(fakeTransport, fakeRepository, fakeIdentityProvider, backgroundScope)
        val message = createTestMessage("msg-1", ttl = 0)
        
        fakeTransport.emitMessage(message)
        
        assertEquals("Should not relay with TTL 0", 0, fakeTransport.sentMessages.size)
    }

    @Test
    fun `Message with TTL gt 0 should be relayed with decremented TTL`() = runTest(testDispatcher) {
        engine = MeshRelayEngine(fakeTransport, fakeRepository, fakeIdentityProvider, backgroundScope)
        val message = createTestMessage("msg-1", ttl = 3)
        
        fakeTransport.emitMessage(message)
        
        assertEquals("Should relay exactly once", 1, fakeTransport.sentMessages.size)
        val relayed = fakeTransport.sentMessages[0]
        assertEquals("msg-1", relayed.id)
        assertEquals(2, relayed.ttl)
        assertEquals("local-node", relayed.senderNodeId)
        assertEquals(message.originNodeId, relayed.originNodeId)
    }

    @Test
    fun `Relay should preserve message fields unrelated to routing`() = runTest(testDispatcher) {
        engine = MeshRelayEngine(fakeTransport, fakeRepository, fakeIdentityProvider, backgroundScope)
        val message = createTestMessage("msg-1", ttl = 1).copy(
            payload = "Target Payload",
            priority = Priority.HIGH,
            latitude = 1.0,
            longitude = 2.0
        )
        
        fakeTransport.emitMessage(message)
        
        assertEquals("Should relay", 1, fakeTransport.sentMessages.size)
        val relayed = fakeTransport.sentMessages[0]
        assertEquals("Target Payload", relayed.payload)
        assertEquals(Priority.HIGH, relayed.priority)
        assertEquals(1.0, relayed.latitude)
        assertEquals(2.0, relayed.longitude)
    }

    private fun createTestMessage(id: String, ttl: Int): MeshMessage {
        return MeshMessage(
            id = id,
            senderNodeId = "remote-node",
            originNodeId = "origin-node",
            messageType = MeshMessageType.SOS,
            timestamp = System.currentTimeMillis(),
            latitude = null,
            longitude = null,
            priority = Priority.MEDIUM,
            payload = "Test",
            ttl = ttl
        )
    }

    // Fakes
    private class FakeMeshTransport : MeshTransport {
        val sentMessages = mutableListOf<MeshMessage>()
        private val incomingFlow = MutableSharedFlow<MeshMessage>(replay = 10)

        suspend fun emitMessage(msg: MeshMessage) = incomingFlow.emit(msg)

        override fun start() {}
        override fun stop() {}
        override fun discoverPeers() {}
        override suspend fun sendMessage(message: MeshMessage): Result<Unit> {
            sentMessages.add(message)
            return Result.success(Unit)
        }
        override fun observeIncomingMessages(): Flow<MeshMessage> = incomingFlow
        override fun observeConnectedPeerCount(): Flow<Int> = TODO()
        override fun observeDiagnostics(): Flow<MeshDiagnostics> = TODO()
    }

    private class FakeMeshMessageRepository : MeshMessageRepository {
        private val messages = mutableMapOf<String, MeshMessage>()
        private val delivered = mutableSetOf<String>()

        override fun saveMessage(message: MeshMessage) { messages[message.id] = message }
        override fun getPendingMessages(): List<MeshMessage> = messages.values.filter { it.id !in delivered }
        override fun markMessageDelivered(messageId: String) { delivered.add(messageId) }
        override fun hasMessage(messageId: String): Boolean = messageId in messages
    }

    private class FakeNodeIdentityProvider(private val id: String) : NodeIdentityProvider {
        override fun getNodeId(): String = id
    }
}
