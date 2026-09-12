package com.example.rsq.util

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.util.Log

object BluetoothStateHelper {

    private const val TAG = "BluetoothStateHelper"

    const val ENABLE_BLUETOOTH_ACTION = BluetoothAdapter.ACTION_REQUEST_ENABLE

    fun isBluetoothEnabled(context: Context): Boolean {
        return try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            @Suppress("DEPRECATION")
            val adapter = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
            val enabled = adapter?.isEnabled == true
            Log.d(TAG, "isBluetoothEnabled check: $enabled")
            enabled
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Bluetooth state: ${e.message}", e)
            false
        }
    }

    fun createEnableBluetoothIntent(): Intent {
        return Intent(ENABLE_BLUETOOTH_ACTION)
    }
}
