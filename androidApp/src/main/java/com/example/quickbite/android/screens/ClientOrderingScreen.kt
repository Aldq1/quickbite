package com.example.quickbite.android.screens

import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quickbite.android.services.FirestoreService
import com.example.quickbite.models.OrderStatus
import com.example.quickbite.models.TableStatus
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

// ── Colour palette ────────────────────────────────────────────────────────────

private val OBrand       = Color(0xFFFF6D00)   // primary brand (Deep Orange)
private val OBrandDark   = Color(0xFFE65100)
private val OBrandTint   = Color(0xFFFFF3EE)   // very light orange tint
private val OTextDark    = Color(0xFF1C1C1E)
private val OTextMuted   = Color(0xFF8A8A8E)
private val OBgSurface   = Color(0xFFF7F7F7)
private val OWhite       = Color(0xFFFFFFFF)
private val OGreenAccent = Color(0xFF34C759)
private val ORedError    = Color(0xFFFF3B30)
private val OAmber       = Color(0xFFFF9F0A)
private val OSlate       = Color(0xFF263238)

// ── Data models ───────────────────────────────────────────────────────────────

private data class MenuEntry(
    val name: String,
    val category: String,
    val price: Double = 0.0,
    val description: String = ""
)

private val DEMO_MENU = listOf(
    MenuEntry("Burger QuickBite Epic",       "Burgeri",   42.00, "Carne de vită 200g, cheddar aged, bacon crispy, sos special QB"),
    MenuEntry("Pizza Quattro Formaggi",      "Pizza",     39.00, "Mozzarella, gorgonzola, parmezan, pecorino — blat crocant pe vatră"),
    MenuEntry("Cartofi cu Parmezan & Trufe", "Garnituri", 18.00, "Cartofi belgieni, sos de parmezan, ulei de trufe, rozmarin"),
    MenuEntry("Limonadă cu Mentă",           "Băuturi",   15.00, "Lămâie stoarsă, sirop de mentă, apă carbogazoasă — 500 ml"),
    MenuEntry("Tiramisu de Casă",            "Deserturi", 22.00, "Rețetă originală italiană — mascarpone, espresso, cacao Valrhona"),
    MenuEntry("Somon Gravlax cu Avocado",    "Pește",     56.00, "File de somon marinat, cremă de avocado, capere, lemon zest"),
    MenuEntry("Sparanghel la Grătar",        "Intrări",   32.00, "Sparanghel verde, sos hollandaise, ou poché, chipsuri de șuncă"),
    MenuEntry("Flat White",                  "Cafea",     14.00, "Dublu espresso, lapte de oat textură mătăsoasă"),
)

private data class CartItem(val name: String, val category: String, val price: Double, val quantity: Int)

// ── Helpers ───────────────────────────────────────────────────────────────────

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resumeWith(Result.success(it)) }
    addOnFailureListener { cont.resumeWith(Result.failure(it)) }
}

