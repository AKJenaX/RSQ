package com.example.rsq.reporting.data

import com.example.rsq.reporting.model.Report
import com.example.rsq.reporting.model.ReportStatus
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class ReportRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    suspend fun submitReport(report: Report): Result<Unit> {
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
                "imageUrl" to report.imageUrl
            )
            firestore.collection("reports")
                .add(reportData)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getReports(userId: String): Result<List<Report>> {
        return try {
            val snapshot = firestore.collection("reports")
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
}
