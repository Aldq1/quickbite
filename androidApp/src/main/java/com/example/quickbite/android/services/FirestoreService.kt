package com.example.quickbite.android.services

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

object FirestoreService {

    private val db get() = FirebaseFirestore.getInstance()

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resumeWith(Result.success(it)) }
        addOnFailureListener { cont.resumeWith(Result.failure(it)) }
    }

    // ── Table status ──────────────────────────────────────────────────────────

    // Fire-and-forget variant — kept for callers that don't run in a coroutine
    fun updateTableStatus(restaurantId: String, tableNumber: Int, status: String, occupantUid: String? = null) {
        db.collection("users").document(restaurantId)
            .collection("tables").document(tableNumber.toString())
            .set(mapOf("status" to status, "tableNumber" to tableNumber, "occupantUid" to occupantUid))
    }

    // Suspend variant — use this from coroutine contexts for proper back-pressure and error handling
    suspend fun updateTableStatusAsync(restaurantId: String, tableNumber: Int, status: String, occupantUid: String? = null) {
        withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { cont ->
                db.collection("users").document(restaurantId)
                    .collection("tables").document(tableNumber.toString())
                    .set(mapOf("status" to status, "tableNumber" to tableNumber, "occupantUid" to occupantUid))
                    .addOnSuccessListener { cont.resumeWith(Result.success(Unit)) }
                    .addOnFailureListener { cont.resumeWith(Result.failure(it)) }
            }
        }
    }

    // ── Order listener ────────────────────────────────────────────────────────

    fun listenToOrder(orderId: String, onStatusChange: (String) -> Unit): ListenerRegistration =
        db.collection("orders").document(orderId)
            .addSnapshotListener { snapshot, _ ->
                val status = snapshot?.getString("status") ?: return@addSnapshotListener
                onStatusChange(status)
            }
}
