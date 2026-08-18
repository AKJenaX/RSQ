package com.example.rsq.reporting.sync

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object SyncScheduler {
    private const val UNIQUE_WORK_NAME = "REPORT_SYNC_WORK"

    // For Unit Testing to prevent IllegalStateException
    var isTestMode = false

    fun scheduleSync(context: Context) {
        if (isTestMode) return

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<ReportSyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                10000L, // 10 seconds
                TimeUnit.MILLISECONDS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE, // Ensure subsequent reports trigger another sync without killing active one
            syncRequest
        )
    }
}
