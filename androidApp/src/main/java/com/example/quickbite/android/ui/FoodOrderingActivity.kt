package com.example.quickbite.android.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.quickbite.android.R
import com.example.quickbite.android.databinding.ActivityFoodOrderingBinding

class FoodOrderingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFoodOrderingBinding

    private val homeFragment           by lazy { HomeFragment() }
    private val foodCategoriesFragment by lazy { FoodCategoriesFragment() }
    private val profileFragment        by lazy { ProfileFragment() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFoodOrderingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .add(R.id.nav_host_fragment, homeFragment,           "home")
                .add(R.id.nav_host_fragment, foodCategoriesFragment, "food").hide(foodCategoriesFragment)
                .add(R.id.nav_host_fragment, profileFragment,        "profile").hide(profileFragment)
                .commit()
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home    -> showFragment(homeFragment)
                R.id.nav_menu    -> showFragment(foodCategoriesFragment)
                R.id.nav_profile -> showFragment(profileFragment)
            }
            true
        }
    }

    private fun showFragment(target: Fragment) {
        supportFragmentManager.beginTransaction().apply {
            listOf(homeFragment, foodCategoriesFragment, profileFragment).forEach { f ->
                if (f == target) show(f) else hide(f)
            }
        }.commit()
    }
}
