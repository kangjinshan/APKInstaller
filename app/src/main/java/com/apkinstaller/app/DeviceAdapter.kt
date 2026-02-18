package com.apkinstaller.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DeviceAdapter(
    private val devices: MutableList<Device>,
    private val onDeviceClick: (Device) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>() {

    private var selectedPosition = -1

    inner class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvDeviceIp: TextView = itemView.findViewById(R.id.tvDeviceIp)
        val tvDeviceStatus: TextView = itemView.findViewById(R.id.tvDeviceStatus)

        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    // 取消之前的选择
                    if (selectedPosition >= 0 && selectedPosition < devices.size) {
                        devices[selectedPosition].isSelected = false
                        notifyItemChanged(selectedPosition)
                    }
                    
                    // 选择新设备
                    selectedPosition = position
                    devices[position].isSelected = true
                    notifyItemChanged(position)
                    
                    onDeviceClick(devices[position])
                }
            }
        }

        fun bind(device: Device) {
            tvDeviceIp.text = device.ip
            tvDeviceStatus.text = device.status
            
            // 设置选中状态的背景色
            if (device.isSelected) {
                itemView.setBackgroundColor(itemView.context.getColor(R.color.teal_200))
            } else {
                itemView.setBackgroundColor(itemView.context.getColor(android.R.color.transparent))
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(devices[position])
    }

    override fun getItemCount() = devices.size

    fun updateDevices(newDevices: List<Device>) {
        devices.clear()
        devices.addAll(newDevices)
        selectedPosition = -1
        notifyDataSetChanged()
    }
}
