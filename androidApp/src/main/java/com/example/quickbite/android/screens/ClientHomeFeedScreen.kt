package com.example.quickbite.android.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ── Palette ───────────────────────────────────────────────────────────────────

private val HBg          = Color(0xFF0C0C0C)
private val HSurface     = Color(0xFF161616)
private val HGlass       = Color(0x14FFFFFF)
private val HGlassBorder = Color(0x1AFFFFFF)
private val HBrand       = Color(0xFFE8430A)
private val HBrandGlow   = Color(0x1AE8430A)
private val HTextPrimary = Color(0xFFFFFFFF)
private val HTextMuted   = Color(0xFF9A9A9A)
private val HGreen       = Color(0xFF30D158)
private val HGreenDim    = Color(0x2030D158)
private val HRed         = Color(0xFFFF3B30)
private val HRedDim      = Color(0x33FF3B30)

// ── Data model ────────────────────────────────────────────────────────────────

data class SpecialOffer(
    val id              : String = "",
    val title           : String = "",
    val description     : String = "",
    val discountPercent : Int    = 0,
    val restaurantName  : String = "",
    val emoji           : String = "🔥"
)

// ── Repository — offline-first via Firestore's built-in disk cache ─────────────
// Firestore caches the last known snapshot to disk automatically; these listeners
// will serve that cache instantly while re-fetching over the network in the background,
// so the UI never crashes or shows empty state when connectivity is lost.

class FeedRepository {

    private val db  = FirebaseFirestore.getInstance()
    private val uid = FirebaseAuth.getInstance().currentUser?.uid

    fun attachUserNameListener(onResult: (String) -> Unit): ListenerRegistration? {
        uid ?: return null
        return db.collection("users").document(uid)
            .addSnapshotListener { snap, _ ->
                onResult(
                    snap?.getString("name")
                        ?: snap?.getString("displayName")
                        ?: "Utilizator"
                )
            }
    }

    fun attachOffersListener(onResult: (List<SpecialOffer>) -> Unit): ListenerRegistration =
        db.collection("offers")
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                onResult(snap.documents.mapNotNull { doc ->
                    SpecialOffer(
                        id              = doc.id,
                        title           = doc.getString("title") ?: return@mapNotNull null,
                        description     = doc.getString("description") ?: "",
                        discountPercent = (doc.getLong("discountPercent") ?: 0L).toInt(),
                        restaurantName  = doc.getString("restaurantName") ?: "",
                        emoji           = doc.getString("emoji") ?: "🔥"
                    )
                })
            }

    // Restaurant identity lives in users/{uid} (written by the Desktop auth/KYC flow).
    // Only users with role=="Restaurant" and kyc_status=="APPROVED" are shown to clients.
    fun attachApprovedRestaurantsListener(onResult: (List<Restaurant>) -> Unit): ListenerRegistration =
        db.collection("users")
            .whereEqualTo("role", "Restaurant")
            .whereEqualTo("kyc_status", "APPROVED")
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                onResult(snap.documents.mapNotNull { doc ->
                    // companyName is the restaurant's trading name; fall back to name (manager name)
                    val displayName = doc.getString("companyName")
                        ?.takeIf { it.isNotBlank() }
                        ?: doc.getString("name")
                        ?: return@mapNotNull null
                    Restaurant(
                        id           = doc.id,
                        name         = displayName,
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
                })
            }
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

class ClientHomeFeedViewModel : ViewModel() {

    private val repo = FeedRepository()

    private val _userName    = MutableStateFlow("Utilizator")
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _offers      = MutableStateFlow<List<SpecialOffer>>(emptyList())
    val offers: StateFlow<List<SpecialOffer>> = _offers.asStateFlow()

    private val _restaurants = MutableStateFlow<List<Restaurant>>(emptyList())
    val restaurants: StateFlow<List<Restaurant>> = _restaurants.asStateFlow()

    private val _isLoading   = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val listeners = mutableListOf<ListenerRegistration>()

    init {
        repo.attachUserNameListener { _userName.value = it }?.let { listeners += it }
        listeners += repo.attachOffersListener { _offers.value = it }
        listeners += repo.attachApprovedRestaurantsListener {
            _restaurants.value = it
            _isLoading.value   = false
        }
    }

