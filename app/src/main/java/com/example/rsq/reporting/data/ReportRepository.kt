package com.example.rsq.reporting.data

import com.example.rsq.reporting.model.Report
import com.google.firebase.firestore.FirebaseFirestore
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
}
