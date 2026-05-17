package com.example.quickbite.android.navigation


import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.*
import com.example.quickbite.android.screens.ClientDashboardScreen
import com.example.quickbite.android.screens.HomeScreen
import com.example.quickbite.android.screens.LoginScreen
import com.example.quickbite.android.screens.ProfessionalDashboardScreen
import com.example.quickbite.android.screens.ProducerDashboardScreen
import com.example.quickbite.android.screens.RegisterScreen
import com.example.quickbite.android.screens.RestaurantDashboardScreen
import com.example.quickbite.android.screens.RoleSelectionScreen
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val context = LocalContext.current

    NavHost(navController = navController, startDestination = "login") {

        composable("login") {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                onNavigateToRegister = {
                    navController.navigate("register")
                }
            )
        }

        composable("register") {
            RegisterScreen(
                onRegisterSuccess = {
                    navController.navigate("role_selection") {
                        popUpTo("register") { inclusive = true }
                    }
                },
                onBackToLogin = {
                    navController.popBackStack()
                }
            )
        }

        composable("role_selection") {
            RoleSelectionScreen(
                onRoleSelected = { role ->
                    val uid = FirebaseAuth.getInstance().currentUser?.uid
                    if (uid != null) {
                        FirebaseFirestore.getInstance()
                            .collection("users")
                            .document(uid)
                            .set(mapOf("role" to role.key))
                            .addOnSuccessListener {
                                navController.navigate("home") {
                                    popUpTo("role_selection") { inclusive = true }
                                }
                            }
                            .addOnFailureListener { e: Exception ->
                                Log.e("RoleSelection", "Failed to save role to Firestore", e)
                                Toast.makeText(
                                    context,
                                    "Eroare la salvarea rolului. Încearcă din nou.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                    } else {
                        Toast.makeText(context, "Utilizatorul nu este autentificat.", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        composable("home") {
            HomeScreen(navController = navController)
        }

        composable("restaurant_dashboard") {
            RestaurantDashboardScreen()
        }

        composable("client_dashboard") {
            ClientDashboardScreen()
        }

        composable("producer_dashboard") {
            ProducerDashboardScreen()
        }

        composable("professional_dashboard") {
            ProfessionalDashboardScreen()
        }
    }
}