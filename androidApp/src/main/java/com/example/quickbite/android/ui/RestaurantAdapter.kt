package com.example.quickbite.android.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.quickbite.android.databinding.ItemRestaurantCardBinding
import com.example.quickbite.android.ui.model.Restaurant

class RestaurantAdapter(
    private val restaurants: List<Restaurant>,
    private val onItemClick: (Restaurant) -> Unit = {}
) : RecyclerView.Adapter<RestaurantAdapter.ViewHolder>() {

    inner class ViewHolder(private val binding: ItemRestaurantCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(restaurant: Restaurant) {
            binding.tvRestaurantName.text = restaurant.name
            binding.tvCuisine.text = restaurant.cuisine
            binding.tvRating.text = restaurant.rating.toString()
            binding.tvDeliveryTime.text = restaurant.deliveryTime
            binding.tvCategoryBadge.text = restaurant.category
            binding.root.setOnClickListener { onItemClick(restaurant) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRestaurantCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(restaurants[position])

    override fun getItemCount() = restaurants.size
}
