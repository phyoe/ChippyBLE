package com.example.chippyble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.chippyble.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: BluetoothViewModel by viewModels()
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var deviceAdapter: DeviceListAdapter  // ここで宣言
    private lateinit var messageAdapter: MessageAdapter    // ここで宣言

    companion object {
        const val CHIPPY_SERVICE_UUID = "0000FF00-0000-1000-8000-00805F9B34FB"
        const val CHIPPY_CHARACTERISTIC_UUID = "0000FF01-0000-1000-8000-00805F9B34FB"
        const val REQUEST_ENABLE_BT = 1
    }

    @SuppressLint("MissingPermission")
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            initializeBluetooth()
        } else {
            Toast.makeText(this, "Permissions denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // アダプターを初期化
        initializeAdapters()

        checkPermissions()
        setupUI()
        setupObservers()
    }

    // アダプター初期化メソッドを追加
    private fun initializeAdapters() {
        deviceAdapter = DeviceListAdapter { device ->
            viewModel.connectToDevice(device)
        }

        messageAdapter = MessageAdapter()
    }

    private fun checkPermissions() {
        val requiredPermissions = mutableListOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (requiredPermissions.all {
                ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            }) {
            initializeBluetooth()
        } else {
            requestPermissionLauncher.launch(requiredPermissions.toTypedArray())
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun initializeBluetooth() {
        val bluetoothManager = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        } else {
            startBluetoothService()
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupUI() {
        // RecyclerViewの設定
        binding.devicesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = deviceAdapter  // 初期化済みのadapterを使用
        }

        binding.messagesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = messageAdapter  // 初期化済みのadapterを使用
        }

        binding.scanButton.setOnClickListener {
            viewModel.startScan(bluetoothAdapter)
        }

        binding.stopScanButton.setOnClickListener {
            viewModel.stopScan()
        }

        binding.sendButton.setOnClickListener {
            val message = binding.messageEditText.text.toString().trim()
            if (message.isNotEmpty()) {
                viewModel.sendMessage(message)
                binding.messageEditText.text.clear()
            }
        }

        binding.makeDiscoverableButton.setOnClickListener {
            makeDiscoverable()
        }

        // デバッグボタン
        binding.debugButton.setOnClickListener {
            debugBluetoothInfo()
            viewModel.loadPairedDevices(bluetoothAdapter)
        }
    }

    private fun setupObservers() {
        viewModel.devices.observe(this) { devices ->
            deviceAdapter.submitList(devices)  // 正しく参照できる
            Log.d("DEBUG", "Devices updated: ${devices.size}")
        }

        viewModel.messages.observe(this) { messages ->
            messageAdapter.submitList(messages)  // 正しく参照できる
            Log.d("DEBUG", "Messages updated: ${messages.size}")
        }

        viewModel.connectionStatus.observe(this) { status ->
            //binding.connectionStatusText.text = "Status: $status"
            if (status.equals("Disconnected")) { // Added a closing parenthesis here
                binding.connectionStatusText.text = "ステータス: 切断"
            } else {
                binding.connectionStatusText.text = "ステータス： $status"
            }
            Log.d("DEBUG", "Connection status: $status")
        }

        viewModel.scanningStatus.observe(this) { scanning ->
            binding.scanButton.isEnabled = !scanning
            binding.stopScanButton.isEnabled = scanning

            // Add visual feedback to make it obvious
            if (scanning) {
                binding.scanButton.alpha = 0.6f
                binding.stopScanButton.alpha = 1.0f
            } else {
                binding.scanButton.alpha = 1.0f
                binding.stopScanButton.alpha = 0.6f
            }

            Log.d("DEBUG", "Scanning: $scanning")
            Log.d("DEBUG", "scanButton enabled: ${binding.scanButton.isEnabled}")
            Log.d("DEBUG", "stopScanButton enabled: ${binding.stopScanButton.isEnabled}")
        }

        viewModel.toastMessage.observe(this) { message ->
            if (message.isNotEmpty()) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                viewModel.clearToastMessage()
            }
        }
    }

    private fun startBluetoothService() {
        val serviceIntent = Intent(this, BluetoothService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)

        // サービス開始後にペアリング済みデバイスを読み込み
        viewModel.loadPairedDevices(bluetoothAdapter)
    }

    private fun makeDiscoverable() {
        val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
        }
        startActivity(discoverableIntent)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun debugBluetoothInfo() {
        Log.d("DEBUG", "=== Bluetooth Debug Info ===")
        Log.d("DEBUG", "Bluetooth enabled: ${bluetoothAdapter.isEnabled}")

        val pairedDevices = bluetoothAdapter.bondedDevices
        Log.d("DEBUG", "Paired devices count: ${pairedDevices.size}")

        pairedDevices.forEachIndexed { index, device ->
            Log.d("DEBUG", "Device $index: ${device.name} - ${device.address}")
        }

        Log.d("DEBUG", "Device adapter items: ${deviceAdapter.itemCount}")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BT && resultCode == RESULT_OK) {
            startBluetoothService()
        }
    }

    override fun onResume() {
        super.onResume()
        // 画面再表示時にデバイスリストを更新
        if (::bluetoothAdapter.isInitialized && bluetoothAdapter.isEnabled) {
            viewModel.loadPairedDevices(bluetoothAdapter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.stopScan()
        viewModel.disconnect()
    }
}