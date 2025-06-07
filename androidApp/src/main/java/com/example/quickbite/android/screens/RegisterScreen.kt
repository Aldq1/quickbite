package com.example.quickbite.screens.register

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun RegisterScreen(
    onRegisterClick: (String, String, String) -> Unit,
    onBackToLogin: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("RESTAURANT") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Înregistrare", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Parolă") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("Rol:", style = MaterialTheme.typography.bodyLarge)
        Row {
            RadioButton(
                selected = role == "RESTAURANT",
                onClick = { role = "RESTAURANT" }
            )
            Text("Restaurant")

            Spacer(modifier = Modifier.width(16.dp))

            RadioButton(
                selected = role == "CLIENT",
                onClick = { role = "CLIENT" }
            )
            Text("Client")
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { onRegisterClick(email, password, role) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Înregistrează-te")
        }

        TextButton(onClick = onBackToLogin) {
            Text("Ai deja cont? Autentifică-te")
        }
    }
}

