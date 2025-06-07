package com.example.quickbite.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.*
import com.example.quickbite.android.screens.LoginScreen
import com.example.quickbite.android.screens.RegisterScreen

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "login") {
        composable("login") {
            LoginScreen(
                onLoginClick = { email, password ->

                },
                onNavigateToRegister = {
                    navController.navigate("register")
                }
            )
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
