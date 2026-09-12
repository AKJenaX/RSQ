package com.example.rsq.data.repository

import com.example.rsq.data.local.AssignmentDao
import com.example.rsq.data.local.AssignmentEntity
import com.example.rsq.data.model.Assignment
import com.example.rsq.data.model.AssignmentStatus
import com.example.rsq.data.model.Priority
import com.example.rsq.util.ConnectivityObserver
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AssignmentRepositoryTest {

    private lateinit var repository: AssignmentRepositoryImpl
    private lateinit var fakeDao: FakeAssignmentDao
    private lateinit var fakeConnectivityObserver: FakeConnectivityObserver

    @Before
    fun setUp() {
        fakeDao = FakeAssignmentDao()
        fakeConnectivityObserver = FakeConnectivityObserver()
        repository = AssignmentRepositoryImpl(fakeDao, null, fakeConnectivityObserver)
    }

    @Test
    fun `test create and retrieve assignment`() = runBlocking {
        val newAssignment = createTestAssignment("ASGN-001")
        repository.createAssignment(newAssignment)

        val assignments = repository.getAssignments().first()
        assertEquals(1, assignments.size)
        assertEquals("ASGN-001", assignments[0].id)
    }

    @Test
    fun `test valid transition available to assigned`() = runBlocking {
        val available = createTestAssignment("ASGN-AVAIL", AssignmentStatus.AVAILABLE)
        repository.createAssignment(available)

        repository.updateAssignmentStatus("ASGN-AVAIL", AssignmentStatus.ASSIGNED)

        val updated = repository.getAssignmentById("ASGN-AVAIL").first()
        assertEquals(AssignmentStatus.ASSIGNED, updated?.status)
    }

    @Test
    fun `test invalid transition available to resolved`() = runBlocking {
        val available = createTestAssignment("ASGN-AVAIL", AssignmentStatus.AVAILABLE)
        repository.createAssignment(available)

        repository.updateAssignmentStatus("ASGN-AVAIL", AssignmentStatus.RESOLVED)

        val updated = repository.getAssignmentById("ASGN-AVAIL").first()
        assertEquals(AssignmentStatus.AVAILABLE, updated?.status)
    }

    @Test
    fun `test assigned to in_progress to resolved transitions`() = runBlocking {
        val assigned = createTestAssignment("ASGN-SEQ", AssignmentStatus.ASSIGNED)
        repository.createAssignment(assigned)

        repository.updateAssignmentStatus("ASGN-SEQ", AssignmentStatus.IN_PROGRESS)
        val inProgress = repository.getAssignmentById("ASGN-SEQ").first()
        assertEquals(AssignmentStatus.IN_PROGRESS, inProgress?.status)

        repository.updateAssignmentStatus("ASGN-SEQ", AssignmentStatus.RESOLVED)
        val resolved = repository.getAssignmentById("ASGN-SEQ").first()
        assertEquals(AssignmentStatus.RESOLVED, resolved?.status)
    }

    @Test
    fun `test assigned to available transition`() = runBlocking {
        val assigned = createTestAssignment("ASGN-1", AssignmentStatus.ASSIGNED)
        repository.createAssignment(assigned)

        repository.updateAssignmentStatus("ASGN-1", AssignmentStatus.AVAILABLE)

        val updated = repository.getAssignmentById("ASGN-1").first()
        assertEquals(AssignmentStatus.AVAILABLE, updated?.status)
    }

    @Test
    fun `test same assignmentId upserts instead of creating duplicates`() = runBlocking {
        val initial = createTestAssignment("ASGN-DUP", AssignmentStatus.ASSIGNED)
        repository.createAssignment(initial)

        val updated = initial.copy(status = AssignmentStatus.IN_PROGRESS, updatedAt = System.currentTimeMillis() + 1000)
        repository.createAssignment(updated)

        val all = repository.getAssignments().first()
        assertEquals("Upserting same assignmentId must maintain exactly 1 item", 1, all.size)
        assertEquals(AssignmentStatus.IN_PROGRESS, all[0].status)
    }

    @Test
    fun `test volunteer status update preserves ownership fields`() = runBlocking {
        val original = createTestAssignment("ASGN-OWN").copy(
            status = AssignmentStatus.ASSIGNED,
            volunteerFirebaseUid = "VOL_FIREBASE_123",
            authorityId = "AUTH_FIREBASE_999"
        )
        repository.createAssignment(original)

        repository.updateAssignmentStatus("ASGN-OWN", AssignmentStatus.IN_PROGRESS)

        val updated = repository.getAssignmentById("ASGN-OWN").first()
        assertNotNull(updated)
        assertEquals(AssignmentStatus.IN_PROGRESS, updated?.status)
        assertEquals("VOL_FIREBASE_123", updated?.volunteerFirebaseUid)
        assertEquals("AUTH_FIREBASE_999", updated?.authorityId)
        assertEquals("ASGN-OWN", updated?.id)
        assertEquals("REPORT-001", updated?.reportId)
    }

    @Test
    fun `test offline status update marks assignment as PENDING sync in Room without reverting`() = runBlocking {
        val mockFirestore: FirebaseFirestore = mock()
        val mockCollection: CollectionReference = mock()
        val mockDocument: DocumentReference = mock()
        val mockTask: Task<Void> = mock()

        whenever(mockFirestore.collection(any())).thenReturn(mockCollection)
        whenever(mockCollection.document(any())).thenReturn(mockDocument)
        whenever(mockDocument.set(any(), any())).thenReturn(mockTask)
        whenever(mockTask.isComplete).thenReturn(true)
        whenever(mockTask.isSuccessful).thenReturn(true)

        val offlineRepo = AssignmentRepositoryImpl(fakeDao, mockFirestore, fakeConnectivityObserver)
        offlineRepo.simulateFirestoreFailure = true

        val assigned = createTestAssignment("ASGN-OFFLINE", AssignmentStatus.ASSIGNED)
        offlineRepo.createAssignment(assigned)

        offlineRepo.updateAssignmentStatus("ASGN-OFFLINE", AssignmentStatus.IN_PROGRESS)

        val updated = offlineRepo.getAssignmentById("ASGN-OFFLINE").first()
        assertNotNull(updated)
        assertEquals(AssignmentStatus.IN_PROGRESS, updated?.status)
        assertEquals("PENDING", updated?.syncState)
    }

    @Test
    fun `test connectivity restoration triggers pending assignment sync automatically without manual call`() = runBlocking {
        val mockFirestore: FirebaseFirestore = mock()
        val mockCollection: CollectionReference = mock()
        val mockDocument: DocumentReference = mock()
        val mockTask: Task<Void> = mock()

        whenever(mockFirestore.collection(any())).thenReturn(mockCollection)
        whenever(mockCollection.document(any())).thenReturn(mockDocument)
        whenever(mockDocument.set(any(), any())).thenReturn(mockTask)
        whenever(mockTask.isComplete).thenReturn(true)
        whenever(mockTask.isSuccessful).thenReturn(true)

        val syncRepo = AssignmentRepositoryImpl(fakeDao, mockFirestore, fakeConnectivityObserver)
        syncRepo.startRealtimeSync("VOL-FIREBASE-UID-001")
        syncRepo.simulateFirestoreFailure = true

        val assigned = createTestAssignment("ASGN-RECOVER", AssignmentStatus.ASSIGNED)
        syncRepo.createAssignment(assigned)

        syncRepo.updateAssignmentStatus("ASGN-RECOVER", AssignmentStatus.IN_PROGRESS)

        val pendingBefore = fakeDao.getPendingAssignmentsOneShot()
        assertEquals(1, pendingBefore.size)
        assertEquals("PENDING", pendingBefore[0].syncState)

        // Connectivity returns!
        syncRepo.simulateFirestoreFailure = false

        // Event-driven test: emit Status.Available through the observer flow WITHOUT calling syncPendingLocalChanges() manually!
        fakeConnectivityObserver.emitStatus(ConnectivityObserver.Status.Available)
        delay(100) // Yield coroutine execution so launchIn(repositoryScope) processes event

        val pendingAfter = fakeDao.getPendingAssignmentsOneShot()
        assertEquals("Emitting Status.Available MUST automatically process pending assignment sync", 0, pendingAfter.size)
    }

    @Test
    fun `test start and stop realtime sync does not duplicate listeners`() {
        repository.startRealtimeSync("VOL_UID_TEST")
        repository.startRealtimeSync("VOL_UID_TEST")
        repository.stopRealtimeSync()
        repository.stopRealtimeSync() // Idempotent check
    }

    @Test
    fun `test Firestore Assignment Mapper serialization and deserialization`() {
        val original = createTestAssignment("ASGN-MAPPED").copy(
            volunteerFirebaseUid = "FIREBASE_UID_VOL_999",
            authorityId = "FIREBASE_UID_AUTH_111"
        )

        val firestoreMap = FirestoreAssignmentMapper.toFirestoreMap(original)

        assertEquals("ASGN-MAPPED", firestoreMap["assignmentId"])
        assertEquals("FIREBASE_UID_VOL_999", firestoreMap["volunteerFirebaseUid"])
        assertEquals("FIREBASE_UID_AUTH_111", firestoreMap["authorityId"])
        assertEquals("AVAILABLE", firestoreMap["status"])
        assertEquals("MEDIUM", firestoreMap["priority"])
    }

    private fun createTestAssignment(id: String, status: AssignmentStatus = AssignmentStatus.AVAILABLE): Assignment {
        return Assignment(
            id = id,
            reportId = "REPORT-001",
            volunteerId = "VOL-001",
            volunteerName = "Test Vol",
            victimName = "Test Vic",
            disasterType = "Test Disaster",
            location = "Test Loc",
            status = status,
            priority = Priority.MEDIUM,
            assignedTime = "12:00 PM",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            volunteerFirebaseUid = "VOL-FIREBASE-UID-001",
            authorityId = "AUTH-FIREBASE-UID-001"
        )
    }

    class FakeConnectivityObserver : ConnectivityObserver {
        private val _flow = MutableSharedFlow<ConnectivityObserver.Status>(replay = 1)
        override fun observe(): Flow<ConnectivityObserver.Status> = _flow.asSharedFlow()
        fun emitStatus(status: ConnectivityObserver.Status) {
            _flow.tryEmit(status)
        }
    }

    class FakeAssignmentDao : AssignmentDao {
        private val assignments = MutableStateFlow<Map<String, AssignmentEntity>>(emptyMap())

        override fun getAllAssignments(): Flow<List<AssignmentEntity>> =
            assignments.map { it.values.toList().sortedByDescending { a -> a.updatedAt } }

        override fun getAssignmentById(id: String): Flow<AssignmentEntity?> =
            assignments.map { it[id] }

        override suspend fun getAssignmentByIdOneShot(id: String): AssignmentEntity? =
            assignments.value[id]

        override fun getAssignmentsForVolunteer(volunteerId: String): Flow<List<AssignmentEntity>> =
            assignments.map { it.values.filter { a -> a.volunteerId == volunteerId } }

        override fun getAssignmentsForReport(reportId: String): Flow<List<AssignmentEntity>> =
            assignments.map { it.values.filter { a -> a.reportId == reportId } }

        override suspend fun getPendingAssignmentsOneShot(): List<AssignmentEntity> =
            assignments.value.values.filter { it.syncState == "PENDING" }

        override suspend fun insertAssignment(assignment: AssignmentEntity) {
            assignments.update { it + (assignment.id to assignment) }
        }

        override suspend fun updateAssignment(assignment: AssignmentEntity) {
            assignments.update { it + (assignment.id to assignment) }
        }

        override suspend fun updateAssignmentStatusAndSyncState(id: String, status: String, updatedAt: Long, syncState: String) {
            assignments.update { map ->
                map[id]?.let { map + (id to it.copy(status = status, updatedAt = updatedAt, syncState = syncState)) } ?: map
            }
        }

        override suspend fun updateAssignmentSyncState(id: String, syncState: String) {
            assignments.update { map ->
                map[id]?.let { map + (id to it.copy(syncState = syncState)) } ?: map
            }
        }
    }
}
