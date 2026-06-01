package com.example.quickbite.android.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.quickbite.android.databinding.ItemMenuItemBinding
import com.example.quickbite.android.ui.model.FoodMenuItem

class MenuItemAdapter(
    private var items: List<FoodMenuItem>,
    private val onAddClick: (FoodMenuItem) -> Unit = {}
) : RecyclerView.Adapter<MenuItemAdapter.ViewHolder>() {

    inner class ViewHolder(private val binding: ItemMenuItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: FoodMenuItem) {
            binding.tvItemName.text = item.name
            binding.tvItemDescription.text = item.description
            binding.tvItemPrice.text = "$${String.format("%.2f", item.price)}"
            binding.btnAddItem.setOnClickListener { onAddClick(item) }
        }
    }

    fun updateItems(newItems: List<FoodMenuItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMenuItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(items[position])

    override fun getItemCount() = items.size
}
