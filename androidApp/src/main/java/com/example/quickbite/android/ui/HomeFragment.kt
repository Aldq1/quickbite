package com.example.quickbite.android.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.quickbite.android.databinding.FragmentHomeBinding
import com.example.quickbite.android.ui.model.Restaurant

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val restaurants = listOf(
        Restaurant(1, "Marios Pizzeria", "Italian • Pizza • Pasta",     "Pizza",   4.8f, "20-30 min"),
        Restaurant(2, "Dragon Palace",   "Chinese • Dim Sum • Noodles", "Chinese", 4.6f, "25-35 min"),
        Restaurant(3, "The Burger Lab",  "American • Burgers • Fries",  "Burgers", 4.9f, "15-25 min"),
        Restaurant(4, "Sakura Garden",   "Japanese • Sushi • Ramen",    "Japanese",4.7f, "30-40 min"),
        Restaurant(5, "Taco Fiesta",     "Mexican • Tacos • Burritos",  "Mexican", 4.5f, "20-30 min"),
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = RestaurantAdapter(restaurants) { restaurant ->
            val intent = Intent(requireContext(), RestaurantMenuActivity::class.java).apply {
                putExtra(RestaurantMenuActivity.EXTRA_RESTAURANT_ID, restaurant.id)
                putExtra(RestaurantMenuActivity.EXTRA_NAME,          restaurant.name)
                putExtra(RestaurantMenuActivity.EXTRA_RATING,        restaurant.rating)
                putExtra(RestaurantMenuActivity.EXTRA_DELIVERY_TIME, restaurant.deliveryTime)
                putExtra(RestaurantMenuActivity.EXTRA_CATEGORY,      restaurant.category)
            }
            startActivity(intent)
        }

        binding.rvRestaurants.apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = adapter
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
