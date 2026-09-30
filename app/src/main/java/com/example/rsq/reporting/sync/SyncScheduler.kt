package com.example.rsq.reporting.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

object SyncScheduler {
    private const val TAG = "RSQ_SYNC"
    private const val UNIQUE_WORK_NAME = "REPORT_SYNC_WORK"

    // For Unit Testing to prevent IllegalStateException
    var isTestMode = false

    fun scheduleSync(context: Context) {
        if (isTestMode) return

        try {
            Log.i(TAG, "RSQ_SYNC: SCHEDULE_REQUEST")

            // Diagnostic connectivity check
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNetwork = cm?.activeNetwork
            val caps = if (activeNetwork != null) cm.getNetworkCapabilities(activeNetwork) else null
            val hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            Log.d(TAG, "RSQ_SYNC: NETWORK_DIAGNOSTIC activeNetwork=$activeNetwork hasInternet=$hasInternet")

            val workManager = WorkManager.getInstance(context)
            val workInfos = try {
                workManager.getWorkInfosForUniqueWork(UNIQUE_WORK_NAME).get()
            } catch (e: Exception) {
                emptyList()
            }

            val activeWork = workInfos.find { 
                it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED 
            }

            if (activeWork != null) {
                Log.i(TAG, "RSQ_SYNC: EXISTING_WORK_KEPT uniqueWork=$UNIQUE_WORK_NAME id=${activeWork.id} state=${activeWork.state} runAttemptCount=${activeWork.runAttemptCount}")
            } else {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val syncRequest = OneTimeWorkRequestBuilder<ReportSyncWorker>()
                    .setConstraints(constraints)
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        10000L, // 10 seconds initial delay
                        TimeUnit.MILLISECONDS
                    )
                    .build()

                workManager.enqueueUniqueWork(
                    UNIQUE_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    syncRequest
                )
                Log.i(TAG, "RSQ_SYNC: NEW_WORK_ENQUEUED uniqueWork=$UNIQUE_WORK_NAME id=${syncRequest.id}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "RSQ_SYNC: SyncScheduler WorkManager error: ${e.message}")
        }
    }
}
