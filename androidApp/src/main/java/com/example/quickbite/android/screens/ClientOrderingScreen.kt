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
import androidx.compose.material.icons.rounded.Lock
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
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

private val OBrand       = Color(0xFFE8430A)
private val OTextDark    = Color(0xFF1C1C1E)
private val OTextMuted   = Color(0xFF8A8A8E)
private val OBgSurface   = Color(0xFFF7F7F7)
private val OWhite       = Color(0xFFFFFFFF)
private val OBrandTint   = Color(0xFFFFF0EC)
private val OGreenAccent = Color(0xFF34C759)
private val ORedError    = Color(0xFFFF3B30)

private data class MenuEntry(val name: String, val category: String, val price: Double = 0.0)
private data class CartItem(val name: String, val category: String, val price: Double, val quantity: Int)

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resumeWith(Result.success(it)) }
    addOnFailureListener { cont.resumeWith(Result.failure(it)) }
}

// Merges incoming cart items into an existing Firestore items list, combining quantities for duplicates.
// Firestore returns numbers as Long; both Int and Long are handled safely.
private fun mergeOrderItems(
    existing: List<Map<String, Any>>,
    incoming: List<Map<String, Any>>
): List<Map<String, Any>> {
    val result = existing.map { it.toMutableMap() }.toMutableList()
    for (item in incoming) {
        val name = item["name"] as? String ?: continue
        val incomingQty = when (val q = item["quantity"]) {
            is Int  -> q
            is Long -> q.toInt()
            else    -> 0
        }
        val idx = result.indexOfFirst { it["name"] == name }
        if (idx >= 0) {
            val existingQty = when (val q = result[idx]["quantity"]) {
                is Int  -> q
                is Long -> q.toInt()
                else    -> 0
            }
            result[idx]["quantity"] = existingQty + incomingQty
        } else {
            result.add(item.toMutableMap())
        }
    }
    return result
}

