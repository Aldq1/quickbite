package com.example.quickbite.android.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

@Composable
fun HomeScreen(navController: NavController) {
    LaunchedEffect(Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .get()
            .addOnSuccessListener { doc ->
                val route = when (doc.getString("role")) {
                    "RESTAURANT"   -> "restaurant_dashboard"
                    "CLIENT"       -> "client_dashboard"
                    "PRODUCER"     -> "producer_dashboard"
                    "PROFESSIONAL" -> "professional_dashboard"
                    else           -> return@addOnSuccessListener
                }
                navController.navigate(route) {
                    popUpTo("home") { inclusive = true }
                }
            }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}