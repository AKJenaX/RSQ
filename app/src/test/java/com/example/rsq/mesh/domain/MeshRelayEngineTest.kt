package com.example.rsq.mesh.domain

import com.example.rsq.data.model.Priority
import com.example.rsq.mesh.model.MeshDiagnostics
import com.example.rsq.mesh.model.MeshMessage
import com.example.rsq.mesh.model.MeshMessageType
import com.example.rsq.mesh.model.MeshRelayEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
    fun `New message should be persisted and emitted with events`() = runTest(testDispatcher) {
        engine = MeshRelayEngine(fakeTransport, fakeRepository, fakeIdentityProvider, backgroundScope)
        val message = createTestMessage("msg-1", ttl = 3)
        
        val events = mutableListOf<MeshRelayEvent>()
        backgroundScope.launch { engine.relayEvents.toList(events) }
        
        fakeTransport.emitMessage(message)
        
        assertTrue("Message should be in repository", fakeRepository.hasMessage("msg-1"))
        
        assertEquals(3, events.size)
        assertEquals("RECEIVED", events[0].action)
        assertEquals("PERSISTED", events[1].action)
        assertEquals("RELAYED", events[2].action)
        assertEquals(2, events[2].ttlAfter)
    }

    @Test
    fun `Duplicate message should not be emitted or relayed and show DISCARDED event`() = runTest(testDispatcher) {
        engine = MeshRelayEngine(fakeTransport, fakeRepository, fakeIdentityProvider, backgroundScope)
        val message = createTestMessage("msg-1", ttl = 3)
        fakeRepository.saveMessage(message)
        
        val events = mutableListOf<MeshRelayEvent>()
        backgroundScope.launch { engine.relayEvents.toList(events) }
        
        fakeTransport.emitMessage(message)
        
        assertEquals("Should not relay duplicate", 0, fakeTransport.sentMessages.size)
        assertEquals(2, events.size)
        assertEquals("RECEIVED", events[0].action)
        assertEquals("DUPLICATE_DISCARDED", events[1].action)
    }

    @Test
    fun `Message with TTL 0 should not be relayed and show TTL_EXPIRED event`() = runTest(testDispatcher) {
        engine = MeshRelayEngine(fakeTransport, fakeRepository, fakeIdentityProvider, backgroundScope)
        val message = createTestMessage("msg-1", ttl = 0)
        
        val events = mutableListOf<MeshRelayEvent>()
        backgroundScope.launch { engine.relayEvents.toList(events) }
        
        fakeTransport.emitMessage(message)
        
        assertEquals(0, fakeTransport.sentMessages.size)
        assertEquals(3, events.size)
        assertEquals("RECEIVED", events[0].action)
        assertEquals("PERSISTED", events[1].action)
        assertEquals("TTL_EXPIRED", events[2].action)
    }

    @Test
    fun `Broadcast message should emit OUTBOUND event`() = runTest(testDispatcher) {
        engine = MeshRelayEngine(fakeTransport, fakeRepository, fakeIdentityProvider, backgroundScope)
        val message = createTestMessage("msg-1", ttl = 3)
        
        val events = mutableListOf<MeshRelayEvent>()
        backgroundScope.launch { engine.relayEvents.toList(events) }
        
        engine.broadcastMessage(message)
        
        assertEquals(1, events.size)
        assertEquals("OUTBOUND", events[0].action)
        assertEquals("local-node", events[0].nodeId)
    }

    @Test
    fun `Relay should preserve ID and origin while updating sender`() = runTest(testDispatcher) {
        engine = MeshRelayEngine(fakeTransport, fakeRepository, fakeIdentityProvider, backgroundScope)
        val message = createTestMessage("msg-1", ttl = 3)
        
        fakeTransport.emitMessage(message)
        
        val relayed = fakeTransport.sentMessages[0]
        assertEquals("msg-1", relayed.id)
        assertEquals("origin-node", relayed.originNodeId)
        assertEquals("local-node", relayed.senderNodeId)
        assertEquals(2, relayed.ttl)
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
