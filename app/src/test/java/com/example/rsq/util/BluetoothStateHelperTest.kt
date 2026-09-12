package com.example.rsq.util

import android.bluetooth.BluetoothAdapter
import org.junit.Assert.assertEquals
import org.junit.Test

class BluetoothStateHelperTest {

    @Test
    fun `ENABLE_BLUETOOTH_ACTION constant matches BluetoothAdapter ACTION_REQUEST_ENABLE`() {
        assertEquals("android.bluetooth.adapter.action.REQUEST_ENABLE", BluetoothAdapter.ACTION_REQUEST_ENABLE)
        assertEquals(BluetoothAdapter.ACTION_REQUEST_ENABLE, BluetoothStateHelper.ENABLE_BLUETOOTH_ACTION)
    }
}