    override fun onCleared() {
        super.onCleared()
        listeners.forEach { it.remove() }
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun ClientHomeFeedScreen(
    onRestaurantClick : (restaurantId: String, tableNumber: Int) -> Unit,
    onQrScanClick     : () -> Unit,
    onProfileClick    : () -> Unit,
    vm                : ClientHomeFeedViewModel = viewModel()
) {
    val userName    by vm.userName.collectAsStateWithLifecycle()
    val offers      by vm.offers.collectAsStateWithLifecycle()
    val restaurants by vm.restaurants.collectAsStateWithLifecycle()
    val isLoading   by vm.isLoading.collectAsStateWithLifecycle()

    Scaffold(
        topBar         = { HomeFeedTopBar(userName = userName, onProfileClick = onProfileClick) },
        floatingActionButton = {
            FloatingActionButton(
                onClick           = onQrScanClick,
                shape             = androidx.compose.foundation.shape.CircleShape,
                containerColor    = HBrand,
                contentColor      = Color.White
            ) {
                Icon(Icons.Rounded.QrCode, contentDescription = "Scanează QR")
            }
        },
        containerColor = HBg
    ) { padding ->
        if (isLoading) {
            Box(
                modifier         = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = HBrand, modifier = Modifier.size(40.dp), strokeWidth = 3.dp)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier            = Modifier.fillMaxSize().padding(padding),
            contentPadding      = PaddingValues(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // ── Oferte Speciale ────────────────────────────────────────────
            if (offers.isNotEmpty()) {
                item(key = "section_offers") {
                    FeedSectionHeader(
                        title    = "Oferte Speciale",
                        subtitle = "${offers.size} oferte active",
                        emoji    = "🔥",
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
                    )
                }
                item(key = "offers_row") {
                    LazyRow(
                        contentPadding        = PaddingValues(horizontal = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(offers, key = { it.id }) { offer ->
                            SpecialOfferCard(offer = offer)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }

            // ── Restaurante ────────────────────────────────────────────────
            item(key = "section_restaurants") {
                FeedSectionHeader(
                    title    = "Restaurante",
                    subtitle = "${restaurants.count { it.isOpen }} deschise acum",
                    emoji    = "🍽️",
                    modifier = Modifier
                        .padding(horizontal = 18.dp)
                        .padding(top = 14.dp, bottom = 8.dp)
                )
            }

            if (restaurants.isEmpty()) {
                item(key = "empty") {
                    HomeFeedEmptyState(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 28.dp)
                    )
                }
            } else {
                itemsIndexed(restaurants, key = { _, r -> r.id }) { index, restaurant ->
                    HomeFeedRestaurantCard(
                        restaurant = restaurant,
                        index      = index,
                        onClick    = { onRestaurantClick(restaurant.id, 1) },
                        modifier   = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

// ── Top bar ───────────────────────────────────────────────────────────────────

@Composable
private fun HomeFeedTopBar(userName: String, onProfileClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(HBg)
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
                Text("Bun venit, $userName! 👋", fontSize = 13.sp, color = HTextMuted)
                Text(
                    "QuickBite",
                    fontSize      = 26.sp,
                    fontWeight    = FontWeight.ExtraBold,
                    color         = HTextPrimary,
                    letterSpacing = (-0.5).sp
                )
            }
            // Profile avatar — initial of name, navigates to ProfileSettingsScreen
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(HGlass)
                    .border(1.dp, HGlassBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                IconButton(onClick = onProfileClick, modifier = Modifier.size(42.dp)) {
                    Text(
                        userName.take(1).uppercase().ifBlank { "Q" },
                        fontSize   = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color      = HBrand
                    )
                }
            }
        }

        // Search bar (visual — extend with ViewModel search state when needed)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(HGlass)
                .border(1.dp, HGlassBorder, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Rounded.Search, null, tint = HTextMuted, modifier = Modifier.size(18.dp))
            Text("Caută restaurant sau bucătărie...", fontSize = 14.sp, color = HTextMuted)
        }
    }
}

// ── Section header ────────────────────────────────────────────────────────────

@Composable
private fun FeedSectionHeader(
    title   : String,
    subtitle: String,
    emoji   : String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier              = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(emoji, fontSize = 18.sp)
            Text(
                title,
                fontSize      = 18.sp,
                fontWeight    = FontWeight.ExtraBold,
                color         = HTextPrimary,
                letterSpacing = (-0.3).sp
            )
        }
        Text(subtitle, fontSize = 12.sp, color = HTextMuted)
    }
}

// ── Special offer card ────────────────────────────────────────────────────────

private val OFFER_GRADIENTS = listOf(
    Pair(Color(0xFF3D1200), Color(0xFF1A0800)),
    Pair(Color(0xFF001A3A), Color(0xFF00101F)),
    Pair(Color(0xFF1A003A), Color(0xFF0D001D)),
    Pair(Color(0xFF002A0E), Color(0xFF001508)),
    Pair(Color(0xFF2A2000), Color(0xFF151000)),
)

@Composable
private fun SpecialOfferCard(offer: SpecialOffer) {
    val (gs, ge) = OFFER_GRADIENTS[offer.id.hashCode().and(0x7FFFFFFF) % OFFER_GRADIENTS.size]

    ElevatedCard(
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.elevatedCardColors(containerColor = HSurface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp),
        modifier  = Modifier.width(210.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .background(Brush.linearGradient(listOf(gs, ge))),
                contentAlignment = Alignment.Center
            ) {
                Text(offer.emoji, fontSize = 34.sp)
                if (offer.discountPercent > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(HBrand)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            "-${offer.discountPercent}%",
                            fontSize   = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color      = Color.White
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    offer.title,
                    fontSize      = 14.sp,
                    fontWeight    = FontWeight.ExtraBold,
                    color         = HTextPrimary,
                    maxLines      = 1,
                    overflow      = TextOverflow.Ellipsis,
                    letterSpacing = (-0.2).sp
                )
                if (offer.description.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        offer.description,
                        fontSize   = 12.sp,
                        color      = HTextMuted,
                        maxLines   = 2,
                        overflow   = TextOverflow.Ellipsis,
                        lineHeight = 17.sp
                    )
                }
                if (offer.restaurantName.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Rounded.LocationOn, null, tint = HBrand, modifier = Modifier.size(11.dp))
                        Text(offer.restaurantName, fontSize = 11.sp, color = HBrand, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

// ── Restaurant card ───────────────────────────────────────────────────────────

private val HFEED_BANNER_GRADIENTS = listOf(
    Pair(Color(0xFF3D1200), Color(0xFF1A0800)),
    Pair(Color(0xFF332700), Color(0xFF1A1400)),
    Pair(Color(0xFF0A2A0E), Color(0xFF051408)),
    Pair(Color(0xFF1A003A), Color(0xFF0D001D)),
    Pair(Color(0xFF00203A), Color(0xFF001020)),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeFeedRestaurantCard(
    restaurant: Restaurant,
    index     : Int,
    onClick   : () -> Unit,
    modifier  : Modifier = Modifier
) {
    val (bannerStart, bannerEnd) = HFEED_BANNER_GRADIENTS[index % HFEED_BANNER_GRADIENTS.size]
    val canTap = restaurant.isOpen

    ElevatedCard(
        onClick   = { if (canTap) onClick() },
        enabled   = canTap,
        shape     = RoundedCornerShape(24.dp),
        colors    = CardDefaults.elevatedCardColors(containerColor = HSurface),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation  = 10.dp,
            pressedElevation  = 18.dp,
            disabledElevation = 2.dp
        ),
        modifier  = modifier.fillMaxWidth()
    ) {
        Column {
            // Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .background(Brush.linearGradient(listOf(bannerStart, bannerEnd)))
            ) {
                Text(restaurant.emoji, fontSize = 52.sp, modifier = Modifier.align(Alignment.Center))

                val statusColor = if (restaurant.isOpen) HGreen else HRed
                val statusDim   = if (restaurant.isOpen) HGreenDim else HRedDim
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusDim)
                        .border(1.dp, statusColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        if (restaurant.isOpen) "● Deschis" else "● Închis",
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
                                .background(HGlass)
                                .border(1.dp, HGlassBorder, RoundedCornerShape(12.dp))
                                .padding(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            Text(
                                "Momentan închis",
                                fontSize   = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color      = HTextPrimary
                            )
                        }
                    }
                }
            }

            // Info
            Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                        Text(
                            restaurant.name,
                            fontSize      = 18.sp,
                            fontWeight    = FontWeight.ExtraBold,
                            color         = HTextPrimary,
                            letterSpacing = (-0.3).sp
                        )
                        if (restaurant.cuisine.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(restaurant.cuisine, fontSize = 13.sp, color = HTextMuted)
                        }
                    }
                    if (restaurant.rating > 0.0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(HBrandGlow)
                                .border(1.dp, HBrand.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
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
                                    color      = HTextPrimary
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Divider(color = HGlassBorder, thickness = 1.dp)
                Spacer(Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (restaurant.ratingCount > 0)
                        HFeedStatPill("💬", "${restaurant.ratingCount}")
                    if (restaurant.deliveryTime.isNotBlank())
                        HFeedStatPill("⏱", restaurant.deliveryTime)
                    if (restaurant.priceRange.isNotBlank())
                        HFeedStatPill("💳", restaurant.priceRange)
                }

                if (restaurant.tags.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        restaurant.tags.take(3).forEach { tag ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(HGlassBorder)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(tag, fontSize = 11.sp, color = HTextMuted, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }

                if (canTap) {
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick  = onClick,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = HBrand)
                    ) {
                        Text(
                            "Comandă acum",
                            fontSize   = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color      = Color.White
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Rounded.ArrowForward, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

// ── Stat pill ─────────────────────────────────────────────────────────────────

@Composable
private fun HFeedStatPill(icon: String, label: String) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(icon, fontSize = 12.sp)
        Text(label, fontSize = 12.sp, color = HTextMuted, fontWeight = FontWeight.Medium)
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun HomeFeedEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier            = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("🍽️", fontSize = 52.sp, textAlign = TextAlign.Center)
        Text(
            "Niciun restaurant disponibil",
            fontSize   = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            color      = HTextPrimary,
            textAlign  = TextAlign.Center
        )
        Text(
            "Restaurantele vor apărea\ndupă aprobarea KYC.",
            fontSize   = 13.sp,
            color      = HTextMuted,
            textAlign  = TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}
