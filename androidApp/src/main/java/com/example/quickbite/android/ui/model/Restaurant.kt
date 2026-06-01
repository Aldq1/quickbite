package com.example.quickbite.android.ui.model

data class Restaurant(
    val id: Int,
    val name: String,
    val cuisine: String,
    val category: String,
    val rating: Float,
    val deliveryTime: String
)