private fun categoryEmoji(category: String) = when (category.trim().lowercase()) {
    "burgeri"   -> "🍔"
    "pizza"     -> "🍕"
    "garnituri" -> "🍟"
    "băuturi"   -> "🍹"
    "deserturi" -> "🍮"
    "pește"     -> "🐟"
    "intrări"   -> "🥗"
    "cafea"     -> "☕"
    else        -> "🍽️"
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
    var tableStatusKnown by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("Toate") }

    // Completely block system back — prevents accidental sign-out on an empty back stack
    BackHandler(enabled = true) { }

    LaunchedEffect(restaurantId) {
        try {
            val snap = withContext(Dispatchers.IO) {
                FirebaseFirestore.getInstance()
                    .collection("users").document(restaurantId)
                    .collection("restaurant_menu")
                    .get()
                    .await()
            }
            val fetched = withContext(Dispatchers.Default) {
                snap.documents.mapNotNull { doc ->
                    val name = doc.getString("product") ?: return@mapNotNull null
                    MenuEntry(
                        name        = name,
                        category    = doc.getString("category") ?: "",
                        price       = doc.getDouble("price") ?: 0.0,
                        description = doc.getString("description") ?: ""
                    )
                }
            }
            menuEntries = fetched.ifEmpty { DEMO_MENU }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { menuEntries = DEMO_MENU }
        finally { isLoading = false }
    }

    DisposableEffect(placedOrderId) {
        val id = placedOrderId ?: return@DisposableEffect onDispose {}
        val reg = FirestoreService.listenToOrder(id) { status ->
            if (status == "COMPLETED") orderReady = true
        }
        onDispose { reg.remove() }
    }

    DisposableEffect(restaurantId, tableNumber) {
        val reg = FirebaseFirestore.getInstance()
            .collection("users").document(restaurantId)
            .collection("tables").document(tableNumber.toString())
            .addSnapshotListener { snapshot, _ ->
                val isOccupied  = snapshot?.getString("status") == TableStatus.OCCUPIED
                val occupantUid = snapshot?.getString("occupantUid")
                tableOccupied    = isOccupied && occupantUid != null && occupantUid != currentUid
                tableStatusKnown = true
            }
        onDispose { reg.remove() }
    }

    val cartTotal = cartItems.sumOf { it.quantity }

    // ── Cart item manipulation ─────────────────────────────────────────────────
    val increaseItem: (MenuEntry) -> Unit = { entry ->
        val idx = cartItems.indexOfFirst { it.name == entry.name }
        if (idx >= 0) cartItems[idx] = cartItems[idx].copy(quantity = cartItems[idx].quantity + 1)
        else cartItems.add(CartItem(entry.name, entry.category, entry.price, 1))
    }
    val decreaseItem: (MenuEntry) -> Unit = { entry ->
        val idx = cartItems.indexOfFirst { it.name == entry.name }
        if (idx >= 0) {
            if (cartItems[idx].quantity <= 1) cartItems.removeAt(idx)
            else cartItems[idx] = cartItems[idx].copy(quantity = cartItems[idx].quantity - 1)
        }
    }

    // ── Order placement ────────────────────────────────────────────────────────
    val placeOrder: () -> Unit = {
        if (currentUid != null && !isPlacing && !tableOccupied) {
            isPlacing = true
            val capturedCount  = cartTotal
            val uid            = currentUid          // captured as non-null String in this branch
            val capturedRid    = restaurantId        // same value the table-status write uses
            val capturedTable  = tableNumber
            val totalPrice     = cartItems.sumOf { it.price * it.quantity }

            // Explicit HashMap<String, Any> per item — no Kotlin data-class serialisation,
            // no nullable types, no Firestore reflection. Plain Java Map the SDK can't choke on.
            val serializedItems: List<HashMap<String, Any>> = cartItems.map { item ->
                hashMapOf(
                    "name"     to item.name,
                    "category" to item.category,
                    "price"    to item.price,
                    "quantity" to item.quantity
                )
            }

            scope.launch(Dispatchers.IO) {
                try {
                    // Pre-generate the document ID so success/failure logs carry the same ID.
                    val orderId   = java.util.UUID.randomUUID().toString()
                    val orderData = hashMapOf<String, Any>(
                        "restaurantId" to capturedRid,
                        "tableNumber"  to capturedTable,
                        "occupantUid"  to uid,
                        "items"        to serializedItems,
                        "totalPrice"   to totalPrice,
                        "status"       to OrderStatus.PENDING,
                        "timestamp"    to System.currentTimeMillis()
                    )

                    println("FIREBASE_WRITE_ATTEMPT: collection=orders id=$orderId restaurantId=$capturedRid table=$capturedTable items=${serializedItems.size} total=$totalPrice status=${OrderStatus.PENDING}")
                    Log.d("QuickBite", "FIREBASE_WRITE_ATTEMPT: id=$orderId restaurantId=$capturedRid table=$capturedTable")

                    // Use .document(id).set() + explicit listeners to avoid the Task<Void>
                    // null-return issue that kills the generic .await() extension silently.
                    suspendCancellableCoroutine<Unit> { cont ->
                        FirebaseFirestore.getInstance()
                            .collection("orders")
                            .document(orderId)
                            .set(orderData)
                            .addOnSuccessListener { cont.resumeWith(Result.success(Unit)) }
                            .addOnFailureListener { cont.resumeWith(Result.failure(it)) }
                    }

                    println("FIREBASE_WRITE_SUCCESS: Order successfully added to Firestore! id=$orderId restaurantId=$capturedRid table=$capturedTable status=${OrderStatus.PENDING}")
                    Log.d("QuickBite", "FIREBASE_WRITE_SUCCESS: id=$orderId")

                    FirestoreService.updateTableStatusAsync(
                        capturedRid, capturedTable, TableStatus.OCCUPIED, occupantUid = uid
                    )
                    withContext(Dispatchers.Main) {
                        placedItemCount = capturedCount
                        cartItems.clear()
                        placedOrderId   = orderId
                        isPlacing       = false
                        Toast.makeText(context, "Comanda a fost plasată!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    println("FIREBASE_WRITE_ERROR: Failed with exception: ${e.javaClass.simpleName}: ${e.message} | restaurantId=$capturedRid table=$capturedTable")
                    Log.e("QuickBite", "FIREBASE_WRITE_ERROR: restaurantId=$capturedRid table=$capturedTable", e)
                    withContext(Dispatchers.Main) {
                        isPlacing = false
                        Toast.makeText(context, "Eroare la plasarea comenzii.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // ── Order ready dialog ────────────────────────────────────────────────────
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
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null,
                        tint = OGreenAccent, modifier = Modifier.size(44.dp))
                }
            },
            title = {
                Text("Comanda ta este gata!", fontSize = 18.sp,
                    fontWeight = FontWeight.Bold, color = OTextDark, textAlign = TextAlign.Center)
            },
            text = {
                Text("Chelnerul se îndreaptă spre masa $tableNumber. Poftă bună!",
                    fontSize = 14.sp, color = OTextMuted, textAlign = TextAlign.Center)
            },
            confirmButton = {
                Button(
                    onClick = { orderReady = false; onOrderPlaced() },
                    shape  = RoundedCornerShape(12.dp),
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
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Înapoi", tint = OWhite)
                }
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Masă $tableNumber", color = OWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = when {
                            tableOccupied         -> "Masă blocată"
                            placedOrderId != null -> "Comanda plasată · așteptăm..."
                            cartTotal > 0         -> "$cartTotal produs${if (cartTotal != 1) "e" else ""} selectate"
                            else                  -> "Alege ce dorești"
                        },
                        color = OWhite.copy(alpha = 0.8f), fontSize = 12.sp
                    )
                }
            }
        },
        containerColor = OBgSurface
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {

            when {
                isLoading || !tableStatusKnown -> Box(
                    modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(color = OBrand) }

                placedOrderId != null -> AwaitingOrderScreen(
                    modifier = Modifier.fillMaxSize(), tableNumber = tableNumber, itemCount = placedItemCount
                )

                tableOccupied -> OccupiedTableScreen(
                    modifier = Modifier.fillMaxSize(), tableNumber = tableNumber, onBack = onBack
                )

                menuEntries.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
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

                else -> {
                    val allCategories = remember(menuEntries) {
                        menuEntries.map { it.category }.filter { it.isNotBlank() }.distinct()
                    }
                    val filteredEntries = if (selectedCategory == "Toate") menuEntries
                                          else menuEntries.filter { it.category == selectedCategory }
                    val grouped = remember(filteredEntries) { filteredEntries.groupBy { it.category } }

                    Column(modifier = Modifier.fillMaxSize()) {
                        CategoryFilterRow(
                            categories = allCategories,
                            selected   = selectedCategory,
                            onSelect   = { selectedCategory = it }
                        )
                        LazyColumn(
                            modifier       = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 14.dp, end = 14.dp, top = 4.dp, bottom = 104.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            grouped.forEach { (category, categoryItems) ->
                                if (selectedCategory == "Toate" && category.isNotBlank()) {
                                    item(key = "hdr_$category") {
                                        Row(
                                            verticalAlignment     = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier              = Modifier.padding(top = 10.dp, bottom = 2.dp)
                                        ) {
                                            Text(categoryEmoji(category), fontSize = 14.sp)
                                            Text(
                                                text = category.uppercase(),
                                                fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                                                color = OTextMuted, letterSpacing = 1.sp
                                            )
                                        }
                                    }
                                }
                                items(items = categoryItems, key = { "${category}_${it.name}" }) { entry ->
                                    OrderableItemCard(
                                        entry    = entry,
                                        quantity = cartItems.find { it.name == entry.name }?.quantity ?: 0,
                                        onIncrease = { increaseItem(entry) },
                                        onDecrease = { decreaseItem(entry) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Floating cart button ──────────────────────────────────────────
            AnimatedVisibility(
                visible  = cartItems.isNotEmpty() && placedOrderId == null && !tableOccupied,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 22.dp)
                    .navigationBarsPadding(),
                enter = fadeIn(tween(180)) +
                        slideInVertically(
                            initialOffsetY = { it / 2 },
                            animationSpec  = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)
                        ) +
                        scaleIn(initialScale = 0.85f, animationSpec = spring(Spring.DampingRatioMediumBouncy)),
                exit  = fadeOut(tween(140)) +
                        slideOutVertically(
                            targetOffsetY = { it / 2 },
                            animationSpec = tween(160)
                        ) +
                        scaleOut(targetScale = 0.88f, animationSpec = tween(140))
            ) {
                FloatingCartButton(
                    itemCount  = cartTotal,
                    totalPrice = cartItems.sumOf { it.price * it.quantity },
                    isLoading  = isPlacing,
                    onClick    = placeOrder
                )
            }
        }
    }
}

// ── Category filter row ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryFilterRow(
    categories: List<String>,
    selected:   String,
    onSelect:   (String) -> Unit
) {
    LazyRow(
        contentPadding         = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement  = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = selected == "Toate",
                onClick  = { onSelect("Toate") },
                label    = { Text("🍽️ Toate", fontSize = 13.sp) },
                shape    = RoundedCornerShape(20.dp),
                colors   = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = OBrand,
                    selectedLabelColor     = OWhite,
                    containerColor         = OWhite,
                    labelColor             = OTextDark
                ),
                border = FilterChipDefaults.filterChipBorder(
                    borderColor         = OBrand.copy(alpha = 0.3f),
                    selectedBorderColor = OBrand,
                    borderWidth         = 1.dp,
                    selectedBorderWidth = 1.5.dp
                )
            )
        }
        items(categories) { category ->
            FilterChip(
                selected = selected == category,
                onClick  = { onSelect(category) },
                label    = { Text("${categoryEmoji(category)} $category", fontSize = 13.sp) },
                shape    = RoundedCornerShape(20.dp),
                colors   = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = OBrand,
                    selectedLabelColor     = OWhite,
                    containerColor         = OWhite,
                    labelColor             = OTextDark
                ),
                border = FilterChipDefaults.filterChipBorder(
                    borderColor         = OBrand.copy(alpha = 0.3f),
                    selectedBorderColor = OBrand,
                    borderWidth         = 1.dp,
                    selectedBorderWidth = 1.5.dp
                )
            )
        }
    }
}

// ── Floating cart button ──────────────────────────────────────────────────────

@Composable
private fun FloatingCartButton(
    itemCount:  Int,
    totalPrice: Double,
    isLoading:  Boolean,
    onClick:    () -> Unit
) {
    Surface(
        onClick          = onClick,
        enabled          = !isLoading,
        shape            = RoundedCornerShape(28.dp),
        color            = OBrand,
        shadowElevation  = 20.dp,
        tonalElevation   = 4.dp,
        modifier         = Modifier.widthIn(min = 300.dp, max = 380.dp)
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 20.dp, vertical = 15.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            if (isLoading) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = OWhite, modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp
                    )
                }
            } else {
                // Count badge
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(OWhite.copy(alpha = 0.22f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = itemCount.toString(),
                        fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = OWhite
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Plasează Comanda",
                        fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = OWhite
                    )
                    Text(
                        "$itemCount ${if (itemCount == 1) "produs" else "produse"}",
                        fontSize = 12.sp, color = OWhite.copy(alpha = 0.78f)
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "%.2f".format(totalPrice),
                        fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = OWhite
                    )
                    Text("RON", fontSize = 11.sp, color = OWhite.copy(alpha = 0.75f))
                }
            }
        }
    }
}

