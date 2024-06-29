package com.huanyu.myblue

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class DeviceListAdapter(
    private val devices: List<BluetoothDevice>,
    private val context: Context,
    private val clickListener: (BluetoothDevice) -> Unit

) : RecyclerView.Adapter<DeviceListAdapter.DeviceViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_2, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = devices[position]
        holder.bind(device)
        holder.itemView.setOnClickListener { clickListener(device) }
        holder.itemView.foreground = ContextCompat.getDrawable(context, R.drawable.my_text_image_ripple_effect)
    }

    override fun getItemCount(): Int {
        return devices.size
    }

    class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val deviceName: TextView = itemView.findViewById(android.R.id.text1)
        private val deviceAddress: TextView = itemView.findViewById(android.R.id.text2)

        @SuppressLint("MissingPermission")
        fun bind(device: BluetoothDevice) {
            deviceName.text = device.name ?: "Unknown Device"
            deviceAddress.text = device.address
        }
    }
}