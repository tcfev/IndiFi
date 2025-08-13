package org.fordem.indifi.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import org.fordem.indifi.databinding.ItemDeviceBinding
import org.fordem.indifi.ui.model.DeviceInfo
import org.fordem.indifi.ui.utils.Constants
import java.text.DateFormat
import java.util.Date

class DeviceAdapter : RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>() {
    private var deviceList = listOf<DeviceInfo>()

    fun submitList(list: List<DeviceInfo>) {
        deviceList = list
        notifyDataSetChanged()
    }

    class DeviceViewHolder(
        private val binding: ItemDeviceBinding
    ) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(device: DeviceInfo) {
            binding.tvDeviceName.text = device.name
            binding.tvDeviceIp.text = device.ip
            binding.tvType.text = if (device.isGroupOwner) "GO" else "GM"
            binding.tvTime.text = DateFormat.getDateTimeInstance().format(Date(device.timestamp))

            itemView.setOnClickListener {
                Constants.openChatCallback(device)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(deviceList[position])
    }

    override fun getItemCount() = deviceList.size

    class DiffCallback : DiffUtil.ItemCallback<DeviceInfo>() {
        override fun areItemsTheSame(oldItem: DeviceInfo, newItem: DeviceInfo): Boolean {
            return oldItem.deviceId == newItem.deviceId
        }

        override fun areContentsTheSame(oldItem: DeviceInfo, newItem: DeviceInfo): Boolean {
            return oldItem == newItem
        }
    }
}
