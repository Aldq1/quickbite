package com.example.quickbite.android.models

sealed class UserRole(
    val key: String,
    val label: String,
    val emoji: String,
    val description: String
) {
    object Restaurant   : UserRole(
        key         = "RESTAURANT",
        label       = "Restaurant",
        emoji       = "🍽️",
        description = "Gestionează meniul, comenzile și personalul restaurantului tău"
    )
    object Producer     : UserRole(
        key         = "PRODUCER",
        label       = "Producător",
        emoji       = "🌾",
        description = "Furnizează ingrediente și produse către restaurante și HoReCa"
    )
    object Client       : UserRole(
        key         = "CLIENT",
        label       = "Client",
        emoji       = "🛒",
        description = "Comandă mâncare și descoperă restaurantele din jurul tău"
    )
    object Professional : UserRole(
        key         = "PROFESSIONAL",
        label       = "Profesionist",
        emoji       = "👨‍🍳",
        description = "Personal HoReCa: bucătar, chelner, barman sau manager"
    )

    companion object {
        val all: List<UserRole> = listOf(Restaurant, Producer, Client, Professional)

        fun fromKey(key: String): UserRole? = all.firstOrNull { it.key == key }
    }
}