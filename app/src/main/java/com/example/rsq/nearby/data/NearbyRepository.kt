package com.example.rsq.nearby.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.rsq.mesh.domain.MeshTransport
import com.example.rsq.mesh.model.MeshTransportStatus
import com.example.rsq.nearby.model.NearbyReadiness
import com.example.rsq.nearby.model.NearbyState
import com.example.rsq.util.BluetoothStateHelper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*

class NearbyRepository(
    private val context: Context,
    private val meshTransport: MeshTransport
) {
    private val TAG = "NearbyRepository"
    private val _nearbyState = MutableStateFlow(NearbyState())
    val nearbyState = _nearbyState.asStateFlow()

    fun isBluetoothEnabled(): Boolean {
        return BluetoothStateHelper.isBluetoothEnabled(context)
    }

    fun hasNearbyPermissions(): Boolean {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        // Fine location is also required for Nearby
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)

        return permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun observeNearbyReadiness(): Flow<NearbyState> = callbackFlow {
        Log.d(TAG, "NEARBY_ACQUISITION_STARTED")

        val diagnosticsJob = meshTransport.observeDiagnostics()
            .onEach { diagnostics ->
                val hasPermission = hasNearbyPermissions()
                val isEnabled = isBluetoothEnabled()

                val readiness = when {
                    !hasPermission -> NearbyReadiness.PERMISSION_DENIED
                    !isEnabled -> NearbyReadiness.SERVICES_DISABLED
                    diagnostics.status == MeshTransportStatus.ERROR -> NearbyReadiness.ERROR
                    diagnostics.status == MeshTransportStatus.READY || 
                    diagnostics.status == MeshTransportStatus.ADVERTISING || 
                    diagnostics.status == MeshTransportStatus.DISCOVERING -> NearbyReadiness.READY
                    diagnostics.status == MeshTransportStatus.STARTING -> NearbyReadiness.DETECTING
                    else -> NearbyReadiness.NOT_DETERMINED
                }

                val newState = NearbyState(
                    readiness = readiness,
                    isDetecting = diagnostics.isAdvertising || diagnostics.isDiscovering,
                    connectedPeers = diagnostics.connectedPeerCount,
                    error = diagnostics.lastError
                )
                _nearbyState.value = newState
                trySend(newState)
            }.launchIn(this)

        // Initial check and trigger start if possible
        if (hasNearbyPermissions() && isBluetoothEnabled()) {
             meshTransport.start()
        } else {
            val hasPermission = hasNearbyPermissions()
            val isEnabled = isBluetoothEnabled()
            val readiness = when {
                !hasPermission -> NearbyReadiness.PERMISSION_DENIED
                !isEnabled -> NearbyReadiness.SERVICES_DISABLED
                else -> NearbyReadiness.NOT_DETERMINED
            }
            val initialState = NearbyState(readiness = readiness)
            _nearbyState.value = initialState
            trySend(initialState)
        }

        awaitClose {
            Log.d(TAG, "NEARBY_ACQUISITION_STOPPED")
            diagnosticsJob.cancel()
            meshTransport.stop()
        }
    }

    fun startDetection() {
        Log.d(TAG, "NEARBY_DETECTION_START_REQUESTED")
        if (hasNearbyPermissions() && isBluetoothEnabled()) {
            meshTransport.start()
        }
    }

    fun stopDetection() {
        Log.d(TAG, "NEARBY_DETECTION_STOP_REQUESTED")
        meshTransport.stop()
    }
}
