package com.example.bluetoothvoiceapp

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.RecyclerView

class DeviceAdapter(
    private val deviceList: MutableList<BluetoothDevice>,
    private val listener: OnDeviceClickListener
) : RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>() {

    interface OnDeviceClickListener {
        fun onDeviceClick(device: BluetoothDevice)
    }

    inner class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val deviceName: TextView = itemView.findViewById(R.id.deviceName)
        val deviceAddress: TextView = itemView.findViewById(R.id.deviceAddress)

        init {
            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    listener.onDeviceClick(deviceList[position])
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.device_item, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = deviceList[position]
        holder.deviceName.text = device.name
        holder.deviceAddress.text = device.address

        val context = holder.itemView.context
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Optionally, handle the permission denial here.
            holder.deviceName.text = context.getString(R.string.permission_not_granted)
            return
        }

        holder.deviceName.text = device.name ?: context.getString(R.string.unknown_device)
    }

    override fun getItemCount(): Int {
        return deviceList.size
    }
}
