package com.example.rsq.data.repository

import android.util.Log
import com.example.rsq.data.local.AssignmentDao
import com.example.rsq.data.local.AssignmentEntity
import com.example.rsq.data.model.Assignment
import com.example.rsq.data.model.AssignmentStatus
import com.example.rsq.data.model.Priority
import com.example.rsq.util.ConnectivityObserver
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.tasks.await

class AssignmentRepositoryImpl(
    private val assignmentDao: AssignmentDao,
    private val firestore: FirebaseFirestore? = null,
    private val connectivityObserver: ConnectivityObserver? = null,
    var simulateFirestoreFailure: Boolean = false
) : AssignmentRepository {

    private val TAG = "AssignmentRepository"

    private val db: FirebaseFirestore by lazy {
        firestore ?: FirebaseFirestore.getInstance()
    }

    private var volunteerListenerRegistration: ListenerRegistration? = null
    private var connectivityJob: Job? = null
    private val syncMutex = Mutex()
    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    override fun getAssignments(): Flow<List<Assignment>> =
        assignmentDao.getAllAssignments().map { list ->
            list.map { it.toDomain() }
        }

    override fun getAssignmentById(id: String): Flow<Assignment?> =
        assignmentDao.getAssignmentById(id).map { it?.toDomain() }

    override fun getAssignmentsForVolunteer(
        volunteerId: String
    ): Flow<List<Assignment>> =
        assignmentDao.getAssignmentsForVolunteer(volunteerId).map { list ->
            list.map { it.toDomain() }
        }

    override fun getAssignmentsForReport(
        reportId: String
    ): Flow<List<Assignment>> =
        assignmentDao.getAssignmentsForReport(reportId).map { list ->
            list.map { it.toDomain() }
        }

    override suspend fun createAssignment(assignment: Assignment) {
        val pendingAssignment = assignment.copy(syncState = "PENDING")
        // 1. Room local write (Immediate Offline-First Source of Truth)
        assignmentDao.insertAssignment(AssignmentEntity.fromDomain(pendingAssignment))
        logI("LOCAL_ASSIGNMENT_CREATED: ${assignment.id}")

        // 2. Firestore Sync (Cross-Device)
        syncAssignmentToFirestore(pendingAssignment)
    }

    override suspend fun updateAssignmentStatus(
        id: String,
        status: AssignmentStatus
    ) {
        val currentEntity = assignmentDao.getAssignmentByIdOneShot(id)
        if (currentEntity != null) {
            val fromStatus = AssignmentStatus.valueOf(currentEntity.status)
            if (isValidTransition(fromStatus, status)) {
                val updatedTime = System.currentTimeMillis()
                // 1. Room local write (Immediate Offline-First Source of Truth)
                assignmentDao.updateAssignmentStatusAndSyncState(id, status.name, updatedTime, "PENDING")
                logI("LOCAL_ASSIGNMENT_STATUS_UPDATED: id=$id, from=$fromStatus, to=$status")

                // 2. Firestore Sync (Cross-Device)
                val updatedDomain = currentEntity.toDomain().copy(
                    status = status,
                    updatedAt = updatedTime,
                    syncState = "PENDING"
                )
                syncAssignmentToFirestore(updatedDomain)
            } else {
                logW("INVALID_STATUS_TRANSITION_REJECTED: id=$id, from=$fromStatus, to=$status")
            }
        }
    }

    override suspend fun assignVolunteer(
        reportId: String,
        volunteerId: String,
        volunteerName: String,
        volunteerFirebaseUid: String?,
        authorityId: String?
    ) {
        val existingAssignments = assignmentDao.getAssignmentsForReport(reportId).first()
        val existing = existingAssignments.firstOrNull()

        val updatedTime = System.currentTimeMillis()
        val assignmentToSave = if (existing != null) {
            existing.toDomain().copy(
                volunteerId = volunteerId,
                volunteerName = volunteerName,
                volunteerFirebaseUid = volunteerFirebaseUid ?: volunteerId,
                authorityId = authorityId ?: existing.authorityId,
                status = AssignmentStatus.ASSIGNED,
                updatedAt = updatedTime,
                syncState = "PENDING"
            )
        } else {
            Assignment(
                id = "ASGN-$reportId",
                reportId = reportId,
                volunteerId = volunteerId,
                volunteerName = volunteerName,
                victimName = "Victim",
                disasterType = "SOS Alert",
                location = "Unknown",
                status = AssignmentStatus.ASSIGNED,
                priority = Priority.HIGH,
                assignedTime = "Just now",
                createdAt = updatedTime,
                updatedAt = updatedTime,
                volunteerFirebaseUid = volunteerFirebaseUid ?: volunteerId,
                authorityId = authorityId,
                syncState = "PENDING"
            )
        }

        createAssignment(assignmentToSave)
    }

    override fun startRealtimeSync(firebaseUid: String) {
        if (firebaseUid.isBlank()) return

        stopRealtimeSync()

        logI("STARTING_REALTIME_FIRESTORE_ASSIGNMENT_SYNC: volunteerFirebaseUid=$firebaseUid")

        // 1. Observe Network Connectivity for automatic recovery
        connectivityJob = connectivityObserver?.observe()
            ?.onEach { status ->
                if (status == ConnectivityObserver.Status.Available) {
                    logI("CONNECTIVITY_RESTORED: Triggering automatic pending assignment sync")
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
            volunteerListenerRegistration = db.collection("assignments")
                .whereEqualTo("volunteerFirebaseUid", firebaseUid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        logW("FIRESTORE_ASSIGNMENT_SYNC_ERROR: ${error.message}")
                        return@addSnapshotListener
                    }

                    if (snapshot != null) {
                        repositoryScope.launch {
                            snapshot.documents.forEach { doc ->
                                val remoteAssignment = FirestoreAssignmentMapper.fromDocument(doc)
                                if (remoteAssignment != null) {
                                    upsertRemoteAssignmentLocally(remoteAssignment)
                                }
                            }
                        }
                    }
                }
        } catch (t: Throwable) {
            logW("FIRESTORE_LISTENER_ATTACH_FAILED: ${t.message}")
        }
    }

    override fun stopRealtimeSync() {
        volunteerListenerRegistration?.remove()
        volunteerListenerRegistration = null
        connectivityJob?.cancel()
        connectivityJob = null
        logI("STOPPED_REALTIME_FIRESTORE_ASSIGNMENT_SYNC")
    }

    suspend fun syncPendingLocalChanges() {
        if (!syncMutex.tryLock()) {
            logI("SYNC_PENDING_LOCAL_ASSIGNMENTS_SKIPPED: Sync already in progress")
            return
        }
        try {
            val pendingList = assignmentDao.getPendingAssignmentsOneShot()
            if (pendingList.isNotEmpty()) {
                logI("SYNC_PENDING_LOCAL_ASSIGNMENTS_STARTED: count=${pendingList.size}")
                pendingList.forEach { entity ->
                    syncAssignmentToFirestore(entity.toDomain())
                }
            }
        } catch (e: Exception) {
            logW("SYNC_PENDING_LOCAL_ASSIGNMENTS_FAILED: ${e.message}")
        } finally {
            syncMutex.unlock()
        }
    }

    private suspend fun syncAssignmentToFirestore(assignment: Assignment) {
        if (simulateFirestoreFailure || firestore == null) {
            logW("FIRESTORE_ASSIGNMENT_SYNC_SKIPPED_OR_OFFLINE: assignments/${assignment.id}")
            return
        }

        try {
            val firestoreData = FirestoreAssignmentMapper.toFirestoreMap(assignment)
            db.collection("assignments")
                .document(assignment.id)
                .set(firestoreData, SetOptions.merge())
                .await()
            assignmentDao.updateAssignmentSyncState(assignment.id, "SYNCED")
            logI("FIRESTORE_ASSIGNMENT_SYNC_SUCCESS: assignments/${assignment.id}, status=${assignment.status}")
        } catch (e: Exception) {
            logW("FIRESTORE_ASSIGNMENT_SYNC_OFFLINE_OR_FAILED: assignments/${assignment.id}, error=${e.message}")
            // Catching cleanly preserves offline functionality and prevents crashes
        }
    }

    private suspend fun upsertRemoteAssignmentLocally(remote: Assignment) {
        val local = assignmentDao.getAssignmentByIdOneShot(remote.id)
        if (local == null) {
            // New cross-device assignment received!
            assignmentDao.insertAssignment(AssignmentEntity.fromDomain(remote.copy(syncState = "SYNCED")))
            logI("CROSS_DEVICE_ASSIGNMENT_RECEIVED: id=${remote.id}, status=${remote.status}")
        } else {
            // Conflict check: if local is PENDING and local updatedAt > remote updatedAt, keep local and push to Firestore!
            if (local.syncState == "PENDING" && local.updatedAt > remote.updatedAt) {
                logI("RETAINING_NEWER_LOCAL_ASSIGNMENT_STATE: id=${local.id}, localStatus=${local.status}, remoteStatus=${remote.status}")
                syncAssignmentToFirestore(local.toDomain())
            } else if (remote.updatedAt >= local.updatedAt) {
                val updatedEntity = AssignmentEntity.fromDomain(remote.copy(syncState = "SYNCED"))
                assignmentDao.insertAssignment(updatedEntity)
                logI("LOCAL_ASSIGNMENT_UPDATED_FROM_FIRESTORE: id=${remote.id}, status=${remote.status}")
            }
        }
    }

    private fun isValidTransition(
        from: AssignmentStatus,
        to: AssignmentStatus
    ): Boolean {
        return when (from) {
            AssignmentStatus.AVAILABLE ->
                to == AssignmentStatus.ASSIGNED

            AssignmentStatus.ASSIGNED ->
                to == AssignmentStatus.IN_PROGRESS ||
                    to == AssignmentStatus.AVAILABLE

            AssignmentStatus.IN_PROGRESS ->
                to == AssignmentStatus.RESOLVED

            AssignmentStatus.RESOLVED ->
                false
        }
    }

    private fun logI(msg: String) {
        try { Log.i(TAG, msg) } catch (t: Throwable) { println("$TAG: $msg") }
    }

    private fun logW(msg: String) {
        try { Log.w(TAG, msg) } catch (t: Throwable) { println("$TAG: $msg") }
    }
}
