package com.example.quickbite.android.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ExitToApp
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

// ── Dark / glass colour palette ───────────────────────────────────────────────

private val DarkBg        = Color(0xFF0C0C0C)
private val DarkSurface   = Color(0xFF161616)
private val GlassWhite    = Color(0x12FFFFFF)  // 7 % white — card fill
private val GlassBorder   = Color(0x1AFFFFFF)  // 10 % white — card stroke
private val GlassDivider  = Color(0x0AFFFFFF)  // 4 % white — ingredient divider
private val Brand         = Color(0xFFE8430A)
private val BrandDim      = Color(0x33E8430A)  // 20 % orange glow
private val TextPrimary   = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFF9A9A9A)
private val White         = Color(0xFFFFFFFF)

// Converts a Firebase Task to a suspend function, safely cancellable within coroutines
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resumeWith(Result.success(it)) }
    addOnFailureListener { cont.resumeWith(Result.failure(it)) }
}

// ── Data model ────────────────────────────────────────────────────────────────

private data class ClientMenuItem(
    val product: String,
    val category: String,
    val ingredients: List<Map<String, String>>,
    val restaurantId: String,
    val restaurantName: String = "",
    val price: Double = 0.0
)

private sealed interface DashboardUiState {
    object Loading : DashboardUiState
    object Empty : DashboardUiState
    data class Success(val items: List<ClientMenuItem>) : DashboardUiState
}

private data class ActiveSession(val restaurantId: String, val tableNumber: Int)

// ── Demo / offline mock data ──────────────────────────────────────────────────

internal const val DEMO_RESTAURANT_ID = "7QBG68DH1bciyywUTt1klkoyzwv2"

private val DEMO_CLIENT_ITEMS = listOf(
    ClientMenuItem("Burger QuickBite Epic",       "Burgeri",
        listOf(mapOf("name" to "Carne vită", "weight" to "200g"), mapOf("name" to "Cheddar aged", "weight" to "40g"), mapOf("name" to "Bacon crispy", "weight" to "30g"), mapOf("name" to "Sos special QB")),
        DEMO_RESTAURANT_ID, "QuickBite Central", 42.00),
    ClientMenuItem("Pizza Quattro Formaggi",      "Pizza",
        listOf(mapOf("name" to "Mozzarella", "weight" to "100g"), mapOf("name" to "Gorgonzola", "weight" to "40g"), mapOf("name" to "Parmezan", "weight" to "30g"), mapOf("name" to "Blat crocant pe vatră")),
        DEMO_RESTAURANT_ID, "QuickBite Central", 49.00),
    ClientMenuItem("Somon Gravlax cu Avocado",    "Pește",
        listOf(mapOf("name" to "File somon marinat", "weight" to "180g"), mapOf("name" to "Cremă avocado", "weight" to "60g"), mapOf("name" to "Capere & lemon zest")),
        DEMO_RESTAURANT_ID, "QuickBite Central", 56.00),
    ClientMenuItem("Cartofi cu Parmezan & Trufe", "Garnituri",
        listOf(mapOf("name" to "Cartofi belgieni", "weight" to "250g"), mapOf("name" to "Ulei de trufe", "weight" to "10ml"), mapOf("name" to "Rozmarin proaspăt")),
        DEMO_RESTAURANT_ID, "QuickBite Central", 18.00),
    ClientMenuItem("Limonadă cu Mentă",           "Băuturi",
        listOf(mapOf("name" to "Lămâie stoarsă", "weight" to "2 buc"), mapOf("name" to "Sirop mentă", "weight" to "30ml"), mapOf("name" to "Apă carbogazoasă 500ml")),
        DEMO_RESTAURANT_ID, "QuickBite Central", 15.00),
    ClientMenuItem("Tiramisu de Casă",            "Deserturi",
        listOf(mapOf("name" to "Mascarpone", "weight" to "120g"), mapOf("name" to "Espresso", "weight" to "60ml"), mapOf("name" to "Cacao Valrhona", "weight" to "15g")),
        DEMO_RESTAURANT_ID, "QuickBite Central", 22.00),
    ClientMenuItem("Sparanghel la Grătar",        "Intrări",
        listOf(mapOf("name" to "Sparanghel verde", "weight" to "200g"), mapOf("name" to "Sos hollandaise", "weight" to "50ml"), mapOf("name" to "Ou poché")),
        DEMO_RESTAURANT_ID, "QuickBite Central", 32.00),
    ClientMenuItem("Flat White Premium",          "Cafea",
        listOf(mapOf("name" to "Dublu espresso", "weight" to "60ml"), mapOf("name" to "Lapte oat texturat", "weight" to "150ml")),
        DEMO_RESTAURANT_ID, "QuickBite Central", 14.00),
)

