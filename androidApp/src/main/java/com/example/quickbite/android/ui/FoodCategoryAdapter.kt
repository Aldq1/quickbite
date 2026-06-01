package com.example.quickbite.android.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.quickbite.android.databinding.ItemFoodCategoryBinding
import com.example.quickbite.android.ui.model.FoodCategory

class FoodCategoryAdapter(
    private val categories: List<FoodCategory>,
    private val onItemClick: (FoodCategory) -> Unit
) : RecyclerView.Adapter<FoodCategoryAdapter.ViewHolder>() {

    inner class ViewHolder(private val binding: ItemFoodCategoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(category: FoodCategory) {
            binding.tvEmoji.text = category.emoji
            binding.tvCategoryName.text = category.name
            binding.tvRestaurantCount.text = "${category.restaurantCount} restaurants"
            binding.flCategoryBg.setBackgroundColor(category.backgroundColor)
            binding.root.setOnClickListener { onItemClick(category) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFoodCategoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(categories[position])

    override fun getItemCount() = categories.size
}
