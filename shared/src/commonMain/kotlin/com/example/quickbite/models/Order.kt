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
    const val PENDING    = "PENDING"
    const val PRIMITA    = "PRIMITA"      // received by kitchen — Romanian QR flow
    const val COOKING    = "COOKING"
    const val FINALIZATA = "FINALIZATA"   // completed — Romanian QR flow
    const val DELIVERED  = "DELIVERED"
    const val COMPLETED  = "COMPLETED"
}

object TableStatus {
    const val FREE                 = "FREE"
    const val LIBERA               = "Liberă"               // Romanian QR flow
    const val OCCUPIED             = "OCCUPIED"
    const val OCUPATA              = "OCUPATA"              // Romanian QR flow
    const val SOLICITARE_CURATENIE = "SOLICITARE_CURATENIE" // cleaning requested
    const val PAYMENT_REQUESTED    = "PAYMENT_REQUESTED"
}