// ── Root screen ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientDashboardScreen(
    onNavigateToOrdering  : (restaurantId: String, tableNumber: Int) -> Unit,
    onNavigateToQrScanner : () -> Unit,
    onSignOut             : () -> Unit
) {
    val currentUid       = remember { FirebaseAuth.getInstance().currentUser?.uid }
    var uiState          by remember { mutableStateOf<DashboardUiState>(DashboardUiState.Loading) }
    var restaurantIds    by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf("Toate") }
    var activeSession    by remember { mutableStateOf<ActiveSession?>(null) }
    var sessionChecked   by remember { mutableStateOf(true) }
    var isBanned         by remember { mutableStateOf(false) }
    var banChecked       by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            // Fetch and parse the full menu on the IO thread — never blocks the main thread
            val snapshot = withContext(Dispatchers.IO) {
                FirebaseFirestore.getInstance()
                    .collectionGroup("restaurant_menu")
                    .get()
                    .await()
            }

            val rawItems = withContext(Dispatchers.Default) {
                snapshot.documents.mapNotNull { doc ->
                    val product      = doc.getString("product")  ?: return@mapNotNull null
                    val category     = doc.getString("category") ?: ""
                    val restaurantId = doc.reference.parent.parent?.id ?: return@mapNotNull null
                    @Suppress("UNCHECKED_CAST")
                    val ingredients  = doc.get("ingredients") as? List<Map<String, String>> ?: emptyList()
                    val price        = doc.getDouble("price") ?: 0.0
                    ClientMenuItem(product, category, ingredients, restaurantId, price = price)
                }
            }

            val uniqueIds = rawItems.map { it.restaurantId }.distinct()
            restaurantIds = uniqueIds

            if (rawItems.isEmpty()) {
                // No Firestore data — fall back to demo content so the app is never empty
                restaurantIds = listOf(DEMO_RESTAURANT_ID)
                uiState = DashboardUiState.Success(DEMO_CLIENT_ITEMS)
                return@LaunchedEffect
            }

            // Fetch each restaurant's display name sequentially on IO; avoids pending-counter race
            val nameMap = mutableMapOf<String, String>()
            withContext(Dispatchers.IO) {
                uniqueIds.forEach { rid ->
                    nameMap[rid] = runCatching {
                        FirebaseFirestore.getInstance()
                            .collection("users").document(rid)
                            .collection("restaurant_profile").document("details")
                            .get()
                            .await()
                            .getString("restaurantName")
                            ?.takeIf { it.isNotBlank() }
                            ?: rid.take(8)
                    }.getOrElse { rid.take(8) }
                }
            }

            val enriched = rawItems.map { item ->
                item.copy(restaurantName = nameMap[item.restaurantId] ?: item.restaurantId.take(8))
            }
            uiState = DashboardUiState.Success(enriched)

        } catch (e: CancellationException) {
            throw e // always rethrow so structured concurrency is preserved
        } catch (_: Exception) {
            // Network/Firestore error — show demo content so the screen is never blank
            restaurantIds = listOf(DEMO_RESTAURANT_ID)
            uiState = DashboardUiState.Success(DEMO_CLIENT_ITEMS)
        }
    }

    // Real-time listener: fires instantly when the waiter marks the order done, unlocking the UI
    DisposableEffect(currentUid) {
        if (currentUid == null) {
            sessionChecked = true
            return@DisposableEffect onDispose {}
        }
        val reg = FirebaseFirestore.getInstance()
            .collection("orders")
            .whereEqualTo("occupantUid", currentUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                activeSession = snapshot?.documents
                    ?.firstOrNull { it.getString("status") == "PENDING" }
                    ?.let { doc ->
                        val rid = doc.getString("restaurantId") ?: return@let null
                        val tbl = doc.getLong("tableNumber")?.toInt() ?: return@let null
                        ActiveSession(rid, tbl)
                    }
            }
        onDispose { reg.remove() }
    }

    // Permanent ban check — document-level listener; resolves instantly from cache if offline
    DisposableEffect(currentUid) {
        if (currentUid == null) {
            banChecked = true
            return@DisposableEffect onDispose {}
        }
        val reg = FirebaseFirestore.getInstance()
            .collection("banned_users")
            .document(currentUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                isBanned = snapshot?.exists() == true
            }
        onDispose { reg.remove() }
    }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brand)
                    .statusBarsPadding()
                    .padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
            ) {
                Text(
                    text = "Descoperă Meniurile",
                    color = White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.3).sp,
                    modifier = Modifier.align(Alignment.CenterStart).padding(vertical = 8.dp)
                )
                IconButton(
                    onClick  = onSignOut,
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(Icons.Rounded.ExitToApp, contentDescription = "Deconectare", tint = White)
                }
            }
        },
        floatingActionButton = {
            if (sessionChecked && activeSession == null && banChecked && !isBanned) {
                FloatingActionButton(
                    onClick        = onNavigateToQrScanner,
                    containerColor = Brand,
                    contentColor   = White,
                    shape          = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Rounded.QrCodeScanner, contentDescription = "Scanează QR masă")
                }
            }
        },
        containerColor = DarkBg
    ) { padding ->
        if (!banChecked || !sessionChecked) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = Brand) }
        } else if (isBanned) {
            BannedScreen(modifier = Modifier.fillMaxSize().padding(padding))
        } else if (activeSession != null) {
            StickySessionScreen(
                session    = activeSession!!,
                onContinue = { onNavigateToOrdering(activeSession!!.restaurantId, activeSession!!.tableNumber) },
                modifier   = Modifier.fillMaxSize().padding(padding)
            )
        } else when (val state = uiState) {
            is DashboardUiState.Loading -> Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = Brand) }

            is DashboardUiState.Empty -> Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { EmptyMenuState() }

            is DashboardUiState.Success -> {
                val allCategories = remember(state.items) {
                    listOf("Toate") + state.items
                        .map { it.category }.filter { it.isNotBlank() }.distinct().sorted()
                }
                val filtered = if (selectedCategory == "Toate") state.items
                               else state.items.filter { it.category == selectedCategory }

                Column(modifier = Modifier.padding(padding).fillMaxSize()) {

                    // ── Dark header card ────────────────────────────────────
                    DarkHeaderCard(
                        restaurantCount = restaurantIds.size,
                        itemCount = state.items.size
                    )

                    // ── Glass category chips ────────────────────────────────
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(allCategories) { cat ->
                            GlassChip(
                                label    = cat,
                                selected = selectedCategory == cat,
                                onClick  = { selectedCategory = cat }
                            )
                        }
                    }

                    // ── Filtered menu list ──────────────────────────────────
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp, end = 16.dp, top = 0.dp, bottom = 96.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Text(
                                text = "${filtered.size} ${if (filtered.size == 1) "produs" else "produse"}",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                letterSpacing = 0.5.sp,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        items(filtered) { item -> GlassMenuItemCard(item = item) }
                    }
                }
            }
        }
    }

}

