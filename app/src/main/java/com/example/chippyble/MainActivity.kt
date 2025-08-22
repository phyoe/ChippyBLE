package com.example.chippyble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.chippyble.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: BluetoothViewModel by viewModels()
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var deviceAdapter: DeviceListAdapter
    private lateinit var messageAdapter: MessageAdapter

    companion object {
        const val CHIPPY_SERVICE_UUID = "0000FF00-0000-1000-8000-00805F9B34FB"
        const val CHIPPY_CHARACTERISTIC_UUID = "0000FF01-0000-1000-8000-00805F9B34FB"
        const val REQUEST_ENABLE_BT = 1
    }

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

        checkPermissions()
        setupUI()
        setupObservers()
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

    private fun setupUI() {
        deviceAdapter = DeviceListAdapter { device ->
            viewModel.connectToDevice(device)
        }

        messageAdapter = MessageAdapter()

        binding.devicesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = deviceAdapter
        }

        binding.messagesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = messageAdapter
        }

        binding.scanButton.setOnClickListener {
            viewModel.startScan()
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
    }

    private fun setupObservers() {
        viewModel.devices.observe(this) { devices ->
            deviceAdapter.submitList(devices)
        }

        viewModel.messages.observe(this) { messages ->
            messageAdapter.submitList(messages)
        }

        viewModel.connectionStatus.observe(this) { status ->
            binding.connectionStatusText.text = status
        }

        viewModel.scanningStatus.observe(this) { scanning ->
            binding.scanButton.isEnabled = !scanning
            binding.stopScanButton.isEnabled = scanning
        }

        viewModel.toastMessage.observe(this) { message ->
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun startBluetoothService() {
        val serviceIntent = Intent(this, BluetoothService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun makeDiscoverable() {
        val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
        }
        startActivity(discoverableIntent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BT && resultCode == RESULT_OK) {
            startBluetoothService()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.stopScan()
        viewModel.disconnect()
    }
}