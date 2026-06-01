package com.example.quickbite.android.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.quickbite.android.R
import com.example.quickbite.android.databinding.ActivityRestaurantMenuBinding
import com.example.quickbite.android.ui.model.FoodMenuItem

class RestaurantMenuActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRestaurantMenuBinding
    private lateinit var menuAdapter: MenuItemAdapter

    private val cartItems = mutableMapOf<Int, Int>()
    private val subCategories = listOf("All", "Starters", "Mains", "Desserts", "Drinks")
    private var selectedSubCategory = "All"
    private var allMenuItems = listOf<FoodMenuItem>()

    companion object {
        const val EXTRA_RESTAURANT_ID = "restaurant_id"
        const val EXTRA_NAME = "restaurant_name"
        const val EXTRA_RATING = "restaurant_rating"
        const val EXTRA_DELIVERY_TIME = "restaurant_delivery_time"
        const val EXTRA_CATEGORY = "restaurant_category"

        val menuData: Map<Int, List<FoodMenuItem>> = mapOf(
            1 to listOf(
                FoodMenuItem(101, "Margherita Pizza",    "Fresh tomato, mozzarella, basil",                  12.99, "Mains"),
                FoodMenuItem(102, "Pepperoni Blast",     "Spicy pepperoni, extra cheese, jalapeños",         15.99, "Mains"),
                FoodMenuItem(103, "Quattro Formaggi",    "Four-cheese blend on thin crust",                  14.49, "Mains"),
                FoodMenuItem(104, "Garlic Bread",        "Toasted with herb butter and mozzarella",           4.99, "Starters"),
                FoodMenuItem(105, "Bruschetta",          "Fresh tomato, basil, aged balsamic",                6.99, "Starters"),
                FoodMenuItem(106, "Tiramisu",            "Mascarpone, espresso-soaked ladyfingers",           7.99, "Desserts"),
                FoodMenuItem(107, "Panna Cotta",         "Vanilla cream, wild berry coulis",                  6.99, "Desserts"),
                FoodMenuItem(108, "Sparkling Water",     "San Pellegrino 500ml",                              2.99, "Drinks"),
                FoodMenuItem(109, "House Wine",          "Red or white, glass 150ml",                         7.49, "Drinks"),
            ),
            2 to listOf(
                FoodMenuItem(201, "Dim Sum Basket",      "8 pieces, assorted steamed dumplings",              9.99, "Starters"),
                FoodMenuItem(202, "Spring Rolls",        "Crispy vegetable, sweet chilli dip",                6.99, "Starters"),
                FoodMenuItem(203, "Kung Pao Chicken",    "Peanuts, chilli, sichuan pepper",                  13.99, "Mains"),
                FoodMenuItem(204, "Peking Duck (½)",     "Pancakes, hoisin sauce, cucumber",                 22.99, "Mains"),
                FoodMenuItem(205, "Egg Fried Rice",      "Wok-tossed with vegetables",                        8.99, "Mains"),
                FoodMenuItem(206, "Mango Pudding",       "Classic Hong Kong dessert",                         4.99, "Desserts"),
                FoodMenuItem(207, "Bubble Tea",          "Taro or milk tea with pearls",                      5.49, "Drinks"),
                FoodMenuItem(208, "Jasmine Tea",         "Premium loose leaf, pot for two",                   3.99, "Drinks"),
            ),
            3 to listOf(
                FoodMenuItem(301, "Classic Cheeseburger","Angus beef, cheddar, lettuce, tomato, pickles",    11.99, "Mains"),
                FoodMenuItem(302, "Smoky BBQ Burger",    "Pulled pork, BBQ sauce, house coleslaw",           14.99, "Mains"),
                FoodMenuItem(303, "Crispy Chicken Burger","Buttermilk chicken, pickles, sriracha mayo",      12.99, "Mains"),
                FoodMenuItem(304, "Loaded Fries",        "Cheese sauce, jalapeños, crispy bacon",             6.99, "Starters"),
                FoodMenuItem(305, "Onion Rings",         "Beer-battered, chipotle mayo dip",                  5.49, "Starters"),
                FoodMenuItem(306, "Brownie Sundae",      "Warm brownie, vanilla ice cream, fudge sauce",      7.99, "Desserts"),
                FoodMenuItem(307, "Chocolate Shake",     "Hand-spun with real chocolate ice cream",           5.99, "Drinks"),
                FoodMenuItem(308, "Classic Cola",        "Fountain drink, unlimited free refills",            2.99, "Drinks"),
            ),
            4 to listOf(
                FoodMenuItem(401, "Salmon Sashimi (6)", "Premium Norwegian Atlantic salmon",                 16.99, "Starters"),
                FoodMenuItem(402, "Edamame",             "Steamed, lightly salted pods",                      5.99, "Starters"),
                FoodMenuItem(403, "Dragon Roll",         "Avocado, shrimp tempura, cucumber, tobiko",        14.99, "Mains"),
                FoodMenuItem(404, "Spicy Tuna Roll",     "Bluefin tuna, cucumber, sriracha mayo",            13.49, "Mains"),
                FoodMenuItem(405, "Tonkotsu Ramen",      "Rich pork broth, chashu, soft egg, nori",          13.99, "Mains"),
                FoodMenuItem(406, "Mochi Ice Cream",     "Matcha, mango or strawberry (3 pcs)",               6.99, "Desserts"),
                FoodMenuItem(407, "Miso Soup",           "Silken tofu, wakame, green onion",                  3.99, "Drinks"),
                FoodMenuItem(408, "Sake",                "Premium junmai, warm or chilled",                   8.99, "Drinks"),
            ),
            5 to listOf(
                FoodMenuItem(501, "Guacamole & Chips",   "Hand-smashed avocado, lime, fresh cilantro",        7.99, "Starters"),
                FoodMenuItem(502, "Elote",               "Grilled corn, cotija cheese, chilli, lime",         5.99, "Starters"),
                FoodMenuItem(503, "Street Tacos (3 pcs)","Al pastor, white onion, cilantro, salsa verde",     9.99, "Mains"),
                FoodMenuItem(504, "Carne Asada Burrito", "Grilled beef, rice, black beans, pico de gallo",   11.99, "Mains"),
                FoodMenuItem(505, "Chicken Quesadilla",  "Grilled chicken, pepper jack, roasted peppers",    10.99, "Mains"),
                FoodMenuItem(506, "Churros (4 pcs)",     "Cinnamon sugar, dulce de leche dip",                5.99, "Desserts"),
                FoodMenuItem(507, "Horchata",            "Traditional rice milk, cinnamon, vanilla",          3.99, "Drinks"),
                FoodMenuItem(508, "Frozen Margarita",    "Lime or mango, salted rim",                         8.99, "Drinks"),
            ),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRestaurantMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val restaurantId   = intent.getIntExtra(EXTRA_RESTAURANT_ID, 1)
        val name           = intent.getStringExtra(EXTRA_NAME) ?: "Restaurant"
        val rating         = intent.getFloatExtra(EXTRA_RATING, 4.5f)
        val deliveryTime   = intent.getStringExtra(EXTRA_DELIVERY_TIME) ?: "30 min"
        val category       = intent.getStringExtra(EXTRA_CATEGORY) ?: ""

        allMenuItems = menuData[restaurantId] ?: emptyList()

        binding.tvRestaurantName.text = name
        binding.tvRating.text         = rating.toString()
        binding.tvDeliveryTime.text   = deliveryTime
        binding.tvCuisineType.text    = category

        binding.btnBack.setOnClickListener { finish() }
        binding.btnQr.setOnClickListener {
            Toast.makeText(this, "Point your camera at the QR code on your table", Toast.LENGTH_LONG).show()
        }

        setupSubCategories()
        setupRecyclerView()
        filterByCategory()
    }

    private fun setupSubCategories() {
        binding.llMenuCategories.removeAllViews()
        subCategories.forEach { cat ->
            val chip = LayoutInflater.from(this)
                .inflate(R.layout.item_category_chip, binding.llMenuCategories, false) as TextView
            chip.text = cat
            chip.setOnClickListener {
                selectedSubCategory = cat
                updateChipSelection()
                filterByCategory()
            }
            binding.llMenuCategories.addView(chip)
        }
        updateChipSelection()
    }

    private fun updateChipSelection() {
        for (i in 0 until binding.llMenuCategories.childCount) {
            val chip = binding.llMenuCategories.getChildAt(i) as TextView
            chip.isSelected = chip.text == selectedSubCategory
        }
    }

    private fun filterByCategory() {
        val filtered = if (selectedSubCategory == "All") allMenuItems
                       else allMenuItems.filter { it.category == selectedSubCategory }
        menuAdapter.updateItems(filtered)
    }

    private fun setupRecyclerView() {
        menuAdapter = MenuItemAdapter(emptyList()) { item ->
            cartItems[item.id] = (cartItems[item.id] ?: 0) + 1
            updateCartBar()
        }
        binding.rvRestaurantMenu.apply {
            layoutManager = LinearLayoutManager(this@RestaurantMenuActivity)
            adapter = menuAdapter
        }
    }

    private fun updateCartBar() {
        val count = cartItems.values.sum()
        val total = allMenuItems
            .filter { cartItems.containsKey(it.id) }
            .sumOf { it.price * (cartItems[it.id] ?: 0) }

        binding.cartBar.visibility    = if (count > 0) View.VISIBLE else View.GONE
        binding.tvCartCount.text      = count.toString()
        binding.tvCartTotal.text      = "$${String.format("%.2f", total)}"
    }
}
