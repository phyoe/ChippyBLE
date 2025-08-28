package com.example.chippyble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.companion.DeviceFilter
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.os.Parcelable
import android.util.Log
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

    // "Paired" / "Bonded" devices
    // "Visible" / "Discovered" devices
    private var scanCallback: ScanCallback? = null
    private var scanHandler: Handler? = null
    private var isCurrentlyScanning = false // Renamed from isScanning to avoid conflict with LiveData
    private val SCAN_PERIOD: Long = 10000 // 10 seconds scan

    // Using a map to store all discovered devices (paired or scanned) to easily update/add them by address
    private val allDiscoveredDevices = mutableMapOf<String, DeviceModel>()

    @SuppressLint("MissingPermission")
    fun refreshVisibleDevicesList(bluetoothAdapter: BluetoothAdapter?) {
        if (bluetoothAdapter == null) {
            _toastMessage.value = "Bluetoothアダプターが利用できません"
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            _toastMessage.value = "Bluetoothが有効になっていません"
            // Optionally, you could trigger a request to enable Bluetooth here
            return
        }

        if (isCurrentlyScanning) {
            _toastMessage.value = "スキャンは既に実行中です"
            return
        }

        _scanningStatus.value = true
        isCurrentlyScanning = true
        _toastMessage.value = "デバイスを検索中..."

        // 1. Clear previous non-paired scan results and load paired devices immediately
        allDiscoveredDevices.clear() // Clear all to rebuild the list
        val pairedBtDevices = bluetoothAdapter.bondedDevices ?: emptySet()

        Log.d("ChippyBLE_VM", "Found ${pairedBtDevices.size} paired devices.")
        pairedBtDevices.forEach { btDevice ->
            val deviceModel = DeviceModel.fromBluetoothDevice(btDevice) // Ensure this populates isPaired and bluetoothDevice
            allDiscoveredDevices[btDevice.address] = deviceModel
        }
        updateDeviceListLiveData() // Update UI with paired devices first

        // 2. Start a new scan for other visible devices
        val scanner = bluetoothAdapter.bluetoothLeScanner
        if (scanner == null) {
            _toastMessage.value = "Bluetooth LEスキャナーが利用できません"
            stopDeviceScanInternally() // Clean up scanning state
            return
        }

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                super.onScanResult(callbackType, result)
                addScanResultToDiscoveredList(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                super.onBatchScanResults(results)
                results.forEach { result -> addScanResultToDiscoveredList(result) }
            }

            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                Log.e("ChippyBLE_VM", "スキャン失敗: $errorCode")
                _toastMessage.value = "スキャン失敗: $errorCode"
                stopDeviceScanInternally()
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            // Permissions should be checked before calling this function in Activity/Fragment
            scanner.startScan(null, settings, scanCallback)
            Log.d("ChippyBLE_VM", "Bluetooth LEスキャン開始")

            scanHandler?.removeCallbacksAndMessages(null) // Remove previous handler if any
            scanHandler = Handler(Looper.getMainLooper())
            scanHandler?.postDelayed({
                if (isCurrentlyScanning) {
                    Log.d("ChippyBLE_VM", "スキャン時間終了")
                    stopDeviceScanInternally()
                    _toastMessage.value = "スキャン完了。合計${allDiscoveredDevices.size}台のデバイスが見つかりました。"
                }
            }, SCAN_PERIOD)

        } catch (e: SecurityException) {
            Log.e("ChippyBLE_VM", "スキャン開始時の権限エラー: ${e.message}")
            _toastMessage.value = "Bluetooth権限が必要です"
            stopDeviceScanInternally()
        } catch (e: Exception) {
            Log.e("ChippyBLE_VM", "スキャン開始時のエラー: ${e.message}")
            _toastMessage.value = "スキャンエラー: ${e.message}"
            stopDeviceScanInternally()
        }
    }

    @SuppressLint("MissingPermission")
    private fun addScanResultToDiscoveredList(result: ScanResult) {
        val btDevice = result.device
        // Create DeviceModel, ensuring 'bluetoothDevice' and 'isPaired' are correctly set
        // The 'isPaired' status for a newly scanned device that wasn't previously bonded will be false.
        // If it's a paired device being re-scanned, its existing entry in allDiscoveredDevices
        // (added from bondedDevices) would already have isPaired = true.
        // The DeviceModel.fromBluetoothDevice should correctly determine 'isPaired'.
        // If a device is already in allDiscoveredDevices, this will update its RSSI and other scan-related info.
        val deviceModel = DeviceModel.fromBluetoothDevice(btDevice, result.rssi)

        allDiscoveredDevices[btDevice.address] = deviceModel
        updateDeviceListLiveData()
        Log.d("ChippyBLE_VM", "スキャン結果追加/更新: ${deviceModel.name}, RSSI: ${result.rssi}")
    }

    /**
     * Stops the ongoing Bluetooth LE scan and updates scanning status.
     * This is an internal function to centralize scan stopping logic.
     */
    @SuppressLint("MissingPermission")
    fun stopActiveScan(bluetoothAdapter: BluetoothAdapter?) {
        if (isCurrentlyScanning) {
            Log.d("ChippyBLE_VM", "手動でスキャンを停止します。")
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
            stopDeviceScanInternally()
            _toastMessage.value = "スキャンが停止されました。"
        }
    }

    private fun stopDeviceScanInternally() {
        scanHandler?.removeCallbacksAndMessages(null)
        // scanHandler = null // No need to nullify handler, can be reused
        // scanCallback = null // Scanner will be stopped, callback can remain for next scan

        _scanningStatus.value = false
        isCurrentlyScanning = false
        Log.d("ChippyBLE_VM", "内部スキャン停止処理完了。")
    }


    /**
     * Updates the LiveData list that the UI observes.
     * Sorts devices, e.g., by RSSI or paired status.
     */
    private fun updateDeviceListLiveData() {
        // Example sorting: Paired devices first, then by RSSI descending
        val sortedList = allDiscoveredDevices.values.toList().sortedWith(
            compareByDescending<DeviceModel> { it.isPaired }
                .thenByDescending { it.rssi }
        )
        _devices.value = sortedList
    }


    // Make sure your DeviceModel.fromBluetoothDevice handles this correctly
    // And your DeviceModel includes 'isPaired' and 'bluetoothDevice'
    // (As per previous discussions, DeviceModel.kt should be correctly set up)

    // ... (rest of your ViewModel: connectToDevice, disconnect, sendMessage, etc.)
    // ... (Make sure DeviceModel and DeviceType are defined correctly)

    override fun onCleared() {
        super.onCleared()
        // Ensure scan is stopped if ViewModel is cleared, passing a null adapter
        // might not be ideal here, consider how to get the adapter if needed
        // or just ensure internal state is reset.
        stopDeviceScanInternally() // Simpler cleanup
        Log.d("ChippyBLE_VM", "ViewModel onCleared, スキャン停止処理。")
    }

    // ペアリング済みデバイスを取得するメソッドを追加
    @SuppressLint("MissingPermission")
    fun loadPairedDevices(bluetoothAdapter: BluetoothAdapter?) {
        val pairedDevices = bluetoothAdapter?.bondedDevices ?: return

        val deviceList = mutableListOf<DeviceModel>()
        pairedDevices.forEach { device ->
            deviceList.add(DeviceModel.fromBluetoothDevice(device))
        }

        _devices.value = deviceList
        //_toastMessage.value = "Found ${deviceList.size} paired devices"
        _toastMessage.value = "${deviceList.size}台のペアリング済みデバイスが見つかりました"
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
        //_toastMessage.value = "Scanning for devices..."
        _toastMessage.value = "デバイスをスキャン中..."

        // まずペアリング済みデバイスを表示
        // loadPairedDevices(bluetoothAdapter)
        // 使用可能なデバイス
        refreshVisibleDevicesList(bluetoothAdapter)

        viewModelScope.launch {
            // BLEスキャンのロジック（後で実装）
            delay(5000) // 5秒間スキャン
            _scanningStatus.value = false
            //_toastMessage.value = "Scan completed"
            _toastMessage.value = "スキャン完了"
        }
    }

    fun stopScan() {
        _scanningStatus.value = false
        //showToast("Scan stopped")
        showToast("スキャン停止")
    }

    // And connectToDevice would be:
    @SuppressLint("MissingPermission")
    fun connectToDevice(device: DeviceModel) { // 'device' HERE IS DeviceModel
        viewModelScope.launch {
            updateConnectionStatus("接続中...")
            //showToast("Connecting to ${device.name ?: "Unknown Device"}")
            showToast("${device.name ?: "不明なデバイス"}に接続しました")

            // 接続処理のシミュレーション
            delay(1500)

            connectedDevice = device.bluetoothDevice // This is correct IF 'device' is DeviceModel AND DeviceModel has 'bluetoothDevice'
            //updateConnectionStatus("Connected to ${device.name ?: "Unknown Device"}")
            updateConnectionStatus("${device.name ?: "不明なデバイス"}に接続しました")
            //showToast("Connected successfully")
            showToast("接続に成功しました")
        }
    }

    fun sendMessage(message: String) {
        viewModelScope.launch {
            val currentMessages = _messages.value ?: emptyList()
            _messages.value = currentMessages + "送信: $message"
            showToast("メッセージを送信しました")

            // 受信メッセージのシミュレーション（実際のBLE通信では不要）
            if (connectedDevice != null) {
                delay(500)
                addReceivedMessage("ありがとうございました！🙏")
            }
        }
    }

    fun disconnect() {
        connectedDevice = null
        updateConnectionStatus("Disconnected")
        //showToast("Disconnected")
        showToast("切断されています")
    }

    fun addReceivedMessage(message: String) {
        viewModelScope.launch {
            val currentMessages = _messages.value ?: emptyList()
            _messages.value = currentMessages + "受信: $message"
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

//    override fun onCleared() {
//        super.onCleared()
//        stopScan()
//    }
}