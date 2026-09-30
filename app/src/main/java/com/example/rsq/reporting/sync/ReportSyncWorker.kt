package com.example.rsq.reporting.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.rsq.reporting.data.LocalReportRepository
import com.example.rsq.reporting.data.ReportRepository
import com.example.rsq.reporting.data.local.LocalReportDatabase
import com.example.rsq.data.repository.NotificationRepositoryImpl
import com.example.rsq.storage.data.StorageRepository
import com.google.firebase.auth.FirebaseAuth

class ReportSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.i(TAG, "RSQ_SYNC: ReportSyncWorker ENTERED id=$id runAttemptCount=$runAttemptCount")
        Log.d(TAG, "RSQ_SYNC: Firebase user=${FirebaseAuth.getInstance().currentUser?.uid ?: "NONE_OR_ANONYMOUS"}")

        val db = LocalReportDatabase.getDatabase(applicationContext)
        val localRepository = LocalReportRepository(db.reportDao())
        val cloudRepository = ReportRepository()
        val storageRepository = StorageRepository()
        val notificationRepository = NotificationRepositoryImpl(db.notificationDao())

        val syncManager = ReportSyncManager(
            applicationContext,
            localRepository,
            cloudRepository,
            storageRepository,
            notificationRepository
        )

        return try {
            val result = syncManager.syncPendingReports()
            if (result.isSuccess) {
                Log.i(TAG, "RSQ_SYNC: WORKER_RESULT=SUCCESS")
                Result.success()
            } else {
                val exception = result.exceptionOrNull()
                if (syncManager.isPermanentError(exception)) {
                    Log.e(TAG, "RSQ_SYNC: WORKER_RESULT=FAILURE reason=${exception?.message}")
                    Result.failure()
                } else {
                    Log.w(TAG, "RSQ_SYNC: WORKER_RESULT=RETRY reason=${exception?.message}")
                    Result.retry()
                }
            }
        } catch (e: Exception) {
            if (syncManager.isPermanentError(e)) {
                Log.e(TAG, "RSQ_SYNC: WORKER_RESULT=FAILURE unexpected=${e.message}")
                Result.failure()
            } else {
                Log.w(TAG, "RSQ_SYNC: WORKER_RESULT=RETRY unexpected=${e.message}")
                Result.retry()
            }
        }
    }

    companion object {
        private const val TAG = "RSQ_SYNC"
    }
}
