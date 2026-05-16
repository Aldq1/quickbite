package com.example.quickbite.android.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.*
import com.example.quickbite.android.screens.HomeScreen
import com.example.quickbite.android.screens.LoginScreen
import com.example.quickbite.android.screens.RegisterScreen

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

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

        composable("home") {
            HomeScreen()
        }

        composable("register") {
            RegisterScreen(
                onRegisterClick = { email, password, role ->
                    // TODO: creare cont Firebase
                },
                onBackToLogin = {
                    navController.popBackStack()
                }
            )
        }
    }
}
