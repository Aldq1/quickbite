package com.example.quickbite.android.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ExitToApp
import androidx.compose.material.icons.rounded.Search
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ── Palette ───────────────────────────────────────────────────────────────────

private val FBg          = Color(0xFF0C0C0C)
private val FSurface     = Color(0xFF161616)
private val FGlass       = Color(0x14FFFFFF)
private val FGlassBorder = Color(0x1AFFFFFF)
private val FBrand       = Color(0xFFE8430A)
private val FBrandGlow   = Color(0x1AE8430A)
private val FTextPrimary = Color(0xFFFFFFFF)
private val FTextMuted   = Color(0xFF9A9A9A)
private val FGreen       = Color(0xFF30D158)
private val FGreenDim    = Color(0x2030D158)
private val FRed         = Color(0xFFFF3B30)
private val FRedDim      = Color(0x33FF3B30)

// ── Public data model ─────────────────────────────────────────────────────────

data class Restaurant(
    val id: String = "",
    val name: String = "",
    val status: String = "open",
    val rating: Double = 0.0,
    val imageUrl: String = "",
    val cuisine: String = "",
    val ratingCount: Int = 0,
    val deliveryTime: String = "15–30 min",
    val priceRange: String = "RON ··",
    val emoji: String = "🍽️",
    val tags: List<String> = emptyList()
) {
    val isOpen: Boolean get() = status.lowercase() in setOf("open", "deschis")
}

// ── Banner gradients (cycled by card index) ───────────────────────────────────

private val BANNER_GRADIENTS = listOf(
    Pair(Color(0xFF3D1200), Color(0xFF1A0800)),
    Pair(Color(0xFF332700), Color(0xFF1A1400)),
    Pair(Color(0xFF0A2A0E), Color(0xFF051408)),
    Pair(Color(0xFF1A003A), Color(0xFF0D001D)),
    Pair(Color(0xFF00203A), Color(0xFF001020)),
)

private fun bannerGradient(index: Int) = BANNER_GRADIENTS[index % BANNER_GRADIENTS.size]

// ── ViewModel ─────────────────────────────────────────────────────────────────

class RestaurantFeedViewModel : ViewModel() {

    private val _restaurants = MutableStateFlow<List<Restaurant>>(emptyList())
    val restaurants: StateFlow<List<Restaurant>> = _restaurants.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var listener: ListenerRegistration? = null

    init {
        attachListener()
    }

    private fun attachListener() {
        listener = FirebaseFirestore.getInstance()
            .collection("restaurants")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    _isLoading.value = false
                    return@addSnapshotListener
                }
                _restaurants.value = snapshot.documents.mapNotNull { doc ->
                    val name = doc.getString("name") ?: return@mapNotNull null
                    Restaurant(
                        id           = doc.id,
                        name         = name,
                        status       = doc.getString("status") ?: "open",
                        rating       = doc.getDouble("rating") ?: 0.0,
                        imageUrl     = doc.getString("imageUrl") ?: "",
                        cuisine      = doc.getString("cuisine") ?: "",
                        ratingCount  = (doc.getLong("ratingCount") ?: 0L).toInt(),
                        deliveryTime = doc.getString("deliveryTime") ?: "15–30 min",
                        priceRange   = doc.getString("priceRange") ?: "RON ··",
                        emoji        = doc.getString("emoji") ?: "🍽️",
                        tags         = (doc.get("tags") as? List<*>)
                                           ?.filterIsInstance<String>() ?: emptyList()
                    )
                }
                _isLoading.value = false
            }
    }

    override fun onCleared() {
        super.onCleared()
        listener?.remove()
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun RestaurantFeedScreen(
    onRestaurantClick: (restaurantId: String, tableNumber: Int) -> Unit,
    onSignOut: () -> Unit,
    vm: RestaurantFeedViewModel = viewModel()
) {
    val restaurants by vm.restaurants.collectAsStateWithLifecycle()
    val isLoading   by vm.isLoading.collectAsStateWithLifecycle()

    Scaffold(
        topBar         = { FeedTopBar(onSignOut = onSignOut) },
        containerColor = FBg
    ) { padding ->
        when {
            isLoading -> Box(
                modifier         = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color       = FBrand,
                    modifier    = Modifier.size(40.dp),
                    strokeWidth = 3.dp
                )
            }

            restaurants.isEmpty() -> FeedEmptyState(
                modifier = Modifier.fillMaxSize().padding(padding)
            )

            else -> LazyColumn(
                modifier       = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(
                    start = 18.dp, end = 18.dp, top = 8.dp, bottom = 40.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { PromoBanner() }

                item {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Restaurante disponibile",
                        fontSize      = 18.sp,
                        fontWeight    = FontWeight.ExtraBold,
                        color         = FTextPrimary,
                        letterSpacing = (-0.3).sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${restaurants.count { it.isOpen }} deschise acum",
                        fontSize = 13.sp,
                        color    = FTextMuted
                    )
                }

                itemsIndexed(restaurants, key = { _, r -> r.id }) { index, restaurant ->
                    RestaurantCard(
                        restaurant = restaurant,
                        index      = index,
                        onClick    = { onRestaurantClick(restaurant.id, 1) }
                    )
                }
            }
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun FeedEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier            = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🍽️", fontSize = 56.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            "Niciun restaurant disponibil",
            fontSize   = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color      = FTextPrimary,
            textAlign  = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Restaurantele vor apărea\ncând sunt adăugate în aplicație.",
            fontSize   = 14.sp,
            color      = FTextMuted,
            textAlign  = TextAlign.Center,
            lineHeight = 21.sp
        )
    }
}

// ── Top bar ───────────────────────────────────────────────────────────────────

