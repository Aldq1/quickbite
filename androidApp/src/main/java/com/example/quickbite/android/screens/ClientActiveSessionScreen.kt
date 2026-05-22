package com.example.quickbite.android.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.quickbite.models.OrderStatus
import com.example.quickbite.models.TableStatus
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

// ── Palette ───────────────────────────────────────────────────────────────────

private val ABg          = Color(0xFF0C0C0C)
private val ASurface     = Color(0xFF161616)
private val AGlass       = Color(0x14FFFFFF)
private val AGlassBorder = Color(0x1AFFFFFF)
private val ABrand       = Color(0xFFE8430A)
private val ABrandDim    = Color(0x1AE8430A)
private val ATextPrimary = Color(0xFFFFFFFF)
private val ATextMuted   = Color(0xFF9A9A9A)
private val AGreen       = Color(0xFF30D158)
private val AGreenDim    = Color(0x2030D158)
private val ARed         = Color(0xFFFF3B30)
private val ARedDim      = Color(0x26FF3B30)
private val AAmber       = Color(0xFFFF9F0A)

// ── Local cart model ──────────────────────────────────────────────────────────

private data class ActiveCartItem(
    val name    : String,
    val category: String,
    val price   : Double,
    val quantity: Int
)

// ── Screen ────────────────────────────────────────────────────────────────────

