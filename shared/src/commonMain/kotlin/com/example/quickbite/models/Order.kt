package com.example.quickbite.models

data class OrderItem(
    val name: String = "",
    val category: String = "",
    val quantity: Int = 1
)

data class Order(
    val id: String = "",
    val restaurantId: String = "",
    val tableNumber: Int = 0,
    val items: List<OrderItem> = emptyList(),
    val totalPrice: Double = 0.0,
    val status: String = OrderStatus.PENDING,
    val timestamp: Long = 0L
)

object OrderStatus {
    const val PENDING   = "PENDING"
    const val COOKING   = "COOKING"    // kitchen accepted, in preparation
    const val DELIVERED = "DELIVERED"
    const val COMPLETED = "COMPLETED"
}

object TableStatus {
    const val FREE               = "FREE"
    const val OCCUPIED           = "OCCUPIED"
    const val PAYMENT_REQUESTED  = "PAYMENT_REQUESTED"
}
