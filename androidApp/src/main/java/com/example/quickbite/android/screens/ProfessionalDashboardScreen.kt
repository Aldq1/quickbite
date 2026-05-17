package com.example.quickbite.android.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

private val PBrand     = Color(0xFFE8430A)
private val PTextDark  = Color(0xFF1C1C1E)
private val PTextMuted = Color(0xFF8A8A8E)
private val PBgSurface = Color(0xFFF7F7F7)
private val PWhite     = Color(0xFFFFFFFF)

// ── Root screen ───────────────────────────────────────────────────────────────
// Flow:
//   1. Fetch users/{uid}.linkedRestaurantId from Firestore.
//   2. If present → show WaiterDashboardScreen for that restaurant.
//   3. If absent  → show a "Link Restaurant" UI where the waiter enters
//                   the restaurant owner's UID (shown as a "Restaurant Code").

@Composable
fun ProfessionalDashboardScreen() {
    val uid = remember { FirebaseAuth.getInstance().currentUser?.uid }
    var linkedId   by remember { mutableStateOf<String?>(null) }
    var isLoading  by remember { mutableStateOf(true) }

    LaunchedEffect(uid) {
        if (uid == null) { isLoading = false; return@LaunchedEffect }
        FirebaseFirestore.getInstance()
            .collection("users").document(uid)
            .get()
            .addOnSuccessListener { doc ->
                linkedId = doc.getString("linkedRestaurantId")
                isLoading = false
            }
            .addOnFailureListener { isLoading = false }
    }

    when {
        isLoading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PBrand)
            }
        }

        linkedId != null -> {
            WaiterDashboardScreen(restaurantId = linkedId!!)
        }

        else -> {
            LinkRestaurantScreen(
                uid = uid,
                onLinked = { id -> linkedId = id }
            )
        }
    }
}

// ── Link restaurant screen ────────────────────────────────────────────────────

@Composable
private fun LinkRestaurantScreen(uid: String?, onLinked: (String) -> Unit) {
    var code      by remember { mutableStateOf("") }
    var isSaving  by remember { mutableStateOf(false) }
    var errorMsg  by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PBrand)
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "Dashboard Ospătar",
                    color = PWhite,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        containerColor = PBgSurface
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = PWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        Icons.Rounded.LinkOff,
                        contentDescription = null,
                        tint = PBrand,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "Conectează-te la un Restaurant",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = PTextDark,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Cere managerului tău Codul Restaurantului și introdu-l mai jos.",
                        fontSize = 13.sp,
                        color = PTextMuted,
                        textAlign = TextAlign.Center
                    )

                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it; errorMsg = null },
                        label = { Text("Cod Restaurant") },
                        placeholder = { Text("ex: abc123def456") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = errorMsg != null,
                        supportingText = errorMsg?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PBrand,
                            focusedLabelColor = PBrand,
                            cursorColor = PBrand
                        )
                    )

                    Button(
                        onClick = {
                            val trimmed = code.trim()
                            if (trimmed.isBlank()) {
                                errorMsg = "Codul nu poate fi gol."
                                return@Button
                            }
                            if (uid == null) {
                                errorMsg = "Utilizator neautentificat."
                                return@Button
                            }
                            isSaving = true
                            // Verify the restaurantId exists before saving
                            FirebaseFirestore.getInstance()
                                .collection("users").document(trimmed)
                                .get()
                                .addOnSuccessListener { doc ->
                                    if (!doc.exists() || doc.getString("role") != "RESTAURANT") {
                                        errorMsg = "Cod invalid sau restaurantul nu există."
                                        isSaving = false
                                    } else {
                                        FirebaseFirestore.getInstance()
                                            .collection("users").document(uid)
                                            .update("linkedRestaurantId", trimmed)
                                            .addOnSuccessListener { onLinked(trimmed) }
                                            .addOnFailureListener {
                                                errorMsg = "Eroare la salvare. Încearcă din nou."
                                                isSaving = false
                                            }
                                    }
                                }
                                .addOnFailureListener {
                                    errorMsg = "Eroare de rețea. Încearcă din nou."
                                    isSaving = false
                                }
                        },
                        enabled = !isSaving && code.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(13.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PBrand,
                            disabledContainerColor = PBrand.copy(alpha = 0.35f)
                        )
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                color = PWhite,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Conectează",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = PWhite
                            )
                        }
                    }
                }
            }
        }
    }
}
