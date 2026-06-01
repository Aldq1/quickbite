package com.example.quickbite.android.navigation

import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.example.quickbite.android.screens.ClientActiveSessionScreen
import com.example.quickbite.android.screens.ClientDashboardScreen
import com.example.quickbite.android.screens.ClientOrderingScreen
import com.example.quickbite.android.screens.LiveFloorPlanScreen
import com.example.quickbite.android.screens.HomeScreen
import com.example.quickbite.android.screens.LoginScreen
import com.example.quickbite.android.screens.ProfessionalDashboardScreen
import com.example.quickbite.android.screens.ProducerDashboardScreen
import com.example.quickbite.android.screens.RegisterScreen
import com.example.quickbite.android.screens.RestaurantDashboardScreen
import com.example.quickbite.android.screens.ClientHomeFeedScreen
import com.example.quickbite.android.screens.ProfileSettingsScreen
import com.example.quickbite.android.screens.QrScannerScreen
import com.example.quickbite.android.screens.RestaurantFeedScreen
import com.example.quickbite.android.screens.RestaurantMapScreen
import com.example.quickbite.android.screens.RestaurantPreviewScreen
import com.example.quickbite.android.screens.RoleSelectionScreen
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val context = LocalContext.current

    val startDestination = if (FirebaseAuth.getInstance().currentUser != null) "home" else "login"

    val signOut: () -> Unit = {
        FirebaseAuth.getInstance().signOut()
        navController.navigate("login") {
            popUpTo(0) { inclusive = true }
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {

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
            RestaurantDashboardScreen(onSignOut = signOut)
        }

        composable("client_dashboard") {
            ClientDashboardScreen(
                onNavigateToOrdering  = { restaurantId, tableNumber ->
                    navController.navigate("client_ordering/$restaurantId/$tableNumber")
                },
                onNavigateToQrScanner = { navController.navigate("qr_scanner") },
                onSignOut             = signOut
            )
        }

        composable(
            route = "client_ordering/{restaurantId}/{tableNumber}",
            arguments = listOf(
                navArgument("restaurantId") { type = NavType.StringType },
                navArgument("tableNumber")  { type = NavType.IntType }
            ),
            deepLinks = listOf(
                // QR codes generated by the desktop embed: quickbite://order/{restaurantId}/{tableNumber}
                navDeepLink { uriPattern = "quickbite://order/{restaurantId}/{tableNumber}" }
            )
        ) { backStackEntry ->
            val restaurantId = backStackEntry.arguments?.getString("restaurantId") ?: return@composable
            val tableNumber  = backStackEntry.arguments?.getInt("tableNumber") ?: 1
            ClientOrderingScreen(
                restaurantId  = restaurantId,
                tableNumber   = tableNumber,
                onOrderPlaced = {
                    if (!navController.popBackStack()) {
                        navController.navigate("home") { popUpTo(0) { inclusive = true } }
                    }
                },
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate("home") { popUpTo(0) { inclusive = true } }
                    }
                }
            )
        }

        composable(
            route     = "live_floor_plan/{restaurantId}",
            arguments = listOf(navArgument("restaurantId") { type = NavType.StringType })
        ) { backStackEntry ->
            val restaurantId = backStackEntry.arguments?.getString("restaurantId") ?: return@composable
            LiveFloorPlanScreen(
                restaurantId         = restaurantId,
                onNavigateToOrdering = { rid, tableNum ->
                    navController.navigate("client_ordering/$rid/$tableNum")
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable("producer_dashboard") {
            ProducerDashboardScreen()
        }

        composable("professional_dashboard") {
            ProfessionalDashboardScreen()
        }

        composable("restaurant_map") {
            RestaurantMapScreen(onBack = { navController.popBackStack() })
        }

        composable("restaurant_feed") {
            ClientHomeFeedScreen(
                onRestaurantClick = { restaurantId, _ ->
                    navController.navigate("restaurant_preview/$restaurantId")
                },
                onQrScanClick  = { navController.navigate("qr_scanner") },
                onProfileClick = { navController.navigate("profile_settings") }
            )
        }

        composable("client_home_feed") {
            ClientHomeFeedScreen(
                onRestaurantClick = { restaurantId, _ ->
                    navController.navigate("restaurant_preview/$restaurantId")
                },
                onQrScanClick  = { navController.navigate("qr_scanner") },
                onProfileClick = { navController.navigate("profile_settings") }
            )
        }

        composable(
            route     = "restaurant_preview/{restaurantId}",
            arguments = listOf(navArgument("restaurantId") { type = NavType.StringType })
        ) { backStackEntry ->
            val restaurantId = backStackEntry.arguments?.getString("restaurantId") ?: return@composable
            RestaurantPreviewScreen(
                restaurantId    = restaurantId,
                onScanQrClicked = { navController.navigate("qr_scanner?expectedRestaurantId=$restaurantId") },
                onBack          = { navController.popBackStack() }
            )
        }

        composable(
            route = "qr_scanner?expectedRestaurantId={expectedRestaurantId}",
            arguments = listOf(
                navArgument("expectedRestaurantId") {
                    type         = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val expectedRestaurantId = backStackEntry.arguments?.getString("expectedRestaurantId") ?: ""
            QrScannerScreen(
                expectedRestaurantId = expectedRestaurantId,
                onRestaurantScanned  = { rawValue ->
                    // QR format: "restaurantId_tableId_tableNumber"
                    val parts = rawValue.trim().split("_")
                    if (parts.size == 3 && parts[2].toIntOrNull() != null) {
                        navController.navigate(
                            "client_active_session/${parts[0]}/${parts[1]}/${parts[2]}"
                        ) { popUpTo("qr_scanner") { inclusive = true } }
                    } else {
                        Toast.makeText(context, "QR invalid. Scanați codul de la masa restaurantului.", Toast.LENGTH_LONG).show()
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route     = "client_active_session/{restaurantId}/{tableId}/{tableNumber}",
            arguments = listOf(
                navArgument("restaurantId") { type = NavType.StringType },
                navArgument("tableId")      { type = NavType.StringType },
                navArgument("tableNumber")  { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val restaurantId = backStackEntry.arguments?.getString("restaurantId") ?: return@composable
            val tableId      = backStackEntry.arguments?.getString("tableId")      ?: return@composable
            val tableNumber  = backStackEntry.arguments?.getInt("tableNumber")     ?: 1
            ClientActiveSessionScreen(
                restaurantId   = restaurantId,
                tableId        = tableId,
                tableNumber    = tableNumber,
                onSessionEnded = {
                    navController.navigate("client_home_feed") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable("profile_settings") {
            ProfileSettingsScreen(
                onSignOut = signOut,
                onBack    = { navController.popBackStack() }
            )
        }
    }
}
