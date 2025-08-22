package com.example.chippyble

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.UUID

class BluetoothService : Service() {
    private val binder = LocalBinder()
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var gattServer: BluetoothGattServer? = null
    private var connectedDevice: BluetoothDevice? = null
    private var connectedGatt: BluetoothGatt? = null

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            super.onConnectionStateChange(device, status, newState)
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connectedDevice = device
                // Notify UI about connection
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connectedDevice = null
                // Notify UI about disconnection
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value)

            val message = String(value)
            // Notify UI about received message
        }
    }

    private val gattClientCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connectedGatt = null
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                connectedGatt = gatt
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val message = String(characteristic.value)
            // Notify UI about received message
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): BluetoothService = this@BluetoothService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        initializeBluetooth()
        createNotificationChannel()
        startForeground(1, createNotification())
    }

    private fun initializeBluetooth() {
        val bluetoothManager = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()

        gattServer = bluetoothManager?.openGattServer(this, gattServerCallback)?.apply {
            addService(createChippyService())
        }
    }

    private fun createChippyService(): BluetoothGattService {
        // Convert the String UUIDs to UUID objects
        val serviceUuid = UUID.fromString(MainActivity.CHIPPY_SERVICE_UUID)
        val characteristicUuid = UUID.fromString(MainActivity.CHIPPY_CHARACTERISTIC_UUID)

        val service = BluetoothGattService(
            serviceUuid, // Pass the UUID object
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )

        val messageCharacteristic = BluetoothGattCharacteristic(
            characteristicUuid, // Pass the UUID object
            BluetoothGattCharacteristic.PROPERTY_READ or
                    BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ or
                    BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        service.addCharacteristic(messageCharacteristic)
        return service
    }

    fun sendMessage(message: String) {
        connectedDevice?.let { device ->
            val characteristic = gattServer?.getService(MainActivity.CHIPPY_SERVICE_UUID)
                ?.getCharacteristic(MainActivity.CHIPPY_CHARACTERISTIC_UUID)

            characteristic?.value = message.toByteArray()
            gattServer?.notifyCharacteristicChanged(device, characteristic, false)
        }
    }

    fun connectToDevice(device: BluetoothDevice) {
        device.connectGatt(this, false, gattClientCallback)
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "chippy_channel",
                "Chippy Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "chippy_channel")
            .setContentTitle("Chippy Bluetooth Service")
            .setContentText("Running in background")
            .setSmallIcon(R.drawable.ic_notification)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        gattServer?.close()
        connectedGatt?.close()
    }
}