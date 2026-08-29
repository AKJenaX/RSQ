package com.example.rsq.reporting.data

import android.util.Log
import com.example.rsq.reporting.model.Report
import com.example.rsq.reporting.model.ReportStatus
import com.example.rsq.reporting.domain.ReportLifecycle
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

open class ReportRepository(
    private val firestore: FirebaseFirestore? = null
) {
    private val TAG = "ReportRepository"

    // Lazy initialization to avoid calling getInstance() during unit tests
    private val db: FirebaseFirestore by lazy {
        firestore ?: FirebaseFirestore.getInstance()
    }

    open suspend fun submitReport(report: Report): Result<Unit> {
        if (report.id.isBlank()) {
            return Result.failure(IllegalArgumentException("Report ID must not be empty for Firestore submission."))
        }

        Log.i(TAG, "FIRESTORE_REPORT_CREATE_STARTED: reports/${report.id}")
        return try {
            val reportData = hashMapOf(
                "userId" to report.userId,
                "title" to report.title,
                "description" to report.description,
                "severity" to report.severity,
                "status" to report.status.toFirestoreValue(),
                "timestamp" to report.timestamp,
                "latitude" to report.latitude,
                "longitude" to report.longitude,
                "imageUrl" to report.imageUrl,
                "imageUrls" to report.imageUrls,
                "aiScore" to report.aiScore,
                "detectedHazards" to report.detectedHazards,
                "recommendedResources" to report.recommendedResources
            )

            db.collection("reports")
                .document(report.id)
                .set(reportData)
                .await()
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
                    status = ReportStatus.fromString(doc.getString("status") ?: "OPEN")
                )
            }
            Result.success(reports)
        } catch (e: Exception) {
            Result.failure(e)
        }
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
