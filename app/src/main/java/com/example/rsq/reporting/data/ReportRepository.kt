package com.example.rsq.reporting.data

import android.util.Log
import com.example.rsq.reporting.model.Report
import com.example.rsq.reporting.model.ReportStatus
import com.example.rsq.reporting.domain.ReportLifecycle
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

open class ReportRepository(
    private val firestore: FirebaseFirestore? = null
) {
    private val TAG = "ReportRepository"
    private val SYNC_TAG = "RSQ_SYNC"
    private val DIAG_LOG = "RSQ_DIAGNOSTIC"

    private val db: FirebaseFirestore by lazy {
        firestore ?: FirebaseFirestore.getInstance()
    }

    open suspend fun submitReport(report: Report): Result<Unit> {
        if (report.id.isBlank()) {
            return Result.failure(IllegalArgumentException("Report ID must not be empty for Firestore submission."))
        }

        Log.i(TAG, "FIRESTORE_REPORT_CREATE_STARTED: reports/${report.id}")
        return try {
            val docRef = db.collection("reports").document(report.id)
            val existingDoc = docRef.get().await()
            val exists = existingDoc.exists()

            val currentAuthUid = try { FirebaseAuth.getInstance().currentUser?.uid ?: "" } catch (t: Throwable) { "" }
            val effectiveOriginUid = report.effectiveOriginUserId
            val isRelayedReport = report.receivedViaRelay || (effectiveOriginUid.isNotBlank() && currentAuthUid.isNotBlank() && effectiveOriginUid != currentAuthUid)

            val reportData = mutableMapOf<String, Any?>(
                "userId" to effectiveOriginUid,
                "title" to report.title,
                "description" to report.description,
                "severity" to report.severity,
                "latitude" to report.latitude,
                "longitude" to report.longitude,
                "aiScore" to report.aiScore,
                "detectedHazards" to report.detectedHazards,
                "recommendedResources" to report.recommendedResources,
                "expirationTimestamp" to report.expirationTimestamp
            )

            if (report.userName.isNotBlank()) {
                reportData["userName"] = report.userName
            }

            if (isRelayedReport) {
                reportData["originUserId"] = effectiveOriginUid
                reportData["originUserName"] = report.effectiveOriginUserName
                reportData["originCreatedAt"] = if (report.originCreatedAt > 0) report.originCreatedAt else report.timestamp
                reportData["relayUserId"] = if (report.relayUserId.isNotBlank()) report.relayUserId else currentAuthUid
                reportData["relayDeviceId"] = report.relayDeviceId
                reportData["receivedViaRelay"] = true
            }

            if (!exists) {
                reportData["status"] = report.status.toFirestoreValue()
                reportData["timestamp"] = report.timestamp
            }

            if (report.imageUrls.isNotEmpty()) {
                reportData["imageUrl"] = report.imageUrl
                reportData["imageUrls"] = report.imageUrls
            } else if (report.imageUrl != null) {
                reportData["imageUrl"] = report.imageUrl
            }

            Log.i(SYNC_TAG, "FIRESTORE_WRITE_PAYLOAD_DIAGNOSTIC: " +
                "collection=reports, " +
                "documentId=${report.id}, " +
                "authenticatedUid=$currentAuthUid, " +
                "userId=${reportData["userId"]}, " +
                "originUserId=${reportData["originUserId"]}, " +
                "relayUserId=${reportData["relayUserId"]}, " +
                "receivedViaRelay=${reportData["receivedViaRelay"]} (${reportData["receivedViaRelay"]?.javaClass?.simpleName})"
            )

            Log.i(DIAG_LOG, "BOUNDARY_9_BEFORE_DOCREF_SET: " +
                "reportId=${report.id}, " +
                "originNodeId=${reportData["relayDeviceId"]}, " +
                "originUserId=${reportData["originUserId"]}, " +
                "relayDeviceId=${reportData["relayDeviceId"]}, " +
                "relayUserId=${reportData["relayUserId"]}, " +
                "receivedViaRelay=${reportData["receivedViaRelay"]}"
            )

            docRef.set(reportData, SetOptions.merge()).await()
            Log.i(TAG, "FIRESTORE_REPORT_CREATE_SUCCESS: reports/${report.id}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "FIRESTORE_REPORT_CREATE_FAILED for reports/${report.id}: ${e.message}", e)
            Result.failure(e)
        }
    }

    open suspend fun getReports(userId: String): Result<List<Report>> {
        return try {
            val snapshot = db.collection("reports")
                .whereEqualTo("userId", userId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .await()

            val reports = snapshot.documents.mapNotNull { doc ->
                val report = doc.toObject(Report::class.java)
                report?.copy(
                    id = doc.id,
                    status = ReportStatus.fromString(doc.getString("status") ?: "OPEN"),
                    expirationTimestamp = doc.getLong("expirationTimestamp") ?: (report.timestamp + 24 * 60 * 60 * 1000L)
                )
            }
            Result.success(reports)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open fun observeReports(userId: String): Flow<List<Report>> = callbackFlow {
        if (firestore == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = db.collection("reports")
            .whereEqualTo("userId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val reports = snapshot.documents.mapNotNull { doc ->
                        val report = doc.toObject(Report::class.java)
                        report?.copy(
                            id = doc.id,
                            status = ReportStatus.fromString(doc.getString("status") ?: "OPEN"),
                            expirationTimestamp = doc.getLong("expirationTimestamp") ?: (report.timestamp + 24 * 60 * 60 * 1000L)
                        )
                    }
                    trySend(reports)
                }
            }

        awaitClose { listener.remove() }
    }

    open fun observeAllActiveReports(): Flow<List<Report>> = callbackFlow {
        if (firestore == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = db.collection("reports")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val reports = snapshot.documents.mapNotNull { doc ->
                        val report = doc.toObject(Report::class.java)
                        report?.copy(
                            id = doc.id,
                            status = ReportStatus.fromString(doc.getString("status") ?: "OPEN"),
                            expirationTimestamp = doc.getLong("expirationTimestamp") ?: (report.timestamp + 24 * 60 * 60 * 1000L)
                        )
                    }
                    trySend(reports)
                }
            }

        awaitClose { listener.remove() }
    }

    open suspend fun updateReportStatus(
        reportId: String,
        currentStatus: ReportStatus,
        newStatus: ReportStatus
    ): Result<Unit> {
        if (!ReportLifecycle.canTransition(currentStatus, newStatus)) {
            return Result.failure(IllegalArgumentException("Invalid status transition: $currentStatus -> $newStatus"))
        }

        return try {
            db.collection("reports")
                .document(reportId)
                .update("status", newStatus.toFirestoreValue())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
