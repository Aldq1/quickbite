package com.example.quickbite.android.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth

private val RegBrand      = Color(0xFFE8430A)
private val RegBrandLight = Color(0xFFFF7043)
private val RegTextDark   = Color(0xFF1C1C1E)
private val RegTextMuted  = Color(0xFF8A8A8E)
private val RegBorderIdle = Color(0xFFE0E0E0)
private val RegWhite      = Color(0xFFFFFFFF)

@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    onBackToLogin: () -> Unit
) {
    val auth = remember { FirebaseAuth.getInstance() }

    var email           by remember { mutableStateOf("") }
    var password        by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading       by remember { mutableStateOf(false) }
    var errorMessage    by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {

        // Warm gradient header band
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.38f)
                .background(Brush.verticalGradient(listOf(RegBrand, RegBrandLight)))
        )

        Column(modifier = Modifier.fillMaxSize()) {

            // ── Header ────────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.38f)
                    .statusBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(RegWhite.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "🍔", fontSize = 38.sp)
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "QuickBite",
                    color = RegWhite,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Creează un cont nou",
                    color = RegWhite.copy(alpha = 0.82f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // ── Form card ─────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .background(RegWhite)
                    .padding(horizontal = 28.dp, vertical = 32.dp)
            ) {
                Text(
                    text = "Înregistrare",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = RegTextDark
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Completați datele pentru a crea un cont",
                    fontSize = 13.sp,
                    color = RegTextMuted
                )
                Spacer(modifier = Modifier.height(28.dp))

                // Email
                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        errorMessage = null
                    },
                    label = { Text("Adresă email") },
                    leadingIcon = {
                        Icon(Icons.Rounded.Email, contentDescription = null, tint = RegBrand)
                    },
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = RegBrand,
                        unfocusedBorderColor = RegBorderIdle,
                        focusedLabelColor    = RegBrand,
                        unfocusedLabelColor  = RegTextMuted,
                        cursorColor          = RegBrand,
                        focusedTextColor     = RegTextDark,
                        unfocusedTextColor   = RegTextDark
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Password
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorMessage = null
                    },
                    label = { Text("Parolă") },
                    leadingIcon = {
                        Icon(Icons.Rounded.Lock, contentDescription = null, tint = RegBrand)
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff
                                              else Icons.Rounded.Visibility,
                                contentDescription = null,
                                tint = RegTextMuted
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None
                                           else PasswordVisualTransformation(),
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = RegBrand,
                        unfocusedBorderColor = RegBorderIdle,
                        focusedLabelColor    = RegBrand,
                        unfocusedLabelColor  = RegTextMuted,
                        cursorColor          = RegBrand,
                        focusedTextColor     = RegTextDark,
                        unfocusedTextColor   = RegTextDark
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Primary register button
                Button(
                    onClick = {
                        val finalEmail    = email.trim()
                        val finalPassword = password.trim()

                        if (finalEmail.isEmpty() || finalPassword.isEmpty()) {
                            errorMessage = "Vă rugăm să completați toate câmpurile."
                            return@Button
                        }
                        if (finalPassword.length < 6) {
                            errorMessage = "Parola trebuie să aibă cel puțin 6 caractere."
                            return@Button
                        }

                        isLoading = true
                        errorMessage = null

                        auth.createUserWithEmailAndPassword(finalEmail, finalPassword)
                            .addOnSuccessListener {
                                isLoading = false
                                onRegisterSuccess()
                            }
                            .addOnFailureListener { e ->
                                isLoading = false
                                errorMessage = e.localizedMessage ?: "Înregistrare eșuată"
                            }
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RegBrand),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = RegWhite,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        Text(
                            text = "Continuă",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            color = RegWhite
                        )
                    }
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Login link
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Ai deja cont? ", color = RegTextMuted, fontSize = 14.sp)
                    TextButton(
                        onClick = onBackToLogin,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = "Autentifică-te",
                            color = RegBrand,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}