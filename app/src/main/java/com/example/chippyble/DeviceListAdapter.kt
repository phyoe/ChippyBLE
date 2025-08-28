package com.example.chippyble

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.chippyble.databinding.DeviceListItemBinding

class DeviceListAdapter(
    private val onDeviceClick: (DeviceModel) -> Unit
) : ListAdapter<DeviceModel, DeviceListAdapter.DeviceViewHolder>(DeviceDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = DeviceListItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DeviceViewHolder(binding, onDeviceClick)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DeviceViewHolder(
        private val binding: DeviceListItemBinding,
        private val onDeviceClick: (DeviceModel) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(device: DeviceModel) {
            binding.deviceNameText.text = device.name
            binding.deviceAddressText.text = device.address

            // ペアリング状態を表示
            if (device.isPaired) {
                binding.pairedStatusText.visibility = android.view.View.VISIBLE
                binding.pairedStatusText.text = "Paired"
            } else {
                binding.pairedStatusText.visibility = android.view.View.GONE
            }

            binding.root.setOnClickListener {
                onDeviceClick(device)
            }
        }
    }

    class DeviceDiffCallback : DiffUtil.ItemCallback<DeviceModel>() {
        override fun areItemsTheSame(oldItem: DeviceModel, newItem: DeviceModel): Boolean {
            return oldItem.address == newItem.address
        }

        override fun areContentsTheSame(oldItem: DeviceModel, newItem: DeviceModel): Boolean {
            return oldItem == newItem
        }
    }
}