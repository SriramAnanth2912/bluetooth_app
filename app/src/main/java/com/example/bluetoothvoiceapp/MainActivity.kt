package com.example.bluetoothvoiceapp

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity(), DeviceAdapter.OnDeviceClickListener  {

    companion object {
        private const val RECORD_AUDIO_REQUEST_CODE = 2
        private const val REQUEST_BLUETOOTH_PERMISSION = 1
        private const val  SCAN_DURATION_MS = 10000L
    }

    private lateinit var receiver: BroadcastReceiver
    private lateinit var speechRecognizer: SpeechRecognizerHelper
    private lateinit var listenButton: Button
    private lateinit var textView: TextView
    private lateinit var messagesTextView: TextView
    private lateinit var connectionStatus: TextView
    private lateinit var bluetoothService: BluetoothService
    private lateinit var deviceAdapter: DeviceAdapter
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothEnableLauncher: ActivityResultLauncher<Intent>
    private val deviceList = mutableListOf<BluetoothDevice>()
    private lateinit var recyclerView: RecyclerView

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val scanButton: Button = findViewById(R.id.scanButton)

        // Initialize UI components
        listenButton = findViewById(R.id.listenButton)
        textView = findViewById(R.id.textView)
        connectionStatus = findViewById(R.id.connectionStatus)
        messagesTextView = findViewById(R.id.messagesTextView)

        recyclerView = findViewById(R.id.recyclerView)
        deviceAdapter = DeviceAdapter(deviceList, this)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = deviceAdapter

        // Set up RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = deviceAdapter

        // Initialize Bluetooth
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        checkBluetoothPermissions()

        scanButton.setOnClickListener {
            scanForDevices(bluetoothAdapter)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun checkBluetoothPermissions() {
        val permissionsNeeded = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.RECORD_AUDIO)
        }

        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toTypedArray(), REQUEST_BLUETOOTH_PERMISSION)
        } else {
            // Permissions are already granted
            initializeBluetooth()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun initializeBluetooth() {

        if (!::bluetoothEnableLauncher.isInitialized) {
            // Initialize the launcher here
            bluetoothEnableLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    // Bluetooth has been enabled, proceed with your Bluetooth operations
                    scanForDevices(bluetoothAdapter)
                } else {
                    // Bluetooth was not enabled, show a message to the user
                    Toast.makeText(this, "Bluetooth must be enabled to proceed", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }

        if (!bluetoothAdapter.isEnabled) {
            // Bluetooth is disabled, request enabling it
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            // Launch the Bluetooth enable request
            // Register the activity result launcher
            bluetoothEnableLauncher.launch(enableBtIntent)
        } else {
            // Bluetooth is already enabled
            Toast.makeText(this, "Bluetooth is already enabled", Toast.LENGTH_SHORT).show()
        }

        // Initialize BluetoothService
        bluetoothService = BluetoothService(this)
        bluetoothService.setOnDataReceivedListener(object : BluetoothService.OnDataReceivedListener {
            override fun onDataReceived(data: String) {
                runOnUiThread {
                    messagesTextView.append("Arduino: $data\n")
                    Toast.makeText(this@MainActivity, "Received: $data", Toast.LENGTH_SHORT).show()
                }
            }
        })


        // Initialize SpeechRecognizerHelper
        speechRecognizer = SpeechRecognizerHelper(this, object : SpeechRecognizerHelper.Listener {
            override fun onSpeechResult(command: String) {
                textView.text = command
                bluetoothService.sendData("$command\n")
            }

            override fun onSpeechError(error: String) {
                Toast.makeText(this@MainActivity, "Speech recognition error: $error", Toast.LENGTH_SHORT).show()
            }
        })

        // Set up listen button
        listenButton.setOnClickListener {
            speechRecognizer.startListening()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun scanForDevices(bluetoothAdapter: BluetoothAdapter) {
        clearDevices()
        // Start scanning for devices
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_SCAN),
                REQUEST_BLUETOOTH_PERMISSION
            )
            return
        }
        bluetoothAdapter.startDiscovery()
             "scanning for devices....".also { connectionStatus.text = it }
            Toast.makeText(this@MainActivity, "Scanning for devices...", Toast.LENGTH_SHORT).show()

            // Register a BroadcastReceiver for found devices
            val receiver = object : BroadcastReceiver() {
                @RequiresApi(33)
                override fun onReceive(context: Context?, intent: Intent?) {
                    val action = intent?.action
                    if (BluetoothDevice.ACTION_FOUND == action) {
                        val device: BluetoothDevice? =
                            intent.getParcelableExtra(
                                BluetoothDevice.EXTRA_DEVICE,
                                BluetoothDevice::class.java
                            )

                        if (device != null) {
                            // Check for Bluetooth connection permission
                            if (ActivityCompat.checkSelfPermission(
                                    this@MainActivity, // Use the enclosing Activity's context
                                    Manifest.permission.BLUETOOTH_CONNECT
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                ActivityCompat.requestPermissions(
                                    this@MainActivity, // Correct context here
                                    arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                                    REQUEST_BLUETOOTH_PERMISSION
                                )
                                return
                            }
                            val deviceName = device.name
                            val deviceAddress = device.address
                            println("Found device: $deviceName, Address: $deviceAddress")

                            // Here you can add the device to a list or update your UI
                            if (!deviceList.contains(device)) {
                                deviceList.add(device)
                                deviceAdapter.notifyItemInserted(deviceList.size - 1)

                                // Make RecyclerView visible if it's not already
                                if (recyclerView.visibility == View.GONE) {
                                    recyclerView.visibility = View.VISIBLE
                                }
                            }
                        }
                    }
                }
            }

            val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
            registerReceiver(receiver, filter)

            // Stop discovery after a certain period to save battery
            Handler(Looper.getMainLooper()).postDelayed({
                bluetoothAdapter.cancelDiscovery()
                "Scan complete".also { connectionStatus.text = it }
                "Select a device".also { connectionStatus.text = it }
                unregisterReceiver(receiver) // Unregister the receiver
            }, SCAN_DURATION_MS) // Scanning for 10 seconds
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onDeviceClick(device: BluetoothDevice) {

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this@MainActivity, // Correct context here
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                REQUEST_BLUETOOTH_PERMISSION
            )
            return
        }
        "Connecting to ${device.name}".also { connectionStatus.text = it }

        val isConnected = connectToBluetoothDevice(device) // Call your connect function
        if (isConnected) {
            Toast.makeText(this, "Connected to ${device.name}", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Failed to connect", Toast.LENGTH_SHORT).show()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun connectToBluetoothDevice(device: BluetoothDevice): Boolean {
        var isConnectedBT: Boolean
        try {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) ActivityCompat.requestPermissions(
                this, // Correct context here
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                REQUEST_BLUETOOTH_PERMISSION
            )

            var isConnected  = false
            bluetoothService.connectToDeviceInBackground(device, object :
                BluetoothService.BluetoothConnectionCallback {
                override fun onConnectionResult(result: Boolean) {
                    isConnected = result
                }
            })

            if (isConnected) {
                "Connection is success".also { connectionStatus.text = it }
                connectionStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                isConnectedBT = true
            } else {
                throw Exception("Failed to connect")
            }
        } catch (e: Exception) {
            "Failed to connect to ${device.name}".also { connectionStatus.text = it }
            connectionStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            bluetoothService.closeConnection()
            isConnectedBT = false
        }
        return isConnectedBT
    }

    private fun clearDevices() {
        val size = deviceList.size
        if (size > 0) {
            deviceList.clear()
            deviceAdapter.notifyItemRangeRemoved(0, size)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
        bluetoothService.closeConnection()
        try {
            unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            // Handle the case where receiver is not registered yet
            val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
            registerReceiver(receiver, filter)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            RECORD_AUDIO_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission granted, proceed
                } else {
                    // Permission denied, disable functionality
                    Toast.makeText(
                        this,
                        "Microphone permission is required for voice commands.",
                        Toast.LENGTH_SHORT
                    ).show()
                    listenButton.isEnabled = false
                }
            }
            REQUEST_BLUETOOTH_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    // Permission granted, initialize Bluetooth
                    initializeBluetooth()
                } else {
                    // Permission denied, handle accordingly
                    Toast.makeText(
                        this,
                        "Bluetooth permission denied. Cannot connect to HC-05.",
                        Toast.LENGTH_SHORT
                    ).show()
                    "Permission denied".also { connectionStatus.text = it }
                    connectionStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
                }
            }
        }
    }
}
