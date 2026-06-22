package com.example.rsq.reporting.data

import com.example.rsq.reporting.model.Report
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class ReportRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    suspend fun submitReport(report: Report): Result<Unit> {
        return try {
            firestore.collection("reports")
                .add(report)
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
            val reports = snapshot.toObjects(Report::class.java)
            Result.success(reports)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
