package com.example.rsq.mesh.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.rsq.MainActivity
import com.example.rsq.R
import com.example.rsq.mesh.domain.MeshServiceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MeshServiceState {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
    FAILED,
    RECOVERING
}

class MeshForegroundService : Service() {

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): MeshForegroundService = this@MeshForegroundService
    }

    companion object {
        private const val TAG = "RSQ_MESH_SERVICE"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "mesh_service_channel"

        const val ACTION_START = "com.example.rsq.mesh.action.START"
        const val ACTION_STOP = "com.example.rsq.mesh.action.STOP"

        private val _serviceState = MutableStateFlow(MeshServiceState.STOPPED)
        val serviceState: StateFlow<MeshServiceState> = _serviceState.asStateFlow()

        fun startService(context: Context) {
            Log.i(TAG, "RSQ_MESH_SERVICE: START requested")
            val intent = Intent(context, MeshForegroundService::class.java).apply {
                action = ACTION_START
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "RSQ_MESH_SERVICE: Failed to start MeshForegroundService: ${e.message}", e)
                _serviceState.value = MeshServiceState.FAILED
            }
        }

        fun stopService(context: Context) {
            Log.i(TAG, "RSQ_MESH_SERVICE: STOP requested")
            val intent = Intent(context, MeshForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.stopService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "RSQ_MESH_SERVICE: Failed to stop MeshForegroundService: ${e.message}", e)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        _serviceState.value = MeshServiceState.STARTING
        Log.i(TAG, "RSQ_MESH_SERVICE: Service onCreate")
        createNotificationChannel()
        try {
            MeshServiceManager.initialize(applicationContext)
            Log.i(TAG, "RSQ_MESH_SERVICE: Mesh initialization started")
        } catch (e: Exception) {
            Log.e(TAG, "RSQ_MESH_SERVICE: Mesh initialization failed: ${e.message}", e)
            _serviceState.value = MeshServiceState.FAILED
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Log.i(TAG, "RSQ_MESH_SERVICE: Stopping Mesh Foreground Service")
                _serviceState.value = MeshServiceState.STOPPING
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                MeshServiceManager.getTransport()?.stop()
                _serviceState.value = MeshServiceState.STOPPED
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                Log.i(TAG, "RSQ_MESH_SERVICE: Foreground service starting")
                val notification = buildNotification("RSQ Mesh active in background")
                startForeground(NOTIFICATION_ID, notification)
                Log.i(TAG, "RSQ_MESH_SERVICE: Foreground service started")

                try {
                    val transport = MeshServiceManager.getTransport()
                    if (transport != null) {
                        transport.start()
                        Log.i(TAG, "RSQ_MESH_TRANSPORT: Mesh transport READY")
                    } else {
                        MeshServiceManager.initialize(applicationContext)
                        MeshServiceManager.getTransport()?.start()
                        Log.i(TAG, "RSQ_MESH_TRANSPORT: Mesh transport initialized and READY")
                    }

                    if (MeshServiceManager.getEngine() != null) {
                        Log.i(TAG, "RSQ_MESH_RELAY: Relay engine READY")
                    }

                    _serviceState.value = MeshServiceState.RUNNING
                    Log.i(TAG, "RSQ_MESH_SERVICE: Mesh RUNNING")
                } catch (e: Exception) {
                    Log.e(TAG, "RSQ_MESH_SERVICE: Error starting mesh transport: ${e.message}", e)
                    _serviceState.value = MeshServiceState.FAILED
                }
                return START_STICKY
            }
        }
    }

    override fun onDestroy() {
        Log.i(TAG, "RSQ_MESH_SERVICE: Service onDestroy - cleaning up resources")
        _serviceState.value = MeshServiceState.STOPPING
        try {
            MeshServiceManager.getTransport()?.stop()
            Log.i(TAG, "RSQ_MESH_TRANSPORT: Mesh transport stopped cleanly")
        } catch (e: Exception) {
            Log.e(TAG, "RSQ_MESH_SERVICE: Error stopping transport in onDestroy: ${e.message}")
        }
        _serviceState.value = MeshServiceState.STOPPED
        Log.i(TAG, "RSQ_MESH_SERVICE: Mesh STOPPED")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "RSQ Offline Mesh Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps RSQ offline emergency mesh communication active in the background."
            }
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("RSQ Emergency Mesh Active")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
