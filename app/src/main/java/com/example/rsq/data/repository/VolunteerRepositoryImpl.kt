package com.example.rsq.data.repository

import android.util.Log
import com.example.rsq.data.local.VolunteerDao
import com.example.rsq.data.local.VolunteerEntity
import com.example.rsq.data.model.Volunteer
import com.example.rsq.data.model.Assignment
import com.example.rsq.util.ConnectivityObserver
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.tasks.await

class VolunteerRepositoryImpl(
    private val volunteerDao: VolunteerDao,
    private val assignmentRepository: AssignmentRepository,
    private val firestore: FirebaseFirestore? = null,
    private val connectivityObserver: ConnectivityObserver? = null,
    var simulateFirestoreFailure: Boolean = false
) : VolunteerRepository {

    private val TAG = "VolunteerRepository"
    private val db: FirebaseFirestore by lazy { firestore ?: FirebaseFirestore.getInstance() }

    private var volunteerSyncRegistration: ListenerRegistration? = null
    private var connectivityJob: Job? = null
    private val syncMutex = Mutex()
    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    override fun getVolunteerData(firebaseUid: String): Flow<Volunteer?> =
        volunteerDao.getVolunteerByFirebaseUid(firebaseUid)
            .map { it?.toDomain() }

    override fun getAssignments(volunteerId: String): Flow<List<Assignment>> =
        assignmentRepository.getAssignmentsForVolunteer(volunteerId)

    override fun getAllVolunteers(): Flow<List<Volunteer>> =
        volunteerDao.getAllVolunteers()
            .onStart { seedInitialVolunteers() }
            .map { list ->
                list.map { it.toDomain() }
            }

    override fun getAvailableVolunteers(): Flow<List<Volunteer>> =
        volunteerDao.getAvailableVolunteers()
            .map { list ->
                list.map { it.toDomain() }
            }

    override suspend fun createVolunteerProfile(firebaseUid: String, name: String) {
        val existing = volunteerDao.getVolunteerByFirebaseUidOneShot(firebaseUid)
        if (existing == null) {
            val newVolunteer = Volunteer(
                id = "VOL-${firebaseUid.take(6).uppercase()}",
                name = name,
                totalAssignments = 0,
                pendingAssignments = 0,
                activeAssignments = 0,
                completedAssignments = 0,
                firebaseUid = firebaseUid,
                isAvailable = false, // Explicitly default new volunteers to unavailable
                syncState = "PENDING"
            )
            volunteerDao.insertVolunteer(VolunteerEntity.fromDomain(newVolunteer, firebaseUid))
            syncPendingLocalChanges()
        }
    }

    override suspend fun updateAvailability(firebaseUid: String, isAvailable: Boolean) {
        // 1. Update local Room database immediately (Offline-first)
        volunteerDao.updateAvailabilityAndSyncState(firebaseUid, isAvailable, "PENDING")
        logI("LOCAL_VOLUNTEER_AVAILABILITY_UPDATED: uid=$firebaseUid, isAvailable=$isAvailable")

        if (simulateFirestoreFailure || firestore == null) {
            logW("FIRESTORE_VOLUNTEER_AVAILABILITY_SYNC_SKIPPED_OR_OFFLINE: uid=$firebaseUid")
            return
        }

        // 2. Sync to Firestore asynchronously
        try {
            db.collection("users").document(firebaseUid)
                .update("isAvailable", isAvailable)
                .await()
            volunteerDao.updateVolunteerSyncState(firebaseUid, "SYNCED")
            logI("FIRESTORE_VOLUNTEER_AVAILABILITY_SYNCED: uid=$firebaseUid, isAvailable=$isAvailable")
        } catch (e: Exception) {
            logW("FIRESTORE_VOLUNTEER_AVAILABILITY_SYNC_FAILED: uid=$firebaseUid, isAvailable=$isAvailable. Kept local state. Error: ${e.message}")
        }
    }

    override fun startRealtimeVolunteerSync() {
        stopRealtimeVolunteerSync()

        logI("STARTING_REALTIME_FIRESTORE_VOLUNTEER_SYNC")

        // 1. Observe Network Connectivity for automatic recovery
        connectivityJob = connectivityObserver?.observe()
            ?.onEach { status ->
                if (status == ConnectivityObserver.Status.Available) {
                    logI("CONNECTIVITY_RESTORED: Triggering automatic pending volunteer availability sync")
                    syncPendingLocalChanges()
                }
            }
            ?.launchIn(repositoryScope)

        // 2. Initial sync for pending local changes
        repositoryScope.launch {
            syncPendingLocalChanges()
        }

        // 3. Attach Firestore snapshot listener
        try {
            volunteerSyncRegistration = db.collection("users")
                .whereEqualTo("role", "VOLUNTEER")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        logW("FIRESTORE_VOLUNTEER_SYNC_ERROR: ${error.message}")
                        return@addSnapshotListener
                    }

                    if (snapshot != null) {
                        repositoryScope.launch {
                            snapshot.documents.forEach { doc ->
                                val uid = doc.getString("firebaseUid") ?: doc.id
                                val name = doc.getString("name") ?: "Responder"
                                val isAvailable = doc.getBoolean("isAvailable") ?: false

                                val existing = volunteerDao.getVolunteerByFirebaseUidOneShot(uid)
                                val volId = existing?.id ?: "VOL-${uid.take(6).uppercase()}"

                                // Conflict check: if local has pending availability update, retain local and push to Firestore!
                                val effectiveAvailability = if (existing?.syncState == "PENDING") {
                                    existing.isAvailable
                                } else {
                                    isAvailable
                                }

                                val updatedEntity = VolunteerEntity(
                                    id = volId,
                                    firebaseUid = uid,
                                    name = name,
                                    totalAssignments = existing?.totalAssignments ?: 0,
                                    pendingAssignments = existing?.pendingAssignments ?: 0,
                                    activeAssignments = existing?.activeAssignments ?: 0,
                                    completedAssignments = existing?.completedAssignments ?: 0,
                                    isAvailable = effectiveAvailability,
                                    syncState = if (existing?.syncState == "PENDING") "PENDING" else "SYNCED"
                                )

                                volunteerDao.insertVolunteer(updatedEntity)
                                logI("VOLUNTEER_PROFILE_SYNCED_FROM_FIRESTORE: uid=$uid, name=$name, isAvailable=$effectiveAvailability")
                            }
                        }
                    }
                }
        } catch (t: Throwable) {
            logW("FIRESTORE_VOLUNTEER_LISTENER_ATTACH_FAILED: ${t.message}")
        }
    }

    override fun stopRealtimeVolunteerSync() {
        volunteerSyncRegistration?.remove()
        volunteerSyncRegistration = null
        connectivityJob?.cancel()
        connectivityJob = null
        logI("STOPPED_REALTIME_FIRESTORE_VOLUNTEER_SYNC")
    }

    suspend fun syncPendingLocalChanges() {
        if (!syncMutex.tryLock()) {
            logI("SYNC_PENDING_LOCAL_VOLUNTEERS_SKIPPED: Sync already in progress")
            return
        }
        try {
            val pendingList = volunteerDao.getPendingVolunteersOneShot()
            if (pendingList.isNotEmpty()) {
                logI("SYNC_PENDING_LOCAL_VOLUNTEERS_STARTED: count=${pendingList.size}")
                pendingList.forEach { entity ->
                    entity.firebaseUid?.let { uid ->
                        updateAvailability(uid, entity.isAvailable)
                    }
                }
            }
        } catch (e: Exception) {
            logW("SYNC_PENDING_LOCAL_VOLUNTEERS_FAILED: ${e.message}")
        } finally {
            syncMutex.unlock()
        }
    }

    private suspend fun seedInitialVolunteers() {
        val current = volunteerDao.getAllVolunteers().first()
        if (current.isEmpty()) {
            val initialList = listOf(
                Volunteer("VOL-DEMO-01", "Sarah Wilson", 12, 1, 0, 11, isAvailable = false, syncState = "SYNCED"),
                Volunteer("VOL-DEMO-02", "Mike Johnson", 25, 2, 1, 22, isAvailable = false, syncState = "SYNCED"),
                Volunteer("VOL-DEMO-03", "Elena Rodriguez", 5, 0, 0, 5, isAvailable = false, syncState = "SYNCED")
            )
            initialList.forEach {
                volunteerDao.insertVolunteer(VolunteerEntity.fromDomain(it, null))
            }
        }
    }

    private fun logI(msg: String) {
        try { Log.i(TAG, msg) } catch (t: Throwable) { println("$TAG: $msg") }
    }

    private fun logW(msg: String) {
        try { Log.w(TAG, msg) } catch (t: Throwable) { println("$TAG: $msg") }
    }
}