// ── Dark header card ──────────────────────────────────────────────────────────

@Composable
private fun DarkHeaderCard(restaurantCount: Int, itemCount: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF1E0A01), Color(0xFF141414))))
            .border(1.dp, Brand.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(BrandDim, shape = CircleShape)
                    .border(1.dp, Brand.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Restaurant,
                    contentDescription = null,
                    tint = Brand,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column {
                Text(
                    text = "Bun venit la QuickBite!",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary,
                    letterSpacing = (-0.3).sp
                )
                Text(
                    text = "$restaurantCount ${if (restaurantCount == 1) "restaurant" else "restaurante"} · $itemCount produse",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

// ── Glass chip ────────────────────────────────────────────────────────────────

@Composable
private fun GlassChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Brand else GlassWhite)
            .border(
                width = 1.dp,
                color = if (selected) Color.Transparent else GlassBorder,
                shape = RoundedCornerShape(20.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = TextPrimary,
            letterSpacing = 0.2.sp
        )
    }
}

// ── Glass menu item card ──────────────────────────────────────────────────────

@Composable
private fun GlassMenuItemCard(item: ClientMenuItem) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(GlassWhite)
            .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        // ── Top row: category chip + price badge ──────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (item.category.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(BrandDim)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = item.category.uppercase(),
                        color = Brand,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp
                    )
                }
            } else {
                Spacer(Modifier.width(1.dp))
            }

            if (item.price > 0.0) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Brand)
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "${"%.2f".format(item.price)} RON",
                        color = White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.3.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ── Product name ──────────────────────────────────────────────────────
        Text(
            text = item.product,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextPrimary,
            letterSpacing = (-0.5).sp
        )

        // ── Restaurant badge ──────────────────────────────────────────────────
        if (item.restaurantName.isNotBlank()) {
            Spacer(Modifier.height(5.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Restaurant,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = item.restaurantName,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // ── Ingredients ───────────────────────────────────────────────────────
        if (item.ingredients.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(GlassDivider))
            Spacer(Modifier.height(12.dp))
            Text(
                text = "INGREDIENTE",
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextSecondary,
                letterSpacing = 1.5.sp
            )
            Spacer(Modifier.height(8.dp))
            item.ingredients.forEach { ingredient ->
                val name   = ingredient["name"]   ?: return@forEach
                val weight = ingredient["weight"] ?: ""
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(modifier = Modifier.size(5.dp).background(Brand, CircleShape))
                        Text(
                            text = name,
                            fontSize = 13.sp,
                            color = TextPrimary.copy(alpha = 0.75f)
                        )
                    }
                    if (weight.isNotBlank()) {
                        Text(
                            text = weight,
                            fontSize = 13.sp,
                            color = Brand,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

// ── Banned screen ─────────────────────────────────────────────────────────────

private val BanRed        = Color(0xFFFF3B30)
private val BanRedDim     = Color(0x1AFF3B30)  // 10 % red — icon ring fill
private val BanRedBorder  = Color(0x40FF3B30)  // 25 % red — ring stroke
private val BanDarkBg     = Color(0xFF100A0A)  // near-black with red undertone
private val BanInfoBg     = Color(0x0DFF3B30)  // 5 % red — info card fill

@Composable
private fun BannedScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(BanDarkBg)
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(104.dp)
                .background(BanRedDim, CircleShape)
                .border(1.dp, BanRedBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Block,
                contentDescription = null,
                tint = BanRed,
                modifier = Modifier.size(52.dp)
            )
        }

        Spacer(Modifier.height(28.dp))

        Text(
            text = "CONT RESTRICȚIONAT",
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = BanRed,
            letterSpacing = 2.sp
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text = "Acces Blocat",
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            letterSpacing = (-0.5).sp
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Acest cont a fost suspendat din rețeaua QuickBite din cauza neplății unor comenzi anterioare.",
            fontSize = 15.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 23.sp
        )

        Spacer(Modifier.height(36.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(BanInfoBg)
                .border(1.dp, BanRedBorder, RoundedCornerShape(14.dp))
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Text(
                text = "Pentru contestații, contactați restaurantul sau suportul QuickBite.",
                fontSize = 13.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ── Sticky session screen ─────────────────────────────────────────────────────

@Composable
private fun StickySessionScreen(
    session: ActiveSession,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(DarkBg)
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(BrandDim, CircleShape)
                .border(1.dp, Brand.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Restaurant,
                contentDescription = null,
                tint = Brand,
                modifier = Modifier.size(46.dp)
            )
        }

        Spacer(Modifier.height(28.dp))

        Text(
            text = "SESIUNE ACTIVĂ",
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Brand,
            letterSpacing = 2.sp
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text = "Ești la Masa ${session.tableNumber}",
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            letterSpacing = (-0.5).sp
        )

        Spacer(Modifier.height(14.dp))

        Text(
            text = "Ai o comandă activă. Adaugă produse sau urmărește statusul comenzii — scanarea QR pentru altă masă este blocată până când chelnerul îți eliberează masa.",
            fontSize = 14.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(Modifier.height(44.dp))

        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Brand)
        ) {
            Text(
                text = "Continuă Comanda",
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = White
            )
            Spacer(Modifier.width(10.dp))
            Icon(
                imageVector = Icons.Rounded.ArrowForward,
                contentDescription = null,
                tint = White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyMenuState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 32.dp)
    ) {
        Text("🍽️", fontSize = 52.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Niciun produs disponibil",
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextPrimary
        )
        Text(
            text = "Restaurantele nu au adăugat încă\nproduse în meniu.",
            fontSize = 14.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

