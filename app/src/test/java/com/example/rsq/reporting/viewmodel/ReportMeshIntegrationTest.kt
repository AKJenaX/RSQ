package com.example.rsq.reporting.viewmodel

import com.example.rsq.data.model.Priority
import com.example.rsq.mesh.domain.MeshRelayEngine
import com.example.rsq.mesh.domain.MeshTransport
import com.example.rsq.mesh.domain.MeshMessageRepository
import com.example.rsq.mesh.domain.NodeIdentityProvider
import com.example.rsq.mesh.model.MeshMessage
import com.example.rsq.mesh.model.MeshMessageType
import com.example.rsq.mesh.model.MeshDiagnostics
import com.example.rsq.reporting.data.ReportRepository
import com.example.rsq.reporting.model.Report
import com.example.rsq.reporting.model.ReportState
import com.example.rsq.reporting.model.ReportStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReportMeshIntegrationTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeReportRepository: FakeReportRepository
    private lateinit var fakeMeshTransport: FakeMeshTransport
    private lateinit var fakeMeshRepository: FakeMeshMessageRepository
    private lateinit var fakeIdentityProvider: FakeNodeIdentityProvider
    private lateinit var relayEngine: MeshRelayEngine
    private lateinit var viewModel: ReportViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeReportRepository = FakeReportRepository()
        fakeMeshTransport = FakeMeshTransport()
        fakeMeshRepository = FakeMeshMessageRepository()
        fakeIdentityProvider = FakeNodeIdentityProvider("local-node")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `submitting a report should trigger mesh broadcast with correct fields including senderNodeId`() = runTest(testDispatcher) {
        relayEngine = MeshRelayEngine(
            transport = fakeMeshTransport,
            repository = fakeMeshRepository,
            identityProvider = fakeIdentityProvider,
            scope = backgroundScope
        )
        
        viewModel = ReportViewModel(
            repository = fakeReportRepository,
            relayEngine = relayEngine,
            identityProvider = fakeIdentityProvider
        )

        val report = Report(
            id = "rep-1",
            userId = "user-1",
            title = "Emergency",
            description = "Help me",
            severity = "HIGH",
            timestamp = 1000L,
            latitude = 12.34,
            longitude = 56.78
        )

        viewModel.submitReport(report)
        advanceUntilIdle()

        assertEquals(1, fakeMeshTransport.sentMessages.size)
        val meshMsg = fakeMeshTransport.sentMessages[0]

        assertEquals("rep-1", meshMsg.id)
        assertEquals("local-node", meshMsg.senderNodeId) // Verified fix 1
        assertEquals("user-1", meshMsg.originNodeId)
        assertEquals(MeshMessageType.REPORT_RELAY, meshMsg.messageType)
        assertEquals(Priority.HIGH, meshMsg.priority)
        assertEquals(12.34, meshMsg.latitude ?: 0.0, 0.0001)
        assertEquals(56.78, meshMsg.longitude ?: 0.0, 0.0001)
        assertEquals("Emergency: Help me", meshMsg.payload)
        assertEquals(3, meshMsg.ttl)
    }

    @Test
    fun `Firebase SUCCESS and Mesh SUCCESS should result in Success state`() = runTest(testDispatcher) {
        relayEngine = MeshRelayEngine(fakeMeshTransport, fakeMeshRepository, fakeIdentityProvider, backgroundScope)
        viewModel = ReportViewModel(fakeReportRepository, relayEngine, fakeIdentityProvider)
        val report = createTestReport("res-1")
        
        viewModel.submitReport(report)
        advanceUntilIdle()
        
        val state = viewModel.reportState.value as ReportState.Success
        assertEquals("Report submitted successfully", state.message)
    }

    @Test
    fun `Firebase SUCCESS and Mesh FAILURE should result in Success state`() = runTest(testDispatcher) {
        relayEngine = MeshRelayEngine(fakeMeshTransport, fakeMeshRepository, fakeIdentityProvider, backgroundScope)
        viewModel = ReportViewModel(fakeReportRepository, relayEngine, fakeIdentityProvider)
        fakeMeshTransport.shouldFail = true
        val report = createTestReport("res-2")
        
        viewModel.submitReport(report)
        advanceUntilIdle()
        
        val state = viewModel.reportState.value as ReportState.Success
        assertEquals("Report submitted successfully", state.message)
    }

    @Test
    fun `Firebase FAILURE and Mesh SUCCESS should result in Success state with special message`() = runTest(testDispatcher) {
        relayEngine = MeshRelayEngine(fakeMeshTransport, fakeMeshRepository, fakeIdentityProvider, backgroundScope)
        viewModel = ReportViewModel(fakeReportRepository, relayEngine, fakeIdentityProvider)
        fakeReportRepository.shouldFail = true
        val report = createTestReport("res-3")
        
        viewModel.submitReport(report)
        advanceUntilIdle()
        
        val state = viewModel.reportState.value as ReportState.Success
        assertEquals("Offline Mesh broadcast successful. Cloud sync pending.", state.message)
    }

    @Test
    fun `Firebase FAILURE and Mesh FAILURE should result in Error state`() = runTest(testDispatcher) {
        relayEngine = MeshRelayEngine(fakeMeshTransport, fakeMeshRepository, fakeIdentityProvider, backgroundScope)
        viewModel = ReportViewModel(fakeReportRepository, relayEngine, fakeIdentityProvider)
        fakeReportRepository.shouldFail = true
        fakeMeshTransport.shouldFail = true
        val report = createTestReport("res-4")
        
        viewModel.submitReport(report)
        advanceUntilIdle()
        
        assertTrue(viewModel.reportState.value is ReportState.Error)
    }

    @Test
    fun `receiving a mesh report should update meshReports flow and deduplicate`() = runTest(testDispatcher) {
        relayEngine = MeshRelayEngine(fakeMeshTransport, fakeMeshRepository, fakeIdentityProvider, backgroundScope)
        viewModel = ReportViewModel(fakeReportRepository, relayEngine, fakeIdentityProvider)

        val meshMsg = MeshMessage(
            id = "rep-2",
            senderNodeId = "node-peer",
            originNodeId = "user-victim",
            messageType = MeshMessageType.REPORT_RELAY,
            timestamp = 2000L,
            latitude = 1.0,
            longitude = 2.0,
            priority = Priority.HIGH,
            payload = "Fire: At the building",
            ttl = 3
        )

        fakeMeshTransport.emitMessage(meshMsg)
        fakeMeshTransport.emitMessage(meshMsg) // Send twice
        advanceUntilIdle()

        val meshReports = viewModel.meshReports.value
        assertEquals(1, meshReports.size)
        
        val report = meshReports[0]
        assertEquals("rep-2", report.id)
        assertEquals("user-victim", report.userId)
        assertEquals("Fire", report.title)
        assertEquals("At the building", report.description)
        assertTrue(report.isOffline)
    }

    private fun createTestReport(id: String) = Report(id = id, userId = "u1", title = "T", description = "D")

    // Fakes
    private class FakeReportRepository : ReportRepository(null) {
        var shouldFail = false
        override suspend fun submitReport(report: Report): Result<Unit> {
            return if (shouldFail) Result.failure(Exception("Cloud offline")) else Result.success(Unit)
        }
        override suspend fun getReports(userId: String): Result<List<Report>> = Result.success(emptyList())
    }

    private class FakeMeshTransport : MeshTransport {
        val sentMessages = mutableListOf<MeshMessage>()
        var shouldFail = false
        private val incomingFlow = MutableSharedFlow<MeshMessage>(replay = 10)

        suspend fun emitMessage(msg: MeshMessage) = incomingFlow.emit(msg)

        override fun start() {}
        override fun stop() {}
        override fun discoverPeers() {}
        override suspend fun sendMessage(message: MeshMessage): Result<Unit> {
            if (shouldFail) return Result.failure(Exception("Mesh unavailable"))
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
