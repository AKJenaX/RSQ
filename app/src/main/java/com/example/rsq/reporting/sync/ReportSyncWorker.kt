package com.example.rsq.reporting.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.rsq.reporting.data.LocalReportRepository
import com.example.rsq.reporting.data.ReportRepository
import com.example.rsq.reporting.data.local.LocalReportDatabase
import com.example.rsq.storage.data.StorageRepository

class ReportSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val db = LocalReportDatabase.getDatabase(applicationContext)
        val localRepository = LocalReportRepository(db.reportDao())
        val cloudRepository = ReportRepository()
        val storageRepository = StorageRepository()
        
        val syncManager = ReportSyncManager(
            applicationContext,
            localRepository,
            cloudRepository,
            storageRepository
        )

        return try {
            val result = syncManager.syncPendingReports()
            if (result.isSuccess) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
