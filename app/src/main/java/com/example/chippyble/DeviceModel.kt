package com.example.chippyble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice // Make sure this import is for android.bluetooth.BluetoothDevice
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize // If you use Parcelize
data class DeviceModel(
    // ... other properties like name, address, rssi, isConnected, deviceType ...
    val name: String?,
    val address: String,
    val isPaired: Boolean,
    var rssi: Int,
    var isConnected: Boolean = false,
    val deviceType: DeviceType = DeviceType.UNKNOWN,

    val bluetoothDevice: BluetoothDevice? // <<< THIS LINE IS CRITICAL
) : Parcelable { // Implement Parcelable if needed

    companion object {
        @SuppressLint("MissingPermission") // Ensure you check permissions before calling this
        fun fromBluetoothDevice(btDevice: BluetoothDevice, rssiValue: Int = -100): DeviceModel {
            return DeviceModel(
                name = btDevice.name ?: "Unknown",
                address = btDevice.address,
                // Determine the paired state from the BluetoothDevice
                isPaired = btDevice.bondState == BluetoothDevice.BOND_BONDED, // <<< ADD THIS LINE
                rssi = rssiValue,
                deviceType = when (btDevice.type) {
                    BluetoothDevice.DEVICE_TYPE_LE -> DeviceType.BLE
                    BluetoothDevice.DEVICE_TYPE_CLASSIC -> DeviceType.CLASSIC
                    BluetoothDevice.DEVICE_TYPE_DUAL -> DeviceType.DUAL
                    else -> DeviceType.UNKNOWN
                },
                bluetoothDevice = btDevice // <<< Ensure you are assigning the passed btDevice here
            )
        }
    }

    fun isNearby(minRssiThreshold: Int = -70): Boolean {
        return rssi >= minRssiThreshold
    }
}

// Ensure DeviceType enum is defined
enum class DeviceType {
    BLE, CLASSIC, DUAL, UNKNOWN
}