// ── Menu item card ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrderableItemCard(
    entry:     MenuEntry,
    quantity:  Int,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit
) {
    val selected = quantity > 0
    var expanded by remember { mutableStateOf(false) }
    val hasExtra = entry.description.isNotBlank()

    ElevatedCard(
        onClick   = { if (hasExtra) expanded = !expanded },
        modifier  = Modifier
            .fillMaxWidth()
            .then(
                if (selected) Modifier.border(1.5.dp, OBrand, RoundedCornerShape(16.dp))
                else Modifier
            )
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness    = Spring.StiffnessMediumLow
                )
            ),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.elevatedCardColors(
            containerColor = if (selected) OBrandTint else OWhite
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (selected) 4.dp else 1.dp,
            pressedElevation = 8.dp
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                // Left: name + category
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = entry.name,
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = OTextDark
                    )
                    if (entry.category.isNotBlank()) {
                        Text(text = entry.category, fontSize = 12.sp, color = OBrand)
                    }
                }

                // Right: quantity controls + price badge
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Expand/collapse hint icon
                    if (hasExtra && !selected) {
                        Icon(
                            imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                            contentDescription = null,
                            tint = OTextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Price badge
                    if (entry.price > 0.0) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (selected) OBrand else OBrand.copy(alpha = 0.10f)
                        ) {
                            Text(
                                text = "${"%.0f".format(entry.price)} RON",
                                fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                                color    = if (selected) OWhite else OBrand,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // Stepper
                    AnimatedVisibility(visible = quantity > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(28.dp).background(OBgSurface, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                IconButton(onClick = onDecrease, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Rounded.Remove, null, tint = OTextDark, modifier = Modifier.size(14.dp))
                                }
                            }
                            Text(
                                text = quantity.toString(),
                                fontSize = 15.sp, fontWeight = FontWeight.Bold, color = OBrand,
                                modifier = Modifier.widthIn(min = 22.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    Box(
                        modifier = Modifier.size(28.dp).background(OBrand, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(onClick = onIncrease, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Rounded.Add, null, tint = OWhite, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            // ── Expandable description ─────────────────────────────────────
            AnimatedVisibility(visible = expanded || selected) {
                Column {
                    if (entry.description.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(
                                    if (selected) OBrand.copy(alpha = 0.20f)
                                    else OTextMuted.copy(alpha = 0.12f)
                                )
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text     = entry.description,
                            fontSize = 12.sp,
                            color    = OTextMuted,
                            lineHeight = 18.sp,
                            overflow = TextOverflow.Visible
                        )
                    }
                    // Total for this item when in cart
                    if (selected && entry.price > 0.0) {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Subtotal", fontSize = 12.sp, color = OTextMuted)
                            Text(
                                "${"%.2f".format(entry.price * quantity)} RON",
                                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = OBrand
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Occupied table blocking screen ────────────────────────────────────────────

@Composable
private fun OccupiedTableScreen(modifier: Modifier, tableNumber: Int, onBack: () -> Unit) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(104.dp).clip(CircleShape).background(ORedError.copy(alpha = 0.09f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier.size(72.dp).clip(CircleShape).background(ORedError.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Lock, null, tint = ORedError, modifier = Modifier.size(36.dp))
            }
        }
        Spacer(Modifier.height(30.dp))
        Text("Masă Ocupată", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = OTextDark, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(ORedError.copy(alpha = 0.08f)).padding(horizontal = 18.dp, vertical = 7.dp)
        ) {
            Text("Masa $tableNumber", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ORedError)
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "Această masă este deja gestionată de un alt dispozitiv. Vă rugăm să folosiți acel telefon pentru a comanda.",
            fontSize = 15.sp, color = OTextMuted, textAlign = TextAlign.Center, lineHeight = 23.sp
        )
        Spacer(Modifier.height(44.dp))
        Button(
            onClick  = onBack,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape    = RoundedCornerShape(14.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = OBrand)
        ) {
            Text("Am înțeles", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = OWhite)
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
            modifier = Modifier.size(88.dp).background(OGreenAccent.copy(alpha = 0.10f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.CheckCircle, null, tint = OGreenAccent, modifier = Modifier.size(52.dp))
        }
        Spacer(Modifier.height(28.dp))
        Text("Comanda a fost plasată!", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = OTextDark)
        Spacer(Modifier.height(8.dp))
        Text(
            "Masă $tableNumber  ·  $itemCount ${if (itemCount == 1) "produs" else "produse"}",
            fontSize = 14.sp, color = OBrand, fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(40.dp))
        LinearProgressIndicator(
            modifier   = Modifier.fillMaxWidth(0.55f).height(3.dp).clip(RoundedCornerShape(2.dp)),
            color      = OBrand,
            trackColor = OBrand.copy(alpha = 0.15f)
        )
        Spacer(Modifier.height(14.dp))
        Text("Bucătăria pregătește comanda...", fontSize = 13.sp, color = OTextMuted)
        Spacer(Modifier.height(4.dp))
        Text("Vei fi notificat când este gata.", fontSize = 12.sp, color = OTextMuted.copy(alpha = 0.7f))
    }
}
