package com.example.quickbite.android.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.example.quickbite.android.databinding.FragmentFoodCategoriesBinding
import com.example.quickbite.android.ui.model.FoodCategory

class FoodCategoriesFragment : Fragment() {

    private var _binding: FragmentFoodCategoriesBinding? = null
    private val binding get() = _binding!!

    private val categories = listOf(
        FoodCategory(1,  "Pizza",    "🍕", Color.parseColor("#2E1A0A"), 8),
        FoodCategory(2,  "Burgers",  "🍔", Color.parseColor("#1A1000"), 12),
        FoodCategory(3,  "Mexican",  "🌮", Color.parseColor("#0D1A0A"), 6),
        FoodCategory(4,  "Asian",    "🍜", Color.parseColor("#1A0A0A"), 15),
        FoodCategory(5,  "Sushi",    "🍱", Color.parseColor("#0A1418"), 7),
        FoodCategory(6,  "Italian",  "🍝", Color.parseColor("#1A1200"), 9),
        FoodCategory(7,  "BBQ",      "🔥", Color.parseColor("#1E0800"), 5),
        FoodCategory(8,  "Salads",   "🥗", Color.parseColor("#081A0E"), 4),
        FoodCategory(9,  "Desserts", "🍰", Color.parseColor("#18001A"), 10),
        FoodCategory(10, "Drinks",   "🥤", Color.parseColor("#081018"), 14),
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFoodCategoriesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = FoodCategoryAdapter(categories) { category ->
            Toast.makeText(requireContext(), "Browsing ${category.name}", Toast.LENGTH_SHORT).show()
        }

        binding.rvFoodCategories.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            this.adapter = adapter
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
