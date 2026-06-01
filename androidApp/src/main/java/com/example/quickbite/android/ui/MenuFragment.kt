package com.example.quickbite.android.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.quickbite.android.R
import com.example.quickbite.android.databinding.FragmentMenuBinding
import com.example.quickbite.android.ui.model.FoodMenuItem

class MenuFragment : Fragment() {

    private var _binding: FragmentMenuBinding? = null
    private val binding get() = _binding!!

    private val categories = listOf("All", "Burgers", "Pizza", "Pasta", "Drinks", "Desserts")
    private var selectedCategory = "All"

    private val allItems = listOf(
        FoodMenuItem(1, "Margherita Pizza", "Tomato sauce, mozzarella, fresh basil", 12.99, "Pizza"),
        FoodMenuItem(2, "Pepperoni Blast", "Spicy pepperoni, extra cheese, jalapeños", 15.99, "Pizza"),
        FoodMenuItem(3, "Classic Cheeseburger", "Angus beef, cheddar, lettuce, tomato", 11.99, "Burgers"),
        FoodMenuItem(4, "Smoky BBQ Burger", "Pulled pork, BBQ sauce, house coleslaw", 14.99, "Burgers"),
        FoodMenuItem(5, "Spaghetti Carbonara", "Spaghetti, guanciale, eggs, pecorino romano", 13.99, "Pasta"),
        FoodMenuItem(6, "Pesto Fusilli", "Basil pesto, cherry tomatoes, pine nuts", 12.49, "Pasta"),
        FoodMenuItem(7, "Craft Lemonade", "Fresh squeezed, mint, sparkling water", 4.99, "Drinks"),
        FoodMenuItem(8, "Mango Smoothie", "Alphonso mango, coconut milk, chia seeds", 5.99, "Drinks"),
        FoodMenuItem(9, "Tiramisu", "Mascarpone cream, espresso, ladyfingers", 7.99, "Desserts"),
        FoodMenuItem(10, "Chocolate Lava Cake", "Warm dark chocolate, vanilla ice cream", 8.99, "Desserts"),
    )

    private lateinit var menuAdapter: MenuItemAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMenuBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupCategories()
        filterItems()
    }

    private fun setupCategories() {
        binding.llCategories.removeAllViews()
        categories.forEach { category ->
            val chip = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_category_chip, binding.llCategories, false) as TextView
            chip.text = category
            chip.setOnClickListener {
                selectedCategory = category
                updateChipSelection()
                filterItems()
            }
            binding.llCategories.addView(chip)
        }
        updateChipSelection()
    }

    private fun updateChipSelection() {
        for (i in 0 until binding.llCategories.childCount) {
            binding.llCategories.getChildAt(i).isSelected = (
                (binding.llCategories.getChildAt(i) as TextView).text == selectedCategory
            )
        }
    }

    private fun filterItems() {
        val filtered = if (selectedCategory == "All") allItems
                       else allItems.filter { it.category == selectedCategory }
        menuAdapter.updateItems(filtered)
    }

    private fun setupRecyclerView() {
        menuAdapter = MenuItemAdapter(emptyList()) { item ->
            Toast.makeText(requireContext(), "Added ${item.name}", Toast.LENGTH_SHORT).show()
        }
        binding.rvMenuItems.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = menuAdapter
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
