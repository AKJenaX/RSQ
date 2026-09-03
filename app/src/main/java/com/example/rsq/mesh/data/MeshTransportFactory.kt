package com.example.rsq.mesh.data

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.rsq.BuildConfig
import com.example.rsq.mesh.domain.MeshTransport
import com.example.rsq.mesh.domain.NodeIdentityProvider

object MeshTransportFactory {

    private const val TAG = "MeshTransportFactory"

    private fun isEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk" == Build.PRODUCT)
    }

    fun createTransport(
        context: Context,
        identityProvider: NodeIdentityProvider
    ): MeshTransport {
        return if (BuildConfig.DEBUG && isEmulator()) {
            Log.i(TAG, "DEBUG build running on Android Emulator. Selecting MockMeshTransport for testing.")
            MockMeshTransport(identityProvider)
        } else {
            Log.i(TAG, "Selecting production NearbyMeshTransport (Google Nearby Connections).")
            NearbyMeshTransport(context.applicationContext, identityProvider)
        }
    }
}
