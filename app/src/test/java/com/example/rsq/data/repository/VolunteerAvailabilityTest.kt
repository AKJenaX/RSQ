package com.example.rsq.data.repository

import com.example.rsq.auth.model.User
import com.example.rsq.data.local.VolunteerDao
import com.example.rsq.data.local.VolunteerEntity
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

class VolunteerAvailabilityTest {

    private lateinit var fakeDao: FakeVolunteerDao
    private lateinit var fakeAssignmentRepo: AssignmentRepository
    private lateinit var repository: VolunteerRepositoryImpl

    @Before
    fun setUp() {
        fakeDao = FakeVolunteerDao()
        fakeAssignmentRepo = AssignmentRepositoryTest.FakeAssignmentDao().let { AssignmentRepositoryImpl(it) }
        repository = VolunteerRepositoryImpl(fakeDao, fakeAssignmentRepo, null) // Pass null for Firestore to test Room logic
    }

    @Test
    fun `newly created volunteer profile defaults to unavailable`() = runBlocking {
        repository.createVolunteerProfile("UID-999", "Test Volunteer")

        val volunteer = repository.getVolunteerData("UID-999").first()
        assertNotNull(volunteer)
        assertEquals(false, volunteer?.isAvailable)
    }

    @Test
    fun `updating availability updates local Room immediately`() = runBlocking {
        repository.createVolunteerProfile("UID-888", "Active Volunteer")
        
        // Initial state
        var volunteer = repository.getVolunteerData("UID-888").first()
        assertEquals(false, volunteer?.isAvailable)

        // Toggle ON
        repository.updateAvailability("UID-888", true)
        volunteer = repository.getVolunteerData("UID-888").first()
        assertEquals(true, volunteer?.isAvailable)

        // Toggle OFF
        repository.updateAvailability("UID-888", false)
        volunteer = repository.getVolunteerData("UID-888").first()
        assertEquals(false, volunteer?.isAvailable)
    }

    @Test
    fun `getAvailableVolunteers returns only available volunteers`() = runBlocking {
        repository.createVolunteerProfile("UID-A", "Alice")
        repository.createVolunteerProfile("UID-B", "Bob")
        repository.createVolunteerProfile("UID-C", "Charlie")

        // Alice is available
        repository.updateAvailability("UID-A", true)
        // Bob is available
        repository.updateAvailability("UID-B", true)
        // Charlie is offline (false)

        val available = repository.getAvailableVolunteers().first()

        assertEquals(2, available.size)
        assertTrue(available.any { it.name == "Alice" })
        assertTrue(available.any { it.name == "Bob" })
        assertFalse(available.any { it.name == "Charlie" })
    }

    @Test
    fun `test connectivity restoration triggers pending volunteer availability sync automatically without manual call`() = runBlocking {
        val mockFirestore: FirebaseFirestore = mock()
        val mockCollection: CollectionReference = mock()
        val mockDocument: DocumentReference = mock()
        val mockTask: Task<Void> = mock()

        whenever(mockFirestore.collection(any())).thenReturn(mockCollection)
        whenever(mockCollection.document(any())).thenReturn(mockDocument)
        whenever(mockDocument.update(any<String>(), any())).thenReturn(mockTask)
        whenever(mockTask.isComplete).thenReturn(true)
        whenever(mockTask.isSuccessful).thenReturn(true)

        val fakeObserver = AssignmentRepositoryTest.FakeConnectivityObserver()
        val connRepo = VolunteerRepositoryImpl(fakeDao, fakeAssignmentRepo, mockFirestore, fakeObserver, simulateFirestoreFailure = true)

        connRepo.startRealtimeVolunteerSync()

        connRepo.createVolunteerProfile("UID-CONN-RECOVER", "Recovery Responder")
        connRepo.updateAvailability("UID-CONN-RECOVER", true)

        val pendingBefore = fakeDao.getPendingVolunteersOneShot()
        assertEquals(1, pendingBefore.size)
        assertEquals("PENDING", pendingBefore[0].syncState)

        // Connectivity returns!
        connRepo.simulateFirestoreFailure = false

        // Event-driven test: emit Status.Available through the observer flow WITHOUT calling syncPendingLocalChanges() manually!
        fakeObserver.emitStatus(ConnectivityObserver.Status.Available)
        delay(100) // Yield coroutine execution so launchIn(repositoryScope) processes event

        val pendingAfter = fakeDao.getPendingVolunteersOneShot()
        assertEquals("Emitting Status.Available MUST automatically process pending volunteer sync", 0, pendingAfter.size)
    }

    @Test
    fun `User model preserves role and authorization fields while allowing isAvailable modification`() {
        val user = User(
            firebaseUid = "UID-ROLE-TEST",
            name = "Jane Responder",
            email = "jane@rsq.org",
            role = "VOLUNTEER",
            isAuthorized = false,
            isAvailable = false
        )

        assertEquals("VOLUNTEER", user.role)
        assertFalse(user.isAuthorized)
        assertFalse(user.isAvailable)

        val updatedAvailability = user.copy(isAvailable = true)
        assertEquals("VOLUNTEER", updatedAvailability.role)
        assertFalse(updatedAvailability.isAuthorized)
        assertTrue(updatedAvailability.isAvailable)
    }

    class FakeVolunteerDao : VolunteerDao {
        private val volunteers = MutableStateFlow<Map<String, VolunteerEntity>>(emptyMap())

        override fun getVolunteerById(id: String): Flow<VolunteerEntity?> =
            volunteers.map { it.values.firstOrNull { v -> v.id == id } }

        override fun getVolunteerByFirebaseUid(uid: String): Flow<VolunteerEntity?> =
            volunteers.map { it.values.firstOrNull { v -> v.firebaseUid == uid } }

        override suspend fun getVolunteerByFirebaseUidOneShot(uid: String): VolunteerEntity? =
            volunteers.value.values.firstOrNull { it.firebaseUid == uid }

        override fun getAllVolunteers(): Flow<List<VolunteerEntity>> =
            volunteers.map { it.values.toList() }

        override fun getAvailableVolunteers(): Flow<List<VolunteerEntity>> =
            volunteers.map { it.values.filter { v -> v.isAvailable } }

        override suspend fun getPendingVolunteersOneShot(): List<VolunteerEntity> =
            volunteers.value.values.filter { it.syncState == "PENDING" }

        override suspend fun insertVolunteer(volunteer: VolunteerEntity) {
            volunteers.update { it + (volunteer.id to volunteer) }
        }

        override suspend fun updateAvailabilityAndSyncState(uid: String, isAvailable: Boolean, syncState: String) {
            volunteers.update { map ->
                val entity = map.values.firstOrNull { it.firebaseUid == uid }
                if (entity != null) {
                    map + (entity.id to entity.copy(isAvailable = isAvailable, syncState = syncState))
                } else {
                    map
                }
            }
        }

        override suspend fun updateVolunteerSyncState(uid: String, syncState: String) {
            volunteers.update { map ->
                val entity = map.values.firstOrNull { it.firebaseUid == uid }
                if (entity != null) {
                    map + (entity.id to entity.copy(syncState = syncState))
                } else {
                    map
                }
            }
        }
    }
}