/**
 * Locked ordering session entered after QR scan.
 * Writes orders to `restaurants/{restaurantId}/orders` (status = PRIMITA).
 * Updates `restaurants/{restaurantId}/tables/{tableId}` for table status changes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientActiveSessionScreen(
    restaurantId  : String,
    tableId       : String,
    tableNumber   : Int,
    onSessionEnded: () -> Unit,
    vm            : ClientViewModel = viewModel()
) {
    val db        = remember { FirebaseFirestore.getInstance() }
    val scope     = rememberCoroutineScope()
    val menuState by vm.menuState.collectAsStateWithLifecycle()

    val cart                = remember { mutableStateListOf<ActiveCartItem>() }
    var selectedCategory    by remember { mutableStateOf("Toate") }
    var isPlacingOrder      by remember { mutableStateOf(false) }
    var isCallingCleaner    by remember { mutableStateOf(false) }
    var isEndingSession     by remember { mutableStateOf(false) }
    var showOrderSentDialog by remember { mutableStateOf(false) }
    var showLeaveDialog     by remember { mutableStateOf(false) }

    // Locked session — system back is disabled
    BackHandler(enabled = true) {}

    LaunchedEffect(restaurantId) { vm.loadMenu(restaurantId) }

    // Mark table occupied as soon as the session screen opens
    LaunchedEffect(tableId) {
        try {
            suspendCancellableCoroutine<Unit> { cont ->
                db.collection("restaurants").document(restaurantId)
                    .collection("tables").document(tableId)
                    .update("status", TableStatus.OCUPATA)
                    .addOnSuccessListener { cont.resumeWith(Result.success(Unit)) }
                    .addOnFailureListener { cont.resumeWith(Result.failure(it)) }
            }
        } catch (e: CancellationException) { throw e }
          catch (_: Exception) {}
    }

    val cartTotal = cart.sumOf { it.quantity }
    val cartPrice = cart.sumOf { it.price * it.quantity }

    // ── Cart helpers ──────────────────────────────────────────────────────────
    val increase: (MenuEntry) -> Unit = { entry ->
        val i = cart.indexOfFirst { it.name == entry.name }
        if (i >= 0) cart[i] = cart[i].copy(quantity = cart[i].quantity + 1)
        else cart.add(ActiveCartItem(entry.name, entry.category, entry.price, 1))
    }
    val decrease: (MenuEntry) -> Unit = { entry ->
        val i = cart.indexOfFirst { it.name == entry.name }
        if (i >= 0) {
            if (cart[i].quantity <= 1) cart.removeAt(i)
            else cart[i] = cart[i].copy(quantity = cart[i].quantity - 1)
        }
    }

    // ── Place order → restaurants/{restaurantId}/orders ───────────────────────
    val placeOrder: () -> Unit = {
        if (cart.isNotEmpty() && !isPlacingOrder) {
            isPlacingOrder = true
            val serializedItems: List<HashMap<String, Any>> = cart.map { item ->
                hashMapOf(
                    "name"     to item.name,
                    "category" to item.category,
                    "price"    to item.price,
                    "quantity" to item.quantity
                )
            }
            val capturedPrice = cartPrice
            scope.launch(Dispatchers.IO) {
                try {
                    val orderId   = java.util.UUID.randomUUID().toString()
                    val orderData = hashMapOf<String, Any>(
                        "tableId"     to tableId,
                        "tableNumber" to tableNumber,
                        "items"       to serializedItems,
                        "totalPrice"  to capturedPrice,
                        "status"      to OrderStatus.PRIMITA,
                        "timestamp"   to System.currentTimeMillis()
                    )
                    suspendCancellableCoroutine<Unit> { cont ->
                        db.collection("restaurants").document(restaurantId)
                            .collection("orders").document(orderId)
                            .set(orderData)
                            .addOnSuccessListener { cont.resumeWith(Result.success(Unit)) }
                            .addOnFailureListener { cont.resumeWith(Result.failure(it)) }
                    }
                    withContext(Dispatchers.Main) {
                        cart.clear()
                        isPlacingOrder      = false
                        showOrderSentDialog = true
                    }
                } catch (e: CancellationException) { throw e }
                  catch (_: Exception) { withContext(Dispatchers.Main) { isPlacingOrder = false } }
            }
        }
    }

    // ── Call cleaner → tables/{tableId} status = SOLICITARE_CURATENIE ─────────
    val callCleaner: () -> Unit = {
        if (!isCallingCleaner) {
            isCallingCleaner = true
            scope.launch(Dispatchers.IO) {
                try {
                    suspendCancellableCoroutine<Unit> { cont ->
                        db.collection("restaurants").document(restaurantId)
                            .collection("tables").document(tableId)
                            .update("status", TableStatus.SOLICITARE_CURATENIE)
                            .addOnSuccessListener { cont.resumeWith(Result.success(Unit)) }
                            .addOnFailureListener { cont.resumeWith(Result.failure(it)) }
                    }
                } catch (e: CancellationException) { throw e }
                  catch (_: Exception) {}
                withContext(Dispatchers.Main) { isCallingCleaner = false }
            }
        }
    }

    // ── End session → tables/{tableId} status = Liberă → navigate away ────────
    val endSession: () -> Unit = {
        if (!isEndingSession) {
            isEndingSession = true
            scope.launch(Dispatchers.IO) {
                try {
                    suspendCancellableCoroutine<Unit> { cont ->
                        db.collection("restaurants").document(restaurantId)
                            .collection("tables").document(tableId)
                            .update("status", TableStatus.LIBERA)
                            .addOnSuccessListener { cont.resumeWith(Result.success(Unit)) }
                            .addOnFailureListener { cont.resumeWith(Result.failure(it)) }
                    }
                } catch (e: CancellationException) { throw e }
                  catch (_: Exception) {}
                withContext(Dispatchers.Main) {
                    isEndingSession = false
                    onSessionEnded()
                }
            }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────

    if (showOrderSentDialog) {
        AlertDialog(
            onDismissRequest = { showOrderSentDialog = false },
            containerColor   = ASurface,
            shape            = RoundedCornerShape(24.dp),
            icon = {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(AGreenDim),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = AGreen, modifier = Modifier.size(40.dp))
                }
            },
            title = {
                Text(
                    "Comandă trimisă! 🛎️",
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color      = ATextPrimary,
                    textAlign  = TextAlign.Center
                )
            },
            text = {
                Text(
                    "Bucătăria a primit comanda pentru Masa $tableNumber. Poți continua să adaugi produse.",
                    fontSize   = 14.sp,
                    color      = ATextMuted,
                    textAlign  = TextAlign.Center,
                    lineHeight = 21.sp
                )
            },
            confirmButton = {
                Button(
                    onClick  = { showOrderSentDialog = false },
                    shape    = RoundedCornerShape(14.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = ABrand),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Super! 🙌", color = ATextPrimary, fontWeight = FontWeight.ExtraBold)
                }
            }
        )
    }

    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            containerColor   = ASurface,
            shape            = RoundedCornerShape(24.dp),
            icon = {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(ARedDim),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.ExitToApp, null, tint = ARed, modifier = Modifier.size(36.dp))
                }
            },
            title = {
                Text(
                    "Finalizezi vizita?",
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color      = ATextPrimary,
                    textAlign  = TextAlign.Center
                )
            },
            text = {
                Text(
                    "Masa $tableNumber va fi eliberată și sesiunea va fi închisă.",
                    fontSize   = 14.sp,
                    color      = ATextMuted,
                    textAlign  = TextAlign.Center,
                    lineHeight = 21.sp
                )
            },
            confirmButton = {
                Button(
                    onClick  = { showLeaveDialog = false; endSession() },
                    shape    = RoundedCornerShape(14.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = ARed),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isEndingSession) {
                        CircularProgressIndicator(
                            color       = ATextPrimary,
                            modifier    = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Da, plec", color = ATextPrimary, fontWeight = FontWeight.ExtraBold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveDialog = false }) {
                    Text("Rămân", color = ATextMuted)
                }
            }
        )
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xFF1A0B00), ABg)))
                    .statusBarsPadding()
            ) {
                // Status row
                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(AGlass)
                                .border(1.dp, AGlassBorder, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Lock, null, tint = ABrand, modifier = Modifier.size(14.dp))
                        }
                        Text("Sesiune Activă", fontSize = 13.sp, color = ATextMuted, fontWeight = FontWeight.Medium)
                    }

                    // Live cart badge
                    AnimatedVisibility(
                        visible = cartTotal > 0,
                        enter   = fadeIn() + scaleIn(),
                        exit    = fadeOut() + scaleOut()
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(ABrand)
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Text(
                                "$cartTotal prod · ${"%.2f".format(cartPrice)} RON",
                                fontSize   = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color      = ATextPrimary
                            )
                        }
                    }
                }

                // "Ești la Masa X" hero
                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .padding(bottom = 18.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.linearGradient(listOf(ABrand, Color(0xFFFF8C00)))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Restaurant, null,
                            tint     = ATextPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Column {
                        Text("Ești la", fontSize = 13.sp, color = ATextMuted)
                        Text(
                            "Masa $tableNumber",
                            fontSize      = 30.sp,
                            fontWeight    = FontWeight.ExtraBold,
                            color         = ATextPrimary,
                            letterSpacing = (-0.5).sp
                        )
                    }
                }
            }
        },
        containerColor = ABg
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {

            when (val state = menuState) {
                is MenuUiState.Loading -> Box(
                    modifier         = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color       = ABrand,
                        strokeWidth = 2.5.dp,
                        modifier    = Modifier.size(42.dp)
                    )
                }

                is MenuUiState.Error -> Box(
                    modifier         = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier            = Modifier.padding(32.dp)
                    ) {
                        Text("⚠️", fontSize = 44.sp)
                        Text(
                            "Eroare la meniu",
                            fontSize   = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color      = ATextPrimary
                        )
                        OutlinedButton(
                            onClick = { vm.loadMenu(restaurantId) },
                            shape   = RoundedCornerShape(12.dp),
                            border  = BorderStroke(1.dp, ABrand),
                            colors  = ButtonDefaults.outlinedButtonColors(contentColor = ABrand)
                        ) {
                            Text("Încearcă din nou")
                        }
                    }
                }

                is MenuUiState.Empty -> Box(
                    modifier         = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🍽️", fontSize = 44.sp)
                        Text(
                            "Meniu indisponibil",
                            fontSize   = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color      = ATextPrimary
                        )
                        Text(
                            "Restaurantul nu are produse momentan.",
                            fontSize  = 13.sp,
                            color     = ATextMuted,
                            textAlign = TextAlign.Center,
                            modifier  = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }

                is MenuUiState.Success -> {
                    val entries       = state.items
                    val allCategories = remember(entries) {
                        entries.map { it.category }.filter { it.isNotBlank() }.distinct()
                    }
                    val filtered = if (selectedCategory == "Toate") entries
                                   else entries.filter { it.category == selectedCategory }
                    val grouped  = remember(filtered) { filtered.groupBy { it.category } }

                    Column(modifier = Modifier.fillMaxSize()) {
                        // Category filter
                        LazyRow(
                            contentPadding        = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                ActiveSessionChip(
                                    label    = "🍽️ Toate",
                                    selected = selectedCategory == "Toate",
                                    onClick  = { selectedCategory = "Toate" }
                                )
                            }
                            items(allCategories) { cat ->
                                ActiveSessionChip(
                                    label    = "${sessionCategoryEmoji(cat)} $cat",
                                    selected = selectedCategory == cat,
                                    onClick  = { selectedCategory = cat }
                                )
                            }
                        }

                        LazyColumn(
                            modifier            = Modifier.fillMaxSize(),
                            contentPadding      = PaddingValues(
                                start  = 16.dp,
                                end    = 16.dp,
                                top    = 4.dp,
                                bottom = 210.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            grouped.forEach { (category, catItems) ->
                                if (selectedCategory == "Toate" && category.isNotBlank()) {
                                    item(key = "hdr_$category") {
                                        Row(
                                            verticalAlignment     = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier              = Modifier.padding(top = 10.dp, bottom = 2.dp)
                                        ) {
                                            Text(sessionCategoryEmoji(category), fontSize = 13.sp)
                                            Text(
                                                category.uppercase(),
                                                fontSize      = 11.sp,
                                                fontWeight    = FontWeight.ExtraBold,
                                                color         = ATextMuted,
                                                letterSpacing = 1.sp
                                            )
                                        }
                                    }
                                }
                                items(catItems, key = { "${category}_${it.name}" }) { entry ->
                                    ActiveSessionOrderableItem(
                                        entry      = entry,
                                        quantity   = cart.find { it.name == entry.name }?.quantity ?: 0,
                                        onIncrease = { increase(entry) },
                                        onDecrease = { decrease(entry) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Fixed bottom action bar ───────────────────────────────────────
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, ABg, ABg))
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(top = 28.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Primary CTA: Send order
                Button(
                    onClick  = placeOrder,
                    enabled  = cart.isNotEmpty() && !isPlacingOrder,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape    = RoundedCornerShape(16.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor         = ABrand,
                        disabledContainerColor = AGlass
                    )
                ) {
                    if (isPlacingOrder) {
                        CircularProgressIndicator(
                            color       = ATextPrimary,
                            modifier    = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Rounded.ShoppingCart, null, tint = ATextPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (cartTotal > 0)
                                "Trimite Comanda · ${"%.2f".format(cartPrice)} RON"
                            else
                                "Trimite Comanda",
                            fontSize   = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color      = if (cart.isNotEmpty()) ATextPrimary else ATextMuted
                        )
                    }
                }

                // Secondary row: Cleaner + Leave
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick  = callCleaner,
                        enabled  = !isCallingCleaner,
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape    = RoundedCornerShape(14.dp),
                        border   = BorderStroke(1.5.dp, AAmber),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = AAmber)
                    ) {
                        if (isCallingCleaner) {
                            CircularProgressIndicator(
                                color       = AAmber,
                                modifier    = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Curăţenie", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    OutlinedButton(
                        onClick  = { showLeaveDialog = true },
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape    = RoundedCornerShape(14.dp),
                        border   = BorderStroke(1.dp, ARed.copy(alpha = 0.5f)),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = ARed)
                    ) {
                        Icon(Icons.Rounded.ExitToApp, null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Pleacă", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

// ── Orderable menu item card ──────────────────────────────────────────────────

@Composable
private fun ActiveSessionOrderableItem(
    entry     : MenuEntry,
    quantity  : Int,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit
) {
    val inCart = quantity > 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ASurface)
            .border(
                width = if (inCart) 1.5.dp else 1.dp,
                color = if (inCart) ABrand.copy(alpha = 0.7f) else AGlassBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 14.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        // Item info
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Text(
                entry.name,
                fontSize   = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color      = ATextPrimary,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
            if (entry.category.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(entry.category, fontSize = 12.sp, color = ABrand)
            }
            if (entry.description.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    entry.description,
                    fontSize   = 12.sp,
                    color      = ATextMuted,
                    maxLines   = 2,
                    overflow   = TextOverflow.Ellipsis,
                    lineHeight = 17.sp
                )
            }
            if (inCart && entry.price > 0.0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Subtotal: ${"%.2f".format(entry.price * quantity)} RON",
                    fontSize   = 11.sp,
                    color      = ABrand,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Price badge + stepper
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (entry.price > 0.0) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (inCart) ABrand else ABrandDim)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        "${"%.0f".format(entry.price)} RON",
                        fontSize   = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color      = ATextPrimary
                    )
                }
            }

            if (quantity > 0) {
                Box(
                    modifier         = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(AGlass)
                        .border(1.dp, AGlassBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = onDecrease, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Rounded.Remove, null, tint = ATextPrimary, modifier = Modifier.size(14.dp))
                    }
                }
                Text(
                    quantity.toString(),
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color      = ABrand,
                    modifier   = Modifier.widthIn(min = 22.dp),
                    textAlign  = TextAlign.Center
                )
            }
            Box(
                modifier         = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(ABrand),
                contentAlignment = Alignment.Center
            ) {
                IconButton(onClick = onIncrease, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Rounded.Add, null, tint = ATextPrimary, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

// ── Filter chip ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActiveSessionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick  = onClick,
        label    = { Text(label, fontSize = 12.sp) },
        shape    = RoundedCornerShape(20.dp),
        colors   = FilterChipDefaults.filterChipColors(
            selectedContainerColor = ABrand,
            selectedLabelColor     = ATextPrimary,
            containerColor         = AGlass,
            labelColor             = ATextMuted
        ),
        border   = FilterChipDefaults.filterChipBorder(
            borderColor         = AGlassBorder,
            selectedBorderColor = ABrand,
            borderWidth         = 1.dp,
            selectedBorderWidth = 1.5.dp
        )
    )
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun sessionCategoryEmoji(category: String) = when (category.trim().lowercase()) {
    "pizza"                -> "🍕"
    "paste"                -> "🍝"
    "carne", "grătar"      -> "🥩"
    "pește"                -> "🐟"
    "salate"               -> "🥙"
    "supe"                 -> "🍲"
    "desert", "deserturi"  -> "🍮"
    "băuturi"              -> "🍹"
    "burgeri"              -> "🍔"
    "garnituri"            -> "🍟"
    "cafea"                -> "☕"
    else                   -> "🍽️"
}