// ── Root screen ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientOrderingScreen(
    restaurantId: String,
    tableNumber: Int,
    onOrderPlaced: () -> Unit,
    onBack: () -> Unit
) {
    val context    = LocalContext.current
    val scope      = rememberCoroutineScope()
    val currentUid = remember { FirebaseAuth.getInstance().currentUser?.uid }
    var menuEntries      by remember { mutableStateOf<List<MenuEntry>>(emptyList()) }
    var isLoading        by remember { mutableStateOf(true) }
    var isPlacing        by remember { mutableStateOf(false) }
    val cartItems        = remember { mutableStateListOf<CartItem>() }
    var placedItemCount  by remember { mutableStateOf(0) }
    var placedOrderId    by remember { mutableStateOf<String?>(null) }
    var orderReady       by remember { mutableStateOf(false) }
    var tableOccupied    by remember { mutableStateOf(false) }
    // Stays false until the first Firestore snapshot fires — prevents brief menu flash on occupied tables
    var tableStatusKnown by remember { mutableStateOf(false) }

    LaunchedEffect(restaurantId) {
        try {
            val snap = withContext(Dispatchers.IO) {
                FirebaseFirestore.getInstance()
                    .collection("users").document(restaurantId)
                    .collection("restaurant_menu")
                    .get()
                    .await()
            }
            menuEntries = withContext(Dispatchers.Default) {
                snap.documents.mapNotNull { doc ->
                    val name = doc.getString("product") ?: return@mapNotNull null
                    MenuEntry(
                        name     = name,
                        category = doc.getString("category") ?: "",
                        price    = doc.getDouble("price") ?: 0.0
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // leave menuEntries empty — UI handles the empty state
        } finally {
            isLoading = false
        }
    }

    // Watch placed order for completion notification
    DisposableEffect(placedOrderId) {
        val id = placedOrderId ?: return@DisposableEffect onDispose {}
        val reg = FirestoreService.listenToOrder(id) { status ->
            if (status == "COMPLETED") orderReady = true
        }
        onDispose { reg.remove() }
    }

    // Real-time table status — blocks access only when occupied by a DIFFERENT user's UID.
    // If the current user owns the session (same UID), they are allowed to continue ordering.
    DisposableEffect(restaurantId, tableNumber) {
        val reg = FirebaseFirestore.getInstance()
            .collection("users").document(restaurantId)
            .collection("tables").document(tableNumber.toString())
            .addSnapshotListener { snapshot, _ ->
                val isOccupied   = snapshot?.getString("status") == TableStatus.OCCUPIED
                val occupantUid  = snapshot?.getString("occupantUid")
                // Blocked = table is occupied AND the lock belongs to someone else
                tableOccupied    = isOccupied && occupantUid != null && occupantUid != currentUid
                tableStatusKnown = true
            }
        onDispose { reg.remove() }
    }

    val cartTotal = cartItems.sumOf { it.quantity }

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
                            tableOccupied         -> "Masă blocată"
                            placedOrderId != null -> "Comanda plasată · așteptăm..."
                            cartTotal > 0         -> "$cartTotal produs${if (cartTotal != 1) "e" else ""} selectate"
                            else                  -> "Alege ce dorești"
                        },
                        color = OWhite.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                }
            }
        },
        bottomBar = {
            if (cartItems.isNotEmpty() && placedOrderId == null && !tableOccupied) {
                Surface(shadowElevation = 12.dp, color = OWhite) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (currentUid == null) return@Button
                                isPlacing = true
                                val capturedCount = cartTotal
                                val totalPrice = cartItems.sumOf { it.price * it.quantity }
                                val orderItems: List<Map<String, Any>> = cartItems.map { item ->
                                    mapOf(
                                        "name"     to item.name,
                                        "category" to item.category,
                                        "quantity" to item.quantity
                                    )
                                }
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        // Single-field query — no composite index required.
                                        // restaurantId + tableNumber are verified client-side.
                                        val existingSnap = FirebaseFirestore.getInstance()
                                            .collection("active_orders")
                                            .whereEqualTo("occupantUid", currentUid)
                                            .whereEqualTo("status", "PENDING")
                                            .get()
                                            .await()

                                        val existingDoc = existingSnap.documents.firstOrNull { doc ->
                                            doc.getString("restaurantId") == restaurantId &&
                                            doc.getLong("tableNumber")?.toInt() == tableNumber
                                        }

                                        val orderId: String
                                        if (existingDoc != null) {
                                            // Active session found — merge new items into the existing order
                                            @Suppress("UNCHECKED_CAST")
                                            val existingItems = existingDoc.get("items") as? List<Map<String, Any>> ?: emptyList()
                                            val existingTotal = existingDoc.getDouble("totalPrice") ?: 0.0
                                            existingDoc.reference.update(
                                                mapOf(
                                                    "items"      to mergeOrderItems(existingItems, orderItems),
                                                    "totalPrice" to existingTotal + totalPrice
                                                )
                                            ).await()
                                            orderId = existingDoc.id
                                        } else {
                                            // No active session — create a new order and lock the table
                                            val docRef = FirebaseFirestore.getInstance()
                                                .collection("active_orders")
                                                .add(
                                                    mapOf(
                                                        "restaurantId" to restaurantId,
                                                        "tableNumber"  to tableNumber,
                                                        "occupantUid"  to currentUid,
                                                        "items"        to orderItems,
                                                        "totalPrice"   to totalPrice,
                                                        "status"       to "PENDING",
                                                        "timestamp"    to System.currentTimeMillis()
                                                    )
                                                )
                                                .await()
                                            FirestoreService.updateTableStatusAsync(
                                                restaurantId, tableNumber, TableStatus.OCCUPIED, occupantUid = currentUid
                                            )
                                            orderId = docRef.id
                                        }

                                        withContext(Dispatchers.Main) {
                                            placedItemCount = capturedCount
                                            cartItems.clear()
                                            placedOrderId = orderId
                                            isPlacing     = false
                                            Toast.makeText(context, "Comanda a fost plasată!", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (_: Exception) {
                                        withContext(Dispatchers.Main) {
                                            isPlacing = false
                                            Toast.makeText(context, "Eroare la plasarea comenzii.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
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
            // Wait for both the menu fetch and the first table snapshot before rendering anything
            isLoading || !tableStatusKnown -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = OBrand) }

            // ── Order already placed: awaiting delivery ───────────────────────
            // Check this BEFORE tableOccupied: once we own the order, the table
            // will be OCCUPIED by us — we must not block our own awaiting screen.
            placedOrderId != null -> AwaitingOrderScreen(
                modifier = Modifier.fillMaxSize().padding(padding),
                tableNumber = tableNumber,
                itemCount = placedItemCount
            )

            // ── Anti-hijack: table occupied by another session ────────────────
            tableOccupied -> OccupiedTableScreen(
                modifier    = Modifier.fillMaxSize().padding(padding),
                tableNumber = tableNumber,
                onBack      = onBack
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
                            quantity = cartItems.find { it.name == entry.name }?.quantity ?: 0,
                            onIncrease = {
                                val idx = cartItems.indexOfFirst { it.name == entry.name }
                                if (idx >= 0) cartItems[idx] = cartItems[idx].copy(quantity = cartItems[idx].quantity + 1)
                                else cartItems.add(CartItem(entry.name, entry.category, entry.price, 1))
                            },
                            onDecrease = {
                                val idx = cartItems.indexOfFirst { it.name == entry.name }
                                if (idx >= 0) {
                                    if (cartItems[idx].quantity <= 1) cartItems.removeAt(idx)
                                    else cartItems[idx] = cartItems[idx].copy(quantity = cartItems[idx].quantity - 1)
                                }
                            }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// ── Occupied table blocking screen ───────────────────────────────────────────

@Composable
private fun OccupiedTableScreen(
    modifier: Modifier,
    tableNumber: Int,
    onBack: () -> Unit
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Double-ring warning icon
        Box(
            modifier = Modifier
                .size(104.dp)
                .clip(CircleShape)
                .background(ORedError.copy(alpha = 0.09f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(ORedError.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint               = ORedError,
                    modifier           = Modifier.size(36.dp)
                )
            }
        }

        Spacer(Modifier.height(30.dp))

        Text(
            text       = "Masă Ocupată",
            fontSize   = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color      = OTextDark,
            textAlign  = TextAlign.Center
        )

        Spacer(Modifier.height(10.dp))

        // Table number badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(ORedError.copy(alpha = 0.08f))
                .padding(horizontal = 18.dp, vertical = 7.dp)
        ) {
            Text(
                text       = "Masa $tableNumber",
                fontSize   = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color      = ORedError
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text       = "Această masă este deja gestionată de un alt dispozitiv de la masa ta. Vă rugăm să folosiți acel telefon pentru a comanda.",
            fontSize   = 15.sp,
            color      = OTextMuted,
            textAlign  = TextAlign.Center,
            lineHeight = 23.sp
        )

        Spacer(Modifier.height(44.dp))

        Button(
            onClick  = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape  = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = OBrand)
        ) {
            Text(
                text       = "Am înțeles",
                fontSize   = 15.sp,
                fontWeight = FontWeight.Bold,
                color      = OWhite
            )
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
