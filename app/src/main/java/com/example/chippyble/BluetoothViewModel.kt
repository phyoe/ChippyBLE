package com.example.chippyble

import android.bluetooth.BluetoothDevice
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BluetoothViewModel : ViewModel() {
    // デバイスリスト
    private val _devices = MutableLiveData<List<BluetoothDevice>>(emptyList())
    val devices: LiveData<List<BluetoothDevice>> = _devices

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

    // 接続中のデバイス
    private var connectedDevice: BluetoothDevice? = null

    fun startScan() {
        _scanningStatus.value = true
        _toastMessage.value = "Scanning for devices..."
    }

    fun stopScan() {
        _scanningStatus.value = false
        showToast("Scan stopped")
    }

    fun connectToDevice(device: BluetoothDevice) {
        viewModelScope.launch {
            updateConnectionStatus("Connecting...")
            showToast("Connecting to ${device.name ?: "Unknown Device"}")

            // 接続処理のシミュレーション
            delay(1500)

            connectedDevice = device
            updateConnectionStatus("Connected to ${device.name ?: "Unknown Device"}")
            showToast("Connected successfully")
        }
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