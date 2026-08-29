package com.example.rsq.reporting.sync

import android.content.Context
import android.util.Log
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
        Log.i(TAG, "ReportSyncWorker STARTED")

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
                Log.i(TAG, "ReportSyncWorker SUCCEEDED")
                Result.success()
            } else {
                Log.w(TAG, "ReportSyncWorker RETRYING: ${result.exceptionOrNull()?.message}")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "ReportSyncWorker FAILED with UNEXPECTED ERROR: ${e.message}", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "ReportSyncWorker"
    }
}
