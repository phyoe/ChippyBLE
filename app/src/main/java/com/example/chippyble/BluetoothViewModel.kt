package com.example.chippyble

import android.bluetooth.BluetoothDevice
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class BluetoothViewModel : ViewModel() {
    private val _devices = MutableLiveData<List<BluetoothDevice>>()
    val devices: LiveData<List<BluetoothDevice>> = _devices

    private val _messages = MutableLiveData<List<String>>()
    val messages: LiveData<List<String>> = _messages

    private val _connectionStatus = MutableLiveData<String>("Disconnected")
    val connectionStatus: LiveData<String> = _connectionStatus

    private val _scanningStatus = MutableLiveData<Boolean>(false)
    val scanningStatus: LiveData<Boolean> = _scanningStatus

    private val _toastMessage = MutableLiveData<String>()
    val toastMessage: LiveData<String> = _toastMessage

    fun startScan() {
        _scanningStatus.value = true
        _toastMessage.value = "Scanning for devices..."
    }

    fun stopScan() {
        _scanningStatus.value = false
        _toastMessage.value = "Scan stopped"
    }

    fun connectToDevice(device: BluetoothDevice) {
        viewModelScope.launch {
            _connectionStatus.value = "Connecting to ${device.name}..."
            _toastMessage.value = "Connecting to ${device.name}"
            // Simulate connection process
            kotlinx.coroutines.delay(1000)
            _connectionStatus.value = "Connected to ${device.name}"
        }
    }

    fun sendMessage(message: String) {
        viewModelScope.launch {
            val currentMessages = _messages.value ?: emptyList()
            _messages.value = currentMessages + "You: $message"
            _toastMessage.value = "Message sent"
        }
    }

    fun disconnect() {
        _connectionStatus.value = "Disconnected"
        _toastMessage.value = "Disconnected"
    }

    fun addReceivedMessage(message: String) {
        viewModelScope.launch {
            val currentMessages = _messages.value ?: emptyList()
            _messages.value = currentMessages + "Received: $message"
        }
    }
}