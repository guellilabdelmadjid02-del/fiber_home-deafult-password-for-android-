package com.example.fiberhome

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.fiberhome.databinding.ItemWifiBinding

class WifiAdapter(private val onItemSelected: (WifiItem) -> Unit) :
    RecyclerView.Adapter<WifiAdapter.WifiViewHolder>() {

    private var items = listOf<WifiItem>()

    fun updateItems(newItems: List<WifiItem>) {
        items = newItems.sortedByDescending { it.isFiberHome }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WifiViewHolder {
        val binding = ItemWifiBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return WifiViewHolder(binding)
    }

    override fun onBindViewHolder(holder: WifiViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class WifiViewHolder(private val binding: ItemWifiBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: WifiItem) {
            binding.tvSsid.text = item.ssid
            binding.tvBssid.text = item.bssid
            
            if (item.isFiberHome) {
                binding.ivBadge.visibility = View.VISIBLE
                binding.cardView.setCardBackgroundColor(
                    ContextCompat.getColor(binding.root.context, android.R.color.holo_green_light).let { 
                        // Just a light tint
                        0x1500FF00.toInt() or (it and 0x00FFFFFF)
                    }
                )
            } else {
                binding.ivBadge.visibility = View.GONE
                binding.cardView.setCardBackgroundColor(ContextCompat.getColor(binding.root.context, android.R.color.white))
            }

            binding.root.setOnClickListener { onItemSelected(item) }
        }
    }
}
