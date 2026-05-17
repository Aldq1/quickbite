package com.example.quickbite.android.services

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

object FirestoreService {

    private val db get() = FirebaseFirestore.getInstance()

    // ── Table status ──────────────────────────────────────────────────────────

    fun updateTableStatus(restaurantId: String, tableNumber: Int, status: String) {
        db.collection("users").document(restaurantId)
            .collection("tables").document(tableNumber.toString())
            .set(mapOf("status" to status, "tableNumber" to tableNumber))
    }

    // ── Order listener ────────────────────────────────────────────────────────

    fun listenToOrder(orderId: String, onStatusChange: (String) -> Unit): ListenerRegistration =
        db.collection("active_orders").document(orderId)
            .addSnapshotListener { snapshot, _ ->
                val status = snapshot?.getString("status") ?: return@addSnapshotListener
                onStatusChange(status)
            }
}