@Composable
private fun FeedTopBar(onSignOut: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(FBg)
            .statusBarsPadding()
            .padding(horizontal = 18.dp)
            .padding(top = 14.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column {
                Text("Bun venit! 👋", fontSize = 13.sp, color = FTextMuted)
                Text(
                    "QuickBite",
                    fontSize      = 26.sp,
                    fontWeight    = FontWeight.ExtraBold,
                    color         = FTextPrimary,
                    letterSpacing = (-0.5).sp
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(FGlass, CircleShape)
                    .border(1.dp, FGlassBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                IconButton(onClick = onSignOut, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Rounded.ExitToApp,
                        contentDescription = "Deconectare",
                        tint     = FTextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(FGlass)
                .border(1.dp, FGlassBorder, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Rounded.Search,
                contentDescription = null,
                tint     = FTextMuted,
                modifier = Modifier.size(18.dp)
            )
            Text("Caută restaurant sau bucătărie...", fontSize = 14.sp, color = FTextMuted)
        }
    }
}

// ── Promo banner ──────────────────────────────────────────────────────────────

@Composable
private fun PromoBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF2A0E00), Color(0xFF1A0A00), Color(0xFF111111))
                )
            )
            .border(1.dp, FBrand.copy(alpha = 0.28f), RoundedCornerShape(20.dp))
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(FBrand)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        "OFERTĂ LIMITATĂ",
                        fontSize      = 9.sp,
                        fontWeight    = FontWeight.ExtraBold,
                        color         = Color.White,
                        letterSpacing = 1.sp
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Prima comandă\nfără comision!",
                    fontSize      = 18.sp,
                    fontWeight    = FontWeight.ExtraBold,
                    color         = FTextPrimary,
                    lineHeight    = 24.sp,
                    letterSpacing = (-0.3).sp
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    "Comandă acum și bucură-te de avantajele QuickBite.",
                    fontSize   = 12.sp,
                    color      = FTextMuted,
                    lineHeight = 18.sp
                )
            }
            Spacer(Modifier.width(12.dp))
            Text("🎉", fontSize = 46.sp)
        }
    }
}

// ── Restaurant card ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RestaurantCard(
    restaurant: Restaurant,
    index: Int,
    onClick: () -> Unit
) {
    val (bannerStart, bannerEnd) = remember(index) { bannerGradient(index) }
    val canTap = restaurant.isOpen

    ElevatedCard(
        onClick   = { if (canTap) onClick() },
        enabled   = canTap,
        shape     = RoundedCornerShape(24.dp),
        colors    = CardDefaults.elevatedCardColors(containerColor = FSurface),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation  = 10.dp,
            pressedElevation  = 18.dp,
            disabledElevation = 2.dp
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // ── Banner ────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(155.dp)
                    .background(Brush.linearGradient(listOf(bannerStart, bannerEnd)))
            ) {
                Text(
                    text     = restaurant.emoji,
                    fontSize = 62.sp,
                    modifier = Modifier.align(Alignment.Center)
                )

                val statusColor = if (restaurant.isOpen) FGreen else FRed
                val statusDim   = if (restaurant.isOpen) FGreenDim else FRedDim
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusDim)
                        .border(1.dp, statusColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text       = if (restaurant.isOpen) "● Deschis" else "● Închis",
                        fontSize   = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color      = statusColor
                    )
                }

                if (!restaurant.isOpen) {
                    Box(
                        modifier         = Modifier.fillMaxSize().background(Color(0x80000000)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(FGlass)
                                .border(1.dp, FGlassBorder, RoundedCornerShape(12.dp))
                                .padding(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            Text(
                                "Momentan închis",
                                fontSize   = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color      = FTextPrimary
                            )
                        }
                    }
                }
            }

            // ── Info area ─────────────────────────────────────────────────
            Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {

                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.Top
                ) {
                    Column(modifier = Modifier
                        .weight(1f)
                        .padding(end = 10.dp)
                    ) {
                        Text(
                            text          = restaurant.name,
                            fontSize      = 19.sp,
                            fontWeight    = FontWeight.ExtraBold,
                            color         = FTextPrimary,
                            letterSpacing = (-0.3).sp
                        )
                        if (restaurant.cuisine.isNotBlank()) {
                            Spacer(Modifier.height(3.dp))
                            Text(restaurant.cuisine, fontSize = 13.sp, color = FTextMuted)
                        }
                    }
                    if (restaurant.rating > 0.0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(FBrandGlow)
                                .border(1.dp, FBrand.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("⭐", fontSize = 11.sp)
                                Text(
                                    "%.1f".format(restaurant.rating),
                                    fontSize   = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color      = FTextPrimary
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Divider(color = FGlassBorder, thickness = 1.dp)
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (restaurant.ratingCount > 0)
                        StatPill("💬", "${restaurant.ratingCount} recenzii")
                    if (restaurant.deliveryTime.isNotBlank())
                        StatPill("⏱", restaurant.deliveryTime)
                    if (restaurant.priceRange.isNotBlank())
                        StatPill("💳", restaurant.priceRange)
                }

                if (restaurant.tags.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        restaurant.tags.forEach { tag ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(FGlassBorder)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    tag,
                                    fontSize   = 11.sp,
                                    color      = FTextMuted,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                if (canTap) {
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick  = onClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = FBrand)
                    ) {
                        Text(
                            "Comandă acum",
                            fontSize   = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color      = Color.White
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Rounded.ArrowForward,
                            contentDescription = null,
                            tint     = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

// ── Stat pill ─────────────────────────────────────────────────────────────────

@Composable
private fun StatPill(icon: String, label: String) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(icon, fontSize = 12.sp)
        Text(label, fontSize = 12.sp, color = FTextMuted, fontWeight = FontWeight.Medium)
    }
}
