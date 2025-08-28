package com.example.chippyble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import android.companion.DeviceFilter
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

class BluetoothViewModel : ViewModel() {

    @Parcelize
    data class DeviceFilter(
        val showOnlyBLE: Boolean = false,
        val showOnlyNearby: Boolean = false,
        val minRssi: Int = -100,
        val showConnectedOnly: Boolean = false,
    ) : Parcelable
    // デバイスリスト
    // DeviceModelのリストを使用
    private val _devices = MutableLiveData<List<DeviceModel>>(emptyList())
    val devices: LiveData<List<DeviceModel>> = _devices

    // connectedDevice is of type BluetoothDevice?
    private var connectedDevice: BluetoothDevice? = null

    // ペアリング済みデバイスを取得するメソッドを追加
    @SuppressLint("MissingPermission")
    fun loadPairedDevices(bluetoothAdapter: BluetoothAdapter?) {
        val pairedDevices = bluetoothAdapter?.bondedDevices ?: return

        val deviceList = mutableListOf<DeviceModel>()
        pairedDevices.forEach { device ->
            deviceList.add(DeviceModel.fromBluetoothDevice(device))
        }

        _devices.value = deviceList
        _toastMessage.value = "Found ${deviceList.size} paired devices"
    }

    // フィルター機能
    fun filterDevices(filter: DeviceFilter) {
        val allDevices = _devices.value ?: emptyList()
        val filteredDevices = allDevices.filter { device ->
            var matches = true

            if (filter.showOnlyBLE) {
                matches = matches && device.deviceType == DeviceType.BLE
            }

            if (filter.showOnlyNearby) {
                matches = matches && device.isNearby()
            }

            if (filter.minRssi > -100) {
                matches = matches && device.rssi >= filter.minRssi
            }

            if (filter.showConnectedOnly) {
                matches = matches && device.isConnected
            }

            matches
        }

        _devices.value = filteredDevices
    }

    // メッセージリスト
    private val _messages = MutableLiveData<List<String>>(emptyList())
    val messages: LiveData<List<String>> = _messages

    // 接続状態 - 初期値を明確に設定
    private val _connectionStatus = MutableLiveData<String>("Disconnected")
    val connectionStatus: LiveData<String> = _connectionStatus

    // スキャン状態
    private val _scanningStatus = MutableLiveData<Boolean>(false)
    val scanningStatus: LiveData<Boolean> = _scanningStatus

    // トーストメッセージ
    private val _toastMessage = MutableLiveData<String>("")
    val toastMessage: LiveData<String> = _toastMessage

    // スキャンメソッドも修正
    fun startScan(bluetoothAdapter: BluetoothAdapter?) {
        _scanningStatus.value = true
        _toastMessage.value = "Scanning for devices..."

        // まずペアリング済みデバイスを表示
        loadPairedDevices(bluetoothAdapter)

        viewModelScope.launch {
            // BLEスキャンのロジック（後で実装）
            delay(5000) // 5秒間スキャン
            _scanningStatus.value = false
            _toastMessage.value = "Scan completed"
        }
    }

    fun stopScan() {
        _scanningStatus.value = false
        showToast("Scan stopped")
    }

    // And connectToDevice would be:
    @SuppressLint("MissingPermission")
    fun connectToDevice(device: DeviceModel) { // 'device' HERE IS DeviceModel
        viewModelScope.launch {
            updateConnectionStatus("Connecting...")
            showToast("Connecting to ${device.name ?: "Unknown Device"}")

            // 接続処理のシミュレーション
            delay(1500)

            connectedDevice = device.bluetoothDevice // This is correct IF 'device' is DeviceModel AND DeviceModel has 'bluetoothDevice'
            updateConnectionStatus("Connected to ${device.name ?: "Unknown Device"}")
            showToast("Connected successfully")
        }
    }

    fun getConnectedDeviceDetail(): BluetoothDevice? { // Renamed for clarity
        // If the error was here previously (e.g., return connectedDevice?.bluetoothDevice)
        // and 'connectedDevice' is ALREADY BluetoothDevice?, then it should be:
        return connectedDevice // Directly return the BluetoothDevice? object
    }

    fun sendMessage(message: String) {
        viewModelScope.launch {
            val currentMessages = _messages.value ?: emptyList()
            _messages.value = currentMessages + "You: $message"
            showToast("Message sent")

            // 受信メッセージのシミュレーション（実際のBLE通信では不要）
            if (connectedDevice != null) {
                delay(500)
                addReceivedMessage("Thank you! 🙏")
            }
        }
    }

    fun disconnect() {
        connectedDevice = null
        updateConnectionStatus("Disconnected")
        showToast("Disconnected")
    }

    fun addReceivedMessage(message: String) {
        viewModelScope.launch {
            val currentMessages = _messages.value ?: emptyList()
            _messages.value = currentMessages + "Received: $message"
        }
    }

    fun updateConnectionStatus(status: String) {
        _connectionStatus.value = status
    }

    fun showToast(message: String) {
        _toastMessage.value = message
    }

    fun clearToastMessage() {
        _toastMessage.value = ""
    }

    fun isConnected(): Boolean {
        return connectedDevice != null
    }

    fun getConnectedDevice(): BluetoothDevice? {
        return connectedDevice
    }
}