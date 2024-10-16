package com.example.bluetoothvoiceapp

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.app.ActivityCompat
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

class BluetoothService(private val context: Context) {

    companion object {
        private val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // Standard SPP UUID for HC-05
        private const val REQUEST_BLUETOOTH_PERMISSION = 1
    }
    private var bluetoothSocket: BluetoothSocket? = null

    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private var isConnected = false

    interface OnDataReceivedListener {
        fun onDataReceived(data: String)
    }

    private var listener: OnDataReceivedListener? = null

    fun setOnDataReceivedListener(listener: OnDataReceivedListener) {
        this.listener = listener
    }

    fun connectToDeviceInBackground(device: BluetoothDevice, callback: BluetoothConnectionCallback) {
        connectToDevice(device, callback)
    }

    interface BluetoothConnectionCallback {
        fun onConnectionResult(result: Boolean)
    }

    private fun connectToDevice(device: BluetoothDevice, callback: BluetoothConnectionCallback) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter: BluetoothAdapter = bluetoothManager.adapter

        // Check for Bluetooth permissions
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // Request the missing permissions
                ActivityCompat.requestPermissions(
                    context as Activity, // Ensure context is an Activity
                    arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                    REQUEST_BLUETOOTH_PERMISSION // Use the defined constant
                )
                Toast.makeText(
                    context,
                    "Bluetooth permission is not granted",
                    Toast.LENGTH_SHORT
                ).show()
                callback.onConnectionResult(false) // Return via callback
                return
            }
        }
        bluetoothAdapter.cancelDiscovery()
        Thread {
            try {
//                bluetoothSocket = bluetoothDevice?.createInsecureRfcommSocketToServiceRecord(MY_UUID)
//                bluetoothSocket?.connect()
//                inputStream = bluetoothSocket?.inputStream
//                outputStream = bluetoothSocket?.outputStream

                val bluetoothSocket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
                    device.createRfcommSocketToServiceRecord(MY_UUID)
                }

                bluetoothSocket?.use { socket ->
                    socket.connect()
                    isConnected = socket.isConnected
                    if(isConnected) listenForData()
                }
            } catch (e: IOException ) {
                e.printStackTrace()
                try {
                    bluetoothSocket?.use { socket -> socket.close() }
                } catch (closeException: IOException) {
                    closeException.printStackTrace()
                    closeConnection()
                }
                callback.onConnectionResult(false) // Connection failed
            }
        }.start()

        if(isConnected) {
            bluetoothSocket?.use { socket->
                inputStream= socket.inputStream
                outputStream= socket.outputStream
            }
            callback.onConnectionResult(true)
        }
    }

    private fun listenForData() {
        Thread{
            val buffer = ByteArray(1024)
            var bytes: Int

            while (isConnected) {
                try {
                    bytes = inputStream?.read(buffer) ?: -1
                    if (bytes > 0) {
                        val incomingMessage = String(buffer, 0, bytes)
                        listener?.onDataReceived(incomingMessage)
                    }
                } catch (e: IOException) {
                    Toast.makeText(
                        context,
                        "IO Exception at listening",
                        Toast.LENGTH_SHORT
                    ).show()
                    e.printStackTrace()
                    closeConnection()
                    break
                }
            }
        }.start()
    }

    fun sendData(data: String) {
        try {
            outputStream?.write(data.toByteArray())
        } catch (e: IOException) {
            Toast.makeText(
                context,
                "IO Exception at sending",
                Toast.LENGTH_SHORT
            ).show()
            e.printStackTrace()
            closeConnection()
        }
    }

    fun closeConnection() {
        isConnected = false
        try {
            bluetoothSocket?.use { socket ->
                socket.inputStream.close()
                socket.outputStream.close()
                socket.close()
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(
                context,
                "IO Exception at closing",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
