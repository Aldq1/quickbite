package com.example.quickbite.android.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quickbite.android.services.FirestoreService
import com.example.quickbite.models.TableStatus
import com.google.firebase.firestore.FirebaseFirestore

private val OBrand       = Color(0xFFE8430A)
private val OTextDark    = Color(0xFF1C1C1E)
private val OTextMuted   = Color(0xFF8A8A8E)
private val OBgSurface   = Color(0xFFF7F7F7)
private val OWhite       = Color(0xFFFFFFFF)
private val OBrandTint   = Color(0xFFFFF0EC)
private val OGreenAccent = Color(0xFF34C759)

private data class MenuEntry(val name: String, val category: String, val price: Double = 0.0)

// ── Root screen ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientOrderingScreen(
    restaurantId: String,
    tableNumber: Int,
    onOrderPlaced: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var menuEntries   by remember { mutableStateOf<List<MenuEntry>>(emptyList()) }
    var isLoading     by remember { mutableStateOf(true) }
    var isPlacing     by remember { mutableStateOf(false) }
    var quantities    by remember { mutableStateOf(mapOf<String, Int>()) }
    var placedOrderId by remember { mutableStateOf<String?>(null) }
    var orderReady    by remember { mutableStateOf(false) }
    var tableOccupied by remember { mutableStateOf(false) }

    LaunchedEffect(restaurantId) {
        FirebaseFirestore.getInstance()
            .collection("users").document(restaurantId)
            .collection("restaurant_menu")
            .get()
            .addOnSuccessListener { snap ->
                menuEntries = snap.documents.mapNotNull { doc ->
                    val name = doc.getString("product") ?: return@mapNotNull null
                    MenuEntry(
                        name     = name,
                        category = doc.getString("category") ?: "",
                        price    = doc.getDouble("price") ?: 0.0
                    )
                }
                isLoading = false
            }
            .addOnFailureListener { isLoading = false }
    }

    // Watch placed order for completion notification
    DisposableEffect(placedOrderId) {
        val id = placedOrderId ?: return@DisposableEffect onDispose {}
        val reg = FirestoreService.listenToOrder(id) { status ->
            if (status == "COMPLETED") orderReady = true
        }
        onDispose { reg.remove() }
    }

    // Block ordering when table is already occupied by another session
    DisposableEffect(restaurantId, tableNumber) {
        val reg = FirebaseFirestore.getInstance()
            .collection("users").document(restaurantId)
            .collection("tables").document(tableNumber.toString())
            .addSnapshotListener { snapshot, _ ->
                tableOccupied = snapshot?.getString("status") == TableStatus.OCCUPIED
            }
        onDispose { reg.remove() }
    }

    val cartItems = quantities.filter { it.value > 0 }
    val cartTotal = cartItems.values.sum()

    // ── Order Ready dialog ────────────────────────────────────────────────────
    if (orderReady) {
        AlertDialog(
            onDismissRequest = {},
            shape = RoundedCornerShape(24.dp),
            containerColor = OWhite,
            icon = {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(OGreenAccent.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = OGreenAccent,
                        modifier = Modifier.size(44.dp)
                    )
                }
            },
            title = {
                Text(
                    text = "Comanda ta este gata!",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = OTextDark,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "Chelnerul se îndreaptă spre masa $tableNumber. Poftă bună!",
                    fontSize = 14.sp,
                    color = OTextMuted,
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = { orderReady = false; onOrderPlaced() },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OBrand),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Mulțumesc!", color = OWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(OBrand)
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Înapoi", tint = OWhite)
                }
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Masă $tableNumber",
                        color = OWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when {
                            placedOrderId != null -> "Comanda plasată · așteptăm..."
                            cartTotal > 0 -> "$cartTotal produs${if (cartTotal != 1) "e" else ""} selectate"
                            else -> "Alege ce dorești"
                        },
                        color = OWhite.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                }
            }
        },
        bottomBar = {
            if (cartItems.isNotEmpty() && placedOrderId == null) {
                Surface(shadowElevation = 12.dp, color = OWhite) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (tableOccupied) {
                            Text(
                                text = "Această masă este deja ocupată!",
                                color = Color(0xFFFF3B30),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Button(
                            onClick = {
                                isPlacing = true
                                val totalPrice = cartItems.entries.sumOf { (name, qty) ->
                                    (menuEntries.find { it.name == name }?.price ?: 0.0) * qty
                                }
                                val orderItems = cartItems.entries.map { (name, qty) ->
                                    val entry = menuEntries.find { it.name == name }
                                    mapOf(
                                        "name"     to name,
                                        "category" to (entry?.category ?: ""),
                                        "quantity" to qty
                                    )
                                }
                                FirebaseFirestore.getInstance()
                                    .collection("active_orders")
                                    .add(
                                        mapOf(
                                            "restaurantId" to restaurantId,
                                            "tableNumber"  to tableNumber,
                                            "items"        to orderItems,
                                            "totalPrice"   to totalPrice,
                                            "status"       to "PENDING",
                                            "timestamp"    to System.currentTimeMillis()
                                        )
                                    )
                                    .addOnSuccessListener { docRef ->
                                        FirestoreService.updateTableStatus(restaurantId, tableNumber, "OCCUPIED")
                                        placedOrderId = docRef.id
                                        quantities = emptyMap()
                                        isPlacing = false
                                        Toast.makeText(context, "Comanda a fost plasată!", Toast.LENGTH_SHORT).show()
                                    }
                                    .addOnFailureListener { e ->
                                        Toast.makeText(context, "Eroare: ${e.message}", Toast.LENGTH_SHORT).show()
                                        isPlacing = false
                                    }
                            },
                            enabled = !isPlacing && !tableOccupied,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = OBrand)
                        ) {
                            if (isPlacing) {
                                CircularProgressIndicator(color = OWhite, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Rounded.ShoppingCart, contentDescription = null, tint = OWhite, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Plasează Comanda ($cartTotal produse)", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = OWhite)
                            }
                        }
                    }
                }
            }
        },
        containerColor = OBgSurface
    ) { padding ->
        when {
            isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = OBrand) }

            // ── Awaiting order state ──────────────────────────────────────────
            placedOrderId != null -> AwaitingOrderScreen(
                modifier = Modifier.fillMaxSize().padding(padding),
                tableNumber = tableNumber,
                itemCount = cartTotal
            )

            menuEntries.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("🍽️", fontSize = 48.sp)
                    Text("Meniu indisponibil", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = OTextDark)
                    Text("Restaurantul nu are produse disponibile.", fontSize = 14.sp, color = OTextMuted)
                }
            }

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                menuEntries.groupBy { it.category }.forEach { (category, items) ->
                    if (category.isNotBlank()) {
                        Text(
                            text = category.uppercase(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = OTextMuted,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                        )
                    }
                    items.forEach { entry ->
                        OrderableItemCard(
                            entry = entry,
                            quantity = quantities[entry.name] ?: 0,
                            onIncrease = {
                                quantities = quantities + (entry.name to ((quantities[entry.name] ?: 0) + 1))
                            },
                            onDecrease = {
                                val cur = quantities[entry.name] ?: 0
                                quantities = if (cur <= 1) quantities - entry.name
                                else quantities + (entry.name to (cur - 1))
                            }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// ── Awaiting order screen ─────────────────────────────────────────────────────

@Composable
private fun AwaitingOrderScreen(modifier: Modifier, tableNumber: Int, itemCount: Int) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .background(OGreenAccent.copy(alpha = 0.10f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = OGreenAccent,
                modifier = Modifier.size(52.dp)
            )
        }
        Spacer(Modifier.height(28.dp))
        Text(
            text = "Comanda a fost plasată!",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = OTextDark
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Masă $tableNumber  ·  $itemCount ${if (itemCount == 1) "produs" else "produse"}",
            fontSize = 14.sp,
            color = OBrand,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(40.dp))
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = OBrand,
            trackColor = OBrand.copy(alpha = 0.15f)
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Chelnerul pregătește comanda...",
            fontSize = 13.sp,
            color = OTextMuted
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Vei fi notificat când este gata.",
            fontSize = 12.sp,
            color = OTextMuted.copy(alpha = 0.7f)
        )
    }
}

// ── Item card ─────────────────────────────────────────────────────────────────

@Composable
private fun OrderableItemCard(
    entry: MenuEntry,
    quantity: Int,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit
) {
    val selected = quantity > 0
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) OBrandTint else OWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 0.dp else 2.dp),
        border = if (selected) BorderStroke(1.5.dp, OBrand) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(text = entry.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = OTextDark)
                if (entry.category.isNotBlank()) {
                    Text(text = entry.category, fontSize = 12.sp, color = OBrand)
                }
                if (entry.price > 0.0) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${"%.2f".format(entry.price)} RON",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = OTextDark
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (quantity > 0) {
                    Box(
                        modifier = Modifier.size(32.dp).clip(CircleShape).background(OBgSurface),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(onClick = onDecrease, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Rounded.Remove, contentDescription = "Scade", tint = OTextDark, modifier = Modifier.size(16.dp))
                        }
                    }
                    Text(
                        text = quantity.toString(),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = OBrand,
                        modifier = Modifier.widthIn(min = 20.dp),
                        textAlign = TextAlign.Center
                    )
                }
                Box(
                    modifier = Modifier.size(32.dp).clip(CircleShape).background(OBrand),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = onIncrease, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Add, contentDescription = "Adaugă", tint = OWhite, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}
