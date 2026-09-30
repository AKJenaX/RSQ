package com.example.rsq.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.rsq.mesh.domain.MeshServiceManager
import com.example.rsq.mesh.service.MeshForegroundService
import com.example.rsq.reporting.sync.SyncScheduler

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "RSQ_MESH_SERVICE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i(TAG, "RSQ_MESH_SERVICE: BootReceiver triggered with action: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED) {

            try {
                // Initialize background mesh engine
                MeshServiceManager.initialize(context.applicationContext)

                // Start Mesh Foreground Service safely where Android OS permits
                MeshForegroundService.startService(context)

                // Schedule background synchronization for pending offline reports
                SyncScheduler.scheduleSync(context.applicationContext)

                Log.i(TAG, "RSQ_MESH_SERVICE: BootReceiver recovery initialization complete")
            } catch (e: Exception) {
                Log.e(TAG, "RSQ_MESH_SERVICE: Error in BootReceiver recovery: ${e.message}", e)
            }
        }
    }
}
