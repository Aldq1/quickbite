package com.example.quickbite.android.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

// Demo restaurant ID used when Firestore has no data or the role is CLIENT
private const val DEMO_ID = "7QBG68DH1bciyywUTt1klkoyzwv2"

@Composable
fun HomeScreen(navController: NavController) {
    LaunchedEffect(Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            navController.navigate("login") { popUpTo("home") { inclusive = true } }
            return@LaunchedEffect
        }
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .get()
            .addOnSuccessListener { doc ->
                val route = when (doc.getString("role")) {
                    "RESTAURANT"   -> "restaurant_dashboard"
                    // CLIENT must scan the QR at their table — no manual table selection allowed
                    "CLIENT"       -> "client_dashboard"
                    "PRODUCER"     -> "producer_dashboard"
                    "PROFESSIONAL" -> "professional_dashboard"
                    else           -> "role_selection"
                }
                navController.navigate(route) {
                    popUpTo("home") { inclusive = true }
                }
            }
            .addOnFailureListener {
                navController.navigate("client_dashboard") {
                    popUpTo("home") { inclusive = true }
                }
            }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}