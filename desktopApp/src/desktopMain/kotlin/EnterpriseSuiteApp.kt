// ─────────────────────────────────────────────────────────────────────────────
// EnterpriseSuiteApp.kt  —  QuickBite Enterprise Suite
// Drop into desktopApp/src/desktopMain/kotlin/ (no package declaration needed).
//
// To open as a standalone window, add to Main.kt:
//   Window(title = "QuickBite Enterprise Suite", state = WindowState(size = DpSize(1360.dp, 860.dp))) {
//       EnterpriseSuiteApp(db = db)
//   }
// ─────────────────────────────────────────────────────────────────────────────

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.cloud.firestore.Firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

// ── Palette ───────────────────────────────────────────────────────────────────

private val EBg          = Color(0xFF080912)
private val ESurface     = Color(0xFF10111E)
private val ESurface2    = Color(0xFF181928)
private val EGlass       = Color(0x0DFFFFFF)
private val EGlassBorder = Color(0x18FFFFFF)
private val EBrand       = Color(0xFF6C7BFF)   // enterprise indigo
private val EBrandDim    = Color(0x1A6C7BFF)
private val EBrandTint   = Color(0x336C7BFF)
private val EOrange      = Color(0xFFE8430A)   // QuickBite brand orange
private val EOrangeDim   = Color(0x1AE8430A)
private val EGreen       = Color(0xFF34C759)
private val EGreenDim    = Color(0x2034C759)
private val EAmber       = Color(0xFFFF9F0A)
private val EAmberDim    = Color(0x20FF9F0A)
private val ETextPrimary = Color(0xFFFFFFFF)
private val ETextMuted   = Color(0xFF9090A8)
private val ETextDim     = Color(0xFF5A5A70)

// ── Material 3 dark colour scheme ─────────────────────────────────────────────

private val EnterpriseColorScheme = darkColorScheme(
    primary          = EBrand,
    onPrimary        = Color.White,
    primaryContainer = Color(0xFF2D3A7C),
    secondary        = EGreen,
    tertiary         = EAmber,
    background       = EBg,
    surface          = ESurface,
    surfaceVariant   = ESurface2,
    onBackground     = ETextPrimary,
    onSurface        = ETextPrimary,
    onSurfaceVariant = ETextMuted,
    outline          = Color(0xFF2A2A40),
    outlineVariant   = Color(0xFF1A1A28),
)

// ── Enums ─────────────────────────────────────────────────────────────────────

enum class AppRole { NONE, RESTAURANT_ADMIN, PRODUCER, CANDIDATE, KYC_AGENT }
private enum class AdminTab { OPERATIONAL, ACQUISITIONS, RECRUITMENT, MENU_MANAGER, TABLE_CONFIG }
private val MENU_CATEGORIES = listOf("Pizza", "Paste", "Carne", "Pește", "Salate", "Supe", "Desert", "Băuturi", "Altele")

// ── Data models ───────────────────────────────────────────────────────────────

private data class ProducerOffer(
    val id: String = "",
    val farmName: String = "",
    val product: String = "",
    val quantity: String = "",
    val price: String = "",
    val status: String = "available",
    val timestamp: Long = 0L
)

private data class HrApplication(
    val id: String = "",
    val fullName: String = "",
    val position: String = "",
    val experience: String = "",
    val status: String = "pending",
    val timestamp: Long = 0L
)

private data class MenuItem(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val price: Double = 0.0,
    val category: String = ""
)

private data class RestaurantTable(
    val id: String = "",
    val tableNumber: Int = 0,
    val capacity: Int = 0,
    val status: String = "LIBERA"
)

// ── Firestore real-time flows ─────────────────────────────────────────────────

private fun producersFlow(db: Firestore): Flow<List<ProducerOffer>> = callbackFlow {
    val reg = db.collection("producers")
        .addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            trySend(
                snapshot.documents.mapNotNull { doc ->
                    ProducerOffer(
                        id        = doc.id,
                        farmName  = doc.getString("farmName") ?: return@mapNotNull null,
                        product   = doc.getString("product") ?: "",
                        quantity  = doc.getString("quantity") ?: "",
                        price     = doc.getString("price") ?: "",
                        status    = doc.getString("status") ?: "available",
                        timestamp = doc.getLong("timestamp") ?: 0L
                    )
                }
            )
        }
    awaitClose { reg.remove() }
}

private fun hrCvsFlow(db: Firestore): Flow<List<HrApplication>> = callbackFlow {
    val reg = db.collection("hr_cvs")
        .addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            trySend(
                snapshot.documents.mapNotNull { doc ->
                    HrApplication(
                        id         = doc.id,
                        fullName   = doc.getString("fullName") ?: return@mapNotNull null,
                        position   = doc.getString("position") ?: "",
                        experience = doc.getString("experience") ?: "",
                        status     = doc.getString("status") ?: "pending",
                        timestamp  = doc.getLong("timestamp") ?: 0L
                    )
                }
            )
        }
    awaitClose { reg.remove() }
}

private fun menuItemsFlow(db: Firestore): Flow<List<MenuItem>> = callbackFlow {
    val reg = db.collection("menu")
        .addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            trySend(
                snapshot.documents.mapNotNull { doc ->
                    MenuItem(
                        id          = doc.id,
                        name        = doc.getString("name") ?: return@mapNotNull null,
                        description = doc.getString("description") ?: "",
                        price       = doc.getDouble("price") ?: 0.0,
                        category    = doc.getString("category") ?: ""
                    )
                }.sortedWith(compareBy({ it.category }, { it.name }))
            )
        }
    awaitClose { reg.remove() }
}

private fun tablesConfigFlow(db: Firestore): Flow<List<RestaurantTable>> = callbackFlow {
    val reg = db.collection("tables")
        .addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            trySend(
                snapshot.documents.mapNotNull { doc ->
                    RestaurantTable(
                        id          = doc.id,
                        tableNumber = doc.getLong("tableNumber")?.toInt() ?: return@mapNotNull null,
                        capacity    = doc.getLong("capacity")?.toInt() ?: 0,
                        status      = doc.getString("status") ?: "LIBERA"
                    )
                }.sortedBy { it.tableNumber }
            )
        }
    awaitClose { reg.remove() }
}

// ── Root composable ───────────────────────────────────────────────────────────

@Composable
fun EnterpriseSuiteApp(db: Firestore?) {
    var currentRole by remember { mutableStateOf(AppRole.NONE) }

    MaterialTheme(colorScheme = EnterpriseColorScheme) {
        AnimatedContent(
            targetState  = currentRole,
            transitionSpec = {
                (slideInHorizontally { it / 12 } + fadeIn(tween(220))) togetherWith
                (slideOutHorizontally { -it / 12 } + fadeOut(tween(160)))
            }
        ) { role ->
            when (role) {
                AppRole.NONE ->
                    AuthScreen(db = db, onAuthenticated = { currentRole = it })
                AppRole.RESTAURANT_ADMIN ->
                    RestaurantAdminPortal(db = db, onBack = { currentRole = AppRole.NONE })
                AppRole.PRODUCER ->
                    ProducerPortal(db = db, onBack = { currentRole = AppRole.NONE })
                AppRole.CANDIDATE ->
                    CandidatePortal(db = db, onBack = { currentRole = AppRole.NONE })
                AppRole.KYC_AGENT ->
                    KycAgentPanel(db = db, onBack = { currentRole = AppRole.NONE })
            }
        }
    }
}

// ── Role selector screen ──────────────────────────────────────────────────────

@Composable
private fun RoleSelectorScreen(onRoleSelected: (AppRole) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF0F0F22), EBg))
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(48.dp)
        ) {
            // Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(EOrange, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "QB",
                            fontSize   = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color      = Color.White
                        )
                    }
                    Column {
                        Text(
                            "QuickBite",
                            fontSize      = 28.sp,
                            fontWeight    = FontWeight.ExtraBold,
                            color         = ETextPrimary,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            "Enterprise Suite",
                            fontSize   = 14.sp,
                            color      = EBrand,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Selectați portalul pentru a continua",
                    fontSize  = 15.sp,
                    color     = ETextMuted,
                    textAlign = TextAlign.Center
                )
            }

            // Role cards
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                RoleCard(
                    icon           = Icons.Rounded.ManageAccounts,
                    title          = "Manager Restaurant",
                    subtitle       = "Admin",
                    description    = "Gestionați achizițiile B2B, recrutarea HR și sistemul operațional live.",
                    accentColor    = EOrange,
                    gradientColors = listOf(Color(0xFF3D1200), Color(0xFF200A00)),
                    onClick        = { onRoleSelected(AppRole.RESTAURANT_ADMIN) }
                )
                RoleCard(
                    icon           = Icons.Rounded.Agriculture,
                    title          = "Portal Producător",
                    subtitle       = "B2B Marketplace",
                    description    = "Publicați ofertele fermei dumneavoastră direct în platforma de achiziții.",
                    accentColor    = EGreen,
                    gradientColors = listOf(Color(0xFF0A2A0E), Color(0xFF051408)),
                    onClick        = { onRoleSelected(AppRole.PRODUCER) }
                )
                RoleCard(
                    icon           = Icons.Rounded.Badge,
                    title          = "Portal Cariere",
                    subtitle       = "HR & Recrutare",
                    description    = "Trimiteți CV-ul și candidați pentru pozițiile disponibile în HoReCa.",
                    accentColor    = EBrand,
                    gradientColors = listOf(Color(0xFF0A0A3A), Color(0xFF05050F)),
                    onClick        = { onRoleSelected(AppRole.CANDIDATE) }
                )
            }
        }
    }
}

@Composable
private fun RoleCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    description: String,
    accentColor: Color,
    gradientColors: List<Color>,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick   = onClick,
        modifier  = Modifier.width(310.dp),
        shape     = RoundedCornerShape(24.dp),
        colors    = CardDefaults.elevatedCardColors(containerColor = ESurface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 14.dp, pressedElevation = 24.dp)
    ) {
        Column {
            // Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(148.dp)
                    .background(Brush.linearGradient(gradientColors)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier         = Modifier
                        .size(76.dp)
                        .background(Color.White.copy(alpha = 0.10f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = Color.White, modifier = Modifier.size(38.dp))
                }
                // Subtitle chip
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(14.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(subtitle, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 0.5.sp)
                }
            }
            // Body
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    title,
                    fontSize      = 18.sp,
                    fontWeight    = FontWeight.ExtraBold,
                    color         = ETextPrimary,
                    letterSpacing = (-0.3).sp
                )
                Spacer(Modifier.height(8.dp))
                Text(description, fontSize = 13.sp, color = ETextMuted, lineHeight = 20.sp)
                Spacer(Modifier.height(22.dp))
                Button(
                    onClick  = onClick,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = accentColor)
                ) {
                    Text("Intră", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.ArrowForward, null, tint = Color.White, modifier = Modifier.size(15.dp))
                }
            }
        }
    }
}

// ── Restaurant Admin Portal ───────────────────────────────────────────────────

@Composable
private fun RestaurantAdminPortal(db: Firestore?, onBack: () -> Unit) {
    var selectedTab      by remember { mutableStateOf(AdminTab.OPERATIONAL) }
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData   = data,
                    modifier       = Modifier.padding(bottom = 24.dp, end = 24.dp),
                    containerColor = ESurface2,
                    contentColor   = ETextPrimary,
                    actionColor    = EBrand
                )
            }
        },
        containerColor = EBg
    ) { padding ->
        Row(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Left sidebar
            AdminSidebar(
                selectedTab = selectedTab,
                onTabChange = { selectedTab = it },
                onBack      = onBack
            )

            // Divider
            Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(EGlassBorder))

            // Content pane
            AnimatedContent(
                targetState  = selectedTab,
                transitionSpec = {
                    fadeIn(tween(200)) togetherWith fadeOut(tween(150))
                },
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) { tab ->
                when (tab) {
                    AdminTab.OPERATIONAL  -> OperationalSystemPlaceholder(db = db, onBack = onBack)
                    AdminTab.ACQUISITIONS -> AcquisitionsTab(db = db, snackbarHostState = snackbarHostState)
                    AdminTab.RECRUITMENT  -> RecruitmentTab(db = db, snackbarHostState = snackbarHostState)
                    AdminTab.MENU_MANAGER -> MenuManagerScreen(db = db, snackbarHostState = snackbarHostState)
                    AdminTab.TABLE_CONFIG -> TableManagerScreen(db = db, snackbarHostState = snackbarHostState)
                }
            }
        }
    }
}

// ── Admin sidebar ─────────────────────────────────────────────────────────────

@Composable
private fun AdminSidebar(
    selectedTab: AdminTab,
    onTabChange: (AdminTab) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(240.dp)
            .fillMaxHeight()
            .background(ESurface)
            .padding(horizontal = 14.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // Logo
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier              = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier         = Modifier
                        .size(38.dp)
                        .background(EOrange, RoundedCornerShape(11.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("QB", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
                Column {
                    Text("QuickBite", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = ETextPrimary)
                    Text("Enterprise", fontSize = 11.sp, color = ETextMuted)
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                "LIVE",
                fontSize      = 10.sp,
                fontWeight    = FontWeight.ExtraBold,
                color         = ETextDim,
                letterSpacing = 1.5.sp,
                modifier      = Modifier.padding(horizontal = 10.dp)
            )

            Spacer(Modifier.height(6.dp))

            SidebarNavItem(
                icon       = Icons.Rounded.TableRestaurant,
                label      = "Operațional (KDS & Mese)",
                isSelected = selectedTab == AdminTab.OPERATIONAL,
                onClick    = { onTabChange(AdminTab.OPERATIONAL) }
            )

            Spacer(Modifier.height(16.dp))

            Text(
                "MANAGEMENT",
                fontSize      = 10.sp,
                fontWeight    = FontWeight.ExtraBold,
                color         = ETextDim,
                letterSpacing = 1.5.sp,
                modifier      = Modifier.padding(horizontal = 10.dp)
            )

            Spacer(Modifier.height(6.dp))

            SidebarNavItem(
                icon       = Icons.Rounded.Inventory2,
                label      = "Achiziții B2B",
                isSelected = selectedTab == AdminTab.ACQUISITIONS,
                onClick    = { onTabChange(AdminTab.ACQUISITIONS) }
            )
            SidebarNavItem(
                icon       = Icons.Rounded.Work,
                label      = "Recrutare HR",
                isSelected = selectedTab == AdminTab.RECRUITMENT,
                onClick    = { onTabChange(AdminTab.RECRUITMENT) }
            )

            Spacer(Modifier.height(16.dp))

            Text(
                "RESTAURANT",
                fontSize      = 10.sp,
                fontWeight    = FontWeight.ExtraBold,
                color         = ETextDim,
                letterSpacing = 1.5.sp,
                modifier      = Modifier.padding(horizontal = 10.dp)
            )

            Spacer(Modifier.height(6.dp))

            SidebarNavItem(
                icon       = Icons.Rounded.MenuBook,
                label      = "Management Meniu",
                isSelected = selectedTab == AdminTab.MENU_MANAGER,
                onClick    = { onTabChange(AdminTab.MENU_MANAGER) }
            )
            SidebarNavItem(
                icon       = Icons.Rounded.GridView,
                label      = "Configurare Mese",
                isSelected = selectedTab == AdminTab.TABLE_CONFIG,
                onClick    = { onTabChange(AdminTab.TABLE_CONFIG) }
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(EGlassBorder))
            SidebarNavItem(
                icon       = Icons.Rounded.Logout,
                label      = "Deconectare / Înapoi",
                isSelected = false,
                onClick    = onBack
            )
        }
    }
}

// ── Acquisitions tab ──────────────────────────────────────────────────────────

@Composable
private fun AcquisitionsTab(db: Firestore?, snackbarHostState: SnackbarHostState) {
    val scope = rememberCoroutineScope()
    var offers by remember { mutableStateOf<List<ProducerOffer>?>(null) }

    LaunchedEffect(db) {
        if (db == null) { offers = emptyList(); return@LaunchedEffect }
        producersFlow(db).collect { offers = it }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Section header
        Row(
            modifier              = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 22.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Achiziții B2B",
                    fontSize      = 24.sp,
                    fontWeight    = FontWeight.ExtraBold,
                    color         = ETextPrimary,
                    letterSpacing = (-0.4).sp
                )
                Text(
                    "Oferte în timp real de la producători locali",
                    fontSize = 14.sp,
                    color    = ETextMuted
                )
            }
            offers?.let { list ->
                EnterpriseCountBadge(
                    count = list.count { it.status == "available" },
                    label = "disponibile",
                    color = EOrange
                )
            }
        }

        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(EGlassBorder))

        if (db == null) {
            FirebaseOfflineWarning(modifier = Modifier.fillMaxSize())
            return@Column
        }

        when {
            offers == null -> Box(
                modifier         = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = EBrand, strokeWidth = 2.5.dp)
            }

            offers!!.isEmpty() -> EnterpriseEmptyState(
                icon     = Icons.Rounded.Inventory2,
                title    = "Nicio ofertă disponibilă",
                subtitle = "Producătorii nu au publicat produse încă.",
                modifier = Modifier.fillMaxSize()
            )

            else -> {
                val sorted = remember(offers) {
                    offers!!.sortedBy { if (it.status == "available") 0 else 1 }
                }
                LazyVerticalGrid(
                    columns             = GridCells.Adaptive(minSize = 270.dp),
                    modifier            = Modifier.fillMaxSize(),
                    contentPadding      = PaddingValues(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    gridItems(sorted, key = { it.id }) { offer ->
                        OfferCard(
                            offer = offer,
                            onBuy = {
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            db.collection("producers")
                                                .document(offer.id)
                                                .update(mapOf("status" to "purchased"))
                                                .get()
                                        }
                                        snackbarHostState.showSnackbar(
                                            "Contract generat pentru \"${offer.product}\"! ✓"
                                        )
                                    } catch (e: Exception) {
                                        snackbarHostState.showSnackbar("Eroare: ${e.message}")
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OfferCard(offer: ProducerOffer, onBuy: () -> Unit) {
    val isPurchased = offer.status == "purchased"

    ElevatedCard(
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.elevatedCardColors(containerColor = ESurface),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (isPurchased) 2.dp else 8.dp
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                    Text(
                        offer.farmName,
                        fontSize     = 16.sp,
                        fontWeight   = FontWeight.ExtraBold,
                        color        = ETextPrimary,
                        maxLines     = 1,
                        overflow     = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(offer.product, fontSize = 13.sp, color = ETextMuted)
                }
                StatusBadge(
                    text  = if (isPurchased) "Achiziționat" else "Disponibil",
                    color = if (isPurchased) ETextDim else EGreen
                )
            }

            Spacer(Modifier.height(16.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(EGlassBorder))
            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (offer.quantity.isNotBlank()) InfoChip("📦", offer.quantity)
                if (offer.price.isNotBlank()) {
                    val priceLabel = if (offer.price.contains("RON") || offer.price.contains("€"))
                        offer.price else "${offer.price} RON"
                    InfoChip("💰", priceLabel)
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick  = onBuy,
                enabled  = !isPurchased,
                modifier = Modifier.fillMaxWidth().height(42.dp),
                shape    = RoundedCornerShape(10.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor         = EOrange,
                    disabledContainerColor = EGlass
                )
            ) {
                Icon(
                    imageVector = if (isPurchased) Icons.Rounded.CheckCircle else Icons.Rounded.ShoppingCart,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint     = if (isPurchased) ETextDim else Color.White
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    if (isPurchased) "Contract generat ✓" else "Cumpără / Generează Contract",
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = if (isPurchased) ETextDim else Color.White
                )
            }
        }
    }
}

// ── Recruitment tab ───────────────────────────────────────────────────────────

@Composable
private fun RecruitmentTab(db: Firestore?, snackbarHostState: SnackbarHostState) {
    val scope = rememberCoroutineScope()
    var cvs by remember { mutableStateOf<List<HrApplication>?>(null) }

    LaunchedEffect(db) {
        if (db == null) { cvs = emptyList(); return@LaunchedEffect }
        hrCvsFlow(db).collect { cvs = it }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 22.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Recrutare HR",
                    fontSize      = 24.sp,
                    fontWeight    = FontWeight.ExtraBold,
                    color         = ETextPrimary,
                    letterSpacing = (-0.4).sp
                )
                Text("Candidaturi primite în timp real", fontSize = 14.sp, color = ETextMuted)
            }
            cvs?.let { list ->
                EnterpriseCountBadge(
                    count = list.count { it.status == "pending" },
                    label = "în așteptare",
                    color = EAmber
                )
            }
        }

        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(EGlassBorder))

        if (db == null) {
            FirebaseOfflineWarning(modifier = Modifier.fillMaxSize())
            return@Column
        }

        when {
            cvs == null -> Box(
                modifier         = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = EBrand, strokeWidth = 2.5.dp)
            }

            cvs!!.isEmpty() -> EnterpriseEmptyState(
                icon     = Icons.Rounded.Work,
                title    = "Niciun CV primit",
                subtitle = "Candidaturile vor apărea automat\ncând sunt trimise prin portal.",
                modifier = Modifier.fillMaxSize()
            )

            else -> {
                val sorted = remember(cvs) {
                    cvs!!.sortedBy { if (it.status == "pending") 0 else 1 }
                }
                LazyColumn(
                    modifier       = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(sorted, key = { it.id }) { cv ->
                        CvCard(
                            cv        = cv,
                            onApprove = {
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            db.collection("hr_cvs")
                                                .document(cv.id)
                                                .update(mapOf("status" to "approved"))
                                                .get()
                                        }
                                        snackbarHostState.showSnackbar(
                                            "${cv.fullName} a fost aprobat pentru \"${cv.position}\"! ✓"
                                        )
                                    } catch (e: Exception) {
                                        snackbarHostState.showSnackbar("Eroare: ${e.message}")
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CvCard(cv: HrApplication, onApprove: () -> Unit) {
    val isApproved = cv.status == "approved"

    ElevatedCard(
        shape     = RoundedCornerShape(18.dp),
        colors    = CardDefaults.elevatedCardColors(containerColor = ESurface),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (isApproved) 2.dp else 6.dp
        ),
        modifier  = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier              = Modifier.padding(20.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Avatar
            Box(
                modifier         = Modifier
                    .size(52.dp)
                    .background(EBrand.copy(alpha = 0.14f), CircleShape)
                    .border(1.dp, EBrand.copy(alpha = 0.30f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    cv.fullName.take(2).uppercase(),
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color      = EBrand
                )
            }

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        cv.fullName,
                        fontSize   = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color      = ETextPrimary
                    )
                    StatusBadge(
                        text  = if (isApproved) "Aprobat" else "În așteptare",
                        color = if (isApproved) EGreen else EAmber
                    )
                }
                Spacer(Modifier.height(3.dp))
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(Icons.Rounded.Work, null, tint = EBrand, modifier = Modifier.size(13.dp))
                    Text(cv.position, fontSize = 13.sp, color = EBrand, fontWeight = FontWeight.SemiBold)
                }
                if (cv.experience.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        cv.experience,
                        fontSize   = 12.sp,
                        color      = ETextMuted,
                        maxLines   = 2,
                        overflow   = TextOverflow.Ellipsis,
                        lineHeight = 18.sp
                    )
                }
            }

            // Approve button
            Button(
                onClick  = onApprove,
                enabled  = !isApproved,
                shape    = RoundedCornerShape(10.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor         = EGreen,
                    disabledContainerColor = EGlass
                ),
                modifier = Modifier.width(148.dp).height(40.dp)
            ) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    null,
                    modifier = Modifier.size(14.dp),
                    tint     = if (isApproved) ETextDim else Color.White
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    if (isApproved) "Aprobat ✓" else "Aprobă Angajarea",
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = if (isApproved) ETextDim else Color.White
                )
            }
        }
    }
}

// ── Producer portal ───────────────────────────────────────────────────────────

@Composable
private fun ProducerPortal(db: Firestore?, onBack: () -> Unit) {
    var farmName  by remember { mutableStateOf("") }
    var product   by remember { mutableStateOf("") }
    var quantity  by remember { mutableStateOf("") }
    var price     by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var success   by remember { mutableStateOf(false) }
    val scope     = rememberCoroutineScope()

    PortalScaffold(
        title    = "Portal Producător",
        subtitle = "Publicați oferta pe piața B2B",
        icon     = Icons.Rounded.Agriculture,
        accentColor = EGreen,
        onBack   = onBack
    ) {
        if (success) {
            SuccessPanel(
                message  = "Oferta a fost publicată cu succes!\nAdministratorii o pot vizualiza acum.",
                onReset  = {
                    farmName = ""; product = ""; quantity = ""; price = ""; success = false
                }
            )
            return@PortalScaffold
        }

        if (db == null) FirebaseOfflineWarning()

        EnterpriseTextField(value = farmName, onValueChange = { farmName = it }, label = "Nume Fermă")
        EnterpriseTextField(value = product,  onValueChange = { product  = it }, label = "Produs (ex: Roșii cherry, Lapte integral)")
        EnterpriseTextField(value = quantity, onValueChange = { quantity = it }, label = "Cantitate disponibilă (ex: 500 kg / săptămână)")
        EnterpriseTextField(value = price,    onValueChange = { price    = it }, label = "Preț unitar (ex: 12.50 RON/kg)")

        Spacer(Modifier.height(8.dp))

        val canSubmit = farmName.isNotBlank() && product.isNotBlank() && db != null && !isLoading
        Button(
            onClick = {
                scope.launch {
                    isLoading = true
                    try {
                        withContext(Dispatchers.IO) {
                            db!!.collection("producers").add(
                                mapOf(
                                    "farmName"  to farmName.trim(),
                                    "product"   to product.trim(),
                                    "quantity"  to quantity.trim(),
                                    "price"     to price.trim(),
                                    "status"    to "available",
                                    "timestamp" to System.currentTimeMillis()
                                )
                            ).get()
                        }
                        success = true
                    } catch (e: Exception) {
                        // Keep form open — user can retry
                    } finally {
                        isLoading = false
                    }
                }
            },
            enabled  = canSubmit,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape    = RoundedCornerShape(14.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = EGreen)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color     = Color.White,
                    modifier  = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(Icons.Rounded.Publish, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Publică Ofertă", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
        }
    }
}

// ── Candidate portal ──────────────────────────────────────────────────────────

@Composable
private fun CandidatePortal(db: Firestore?, onBack: () -> Unit) {
    var fullName    by remember { mutableStateOf("") }
    var position    by remember { mutableStateOf("") }
    var experience  by remember { mutableStateOf("") }
    var isLoading   by remember { mutableStateOf(false) }
    var success     by remember { mutableStateOf(false) }
    val scope       = rememberCoroutineScope()

    PortalScaffold(
        title       = "Portal Cariere",
        subtitle    = "Candidați pentru o poziție în echipa noastră",
        icon        = Icons.Rounded.Badge,
        accentColor = EBrand,
        onBack      = onBack
    ) {
        if (success) {
            SuccessPanel(
                message = "CV-ul a fost trimis cu succes!\nVă vom contacta în curând.",
                onReset = { fullName = ""; position = ""; experience = ""; success = false }
            )
            return@PortalScaffold
        }

        if (db == null) FirebaseOfflineWarning()

        EnterpriseTextField(value = fullName,   onValueChange = { fullName   = it }, label = "Nume Complet")
        EnterpriseTextField(value = position,   onValueChange = { position   = it }, label = "Poziție Dorită (ex: Ospătar, Bucătar, Manager)")
        EnterpriseTextField(
            value         = experience,
            onValueChange = { experience = it },
            label         = "Experiență relevantă",
            singleLine    = false,
            minLines      = 4
        )

        Spacer(Modifier.height(8.dp))

        val canSubmit = fullName.isNotBlank() && position.isNotBlank() && db != null && !isLoading
        Button(
            onClick = {
                scope.launch {
                    isLoading = true
                    try {
                        withContext(Dispatchers.IO) {
                            db!!.collection("hr_cvs").add(
                                mapOf(
                                    "fullName"   to fullName.trim(),
                                    "position"   to position.trim(),
                                    "experience" to experience.trim(),
                                    "status"     to "pending",
                                    "timestamp"  to System.currentTimeMillis()
                                )
                            ).get()
                        }
                        success = true
                    } catch (e: Exception) {
                        // Keep form open — user can retry
                    } finally {
                        isLoading = false
                    }
                }
            },
            enabled  = canSubmit,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape    = RoundedCornerShape(14.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = EBrand)
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Rounded.Send, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Trimite CV", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
        }
    }
}

// ── KYC agent panel ───────────────────────────────────────────────────────────

private data class RestaurantRegistration(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val companyName: String = "",
    val cui: String = "",
    val kycStatus: String = "PENDING"
)

private fun kycRegistrationsFlow(db: Firestore): Flow<List<RestaurantRegistration>> = callbackFlow {
    val reg = db.collection("users")
        .whereEqualTo("role", "Restaurant")
        .addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            trySend(
                snapshot.documents.mapNotNull { doc ->
                    RestaurantRegistration(
                        uid         = doc.id,
                        name        = doc.getString("name") ?: "",
                        email       = doc.getString("email") ?: "",
                        companyName = doc.getString("companyName") ?: return@mapNotNull null,
                        cui         = doc.getString("cui") ?: "",
                        kycStatus   = doc.getString("kyc_status") ?: "PENDING"
                    )
                }.sortedBy { if (it.kycStatus == "PENDING") 0 else 1 }
            )
        }
    awaitClose { reg.remove() }
}

@Composable
private fun KycAgentPanel(db: Firestore?, onBack: () -> Unit) {
    var registrations by remember { mutableStateOf<List<RestaurantRegistration>?>(null) }
    val scope             = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(db) {
        if (db == null) { registrations = emptyList(); return@LaunchedEffect }
        kycRegistrationsFlow(db).collect { registrations = it }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData   = data,
                    modifier       = Modifier.padding(bottom = 24.dp, end = 24.dp),
                    containerColor = ESurface2,
                    contentColor   = ETextPrimary,
                    actionColor    = EBrand
                )
            }
        },
        containerColor = EBg
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Header
            Row(
                modifier              = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 22.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    TextButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, null, tint = ETextMuted, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Deconectare", color = ETextMuted, fontSize = 13.sp)
                    }
                    Column {
                        Text(
                            "Panou Verificare KYC",
                            fontSize      = 24.sp,
                            fontWeight    = FontWeight.ExtraBold,
                            color         = ETextPrimary,
                            letterSpacing = (-0.4).sp
                        )
                        Text(
                            "Aprobați înregistrările restaurantelor partenere",
                            fontSize = 14.sp,
                            color    = ETextMuted
                        )
                    }
                }
                registrations?.let { list ->
                    EnterpriseCountBadge(
                        count = list.count { it.kycStatus == "PENDING" },
                        label = "în așteptare",
                        color = EAmber
                    )
                }
            }

            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(EGlassBorder))

            when {
                db == null -> FirebaseOfflineWarning(modifier = Modifier.fillMaxSize())

                registrations == null -> Box(
                    modifier         = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = EBrand, strokeWidth = 2.5.dp)
                }

                registrations!!.isEmpty() -> EnterpriseEmptyState(
                    icon     = Icons.Rounded.ManageAccounts,
                    title    = "Nicio înregistrare",
                    subtitle = "Restaurantele înregistrate vor apărea automat aici.",
                    modifier = Modifier.fillMaxSize()
                )

                else -> LazyColumn(
                    modifier       = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(registrations!!, key = { it.uid }) { reg ->
                        KycRegistrationCard(
                            reg       = reg,
                            onApprove = {
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            db.collection("users")
                                                .document(reg.uid)
                                                .update(mapOf("kyc_status" to "APPROVED"))
                                                .get()
                                        }
                                        snackbarHostState.showSnackbar(
                                            "\"${reg.companyName}\" a fost aprobat ✓"
                                        )
                                    } catch (e: Exception) {
                                        snackbarHostState.showSnackbar("Eroare: ${e.message}")
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KycRegistrationCard(reg: RestaurantRegistration, onApprove: () -> Unit) {
    val isApproved = reg.kycStatus == "APPROVED"

    ElevatedCard(
        shape     = RoundedCornerShape(18.dp),
        colors    = CardDefaults.elevatedCardColors(containerColor = ESurface),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (isApproved) 2.dp else 6.dp
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier              = Modifier.padding(20.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Icon
            Box(
                modifier         = Modifier
                    .size(52.dp)
                    .background(EOrange.copy(alpha = 0.12f), androidx.compose.foundation.shape.CircleShape)
                    .border(1.dp, EOrange.copy(alpha = 0.28f), androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    reg.companyName.take(2).uppercase(),
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color      = EOrange
                )
            }

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        reg.companyName,
                        fontSize   = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color      = ETextPrimary
                    )
                    StatusBadge(
                        text  = if (isApproved) "Aprobat" else "În așteptare",
                        color = if (isApproved) EGreen else EAmber
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text("CUI: ${reg.cui}", fontSize = 13.sp, color = ETextMuted)
                Spacer(Modifier.height(2.dp))
                Text("${reg.name}  ·  ${reg.email}", fontSize = 12.sp, color = ETextDim)
            }

            // Approve button
            Button(
                onClick  = onApprove,
                enabled  = !isApproved,
                shape    = RoundedCornerShape(10.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor         = EGreen,
                    disabledContainerColor = EGlass
                ),
                modifier = Modifier.width(160.dp).height(40.dp)
            ) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    null,
                    modifier = Modifier.size(14.dp),
                    tint     = if (isApproved) ETextDim else Color.White
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    if (isApproved) "Aprobat ✓" else "Aprobă KYC",
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = if (isApproved) ETextDim else Color.White
                )
            }
        }
    }
}

// ── Operational system ────────────────────────────────────────────────────────

@Composable
fun OperationalSystemPlaceholder(db: Firestore?, onBack: () -> Unit) {
    // Re-apply the kitchen colour scheme so WaiterApp doesn't inherit enterprise indigo.
    MaterialTheme(colorScheme = KitchenTheme) {
        WaiterApp(db = db)
    }
}

// ── Menu manager tab ──────────────────────────────────────────────────────────

@Composable
private fun MenuManagerScreen(db: Firestore?, snackbarHostState: SnackbarHostState) {
    val scope = rememberCoroutineScope()
    var items         by remember { mutableStateOf<List<MenuItem>?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(db) {
        if (db == null) { items = emptyList(); return@LaunchedEffect }
        menuItemsFlow(db).collect { items = it }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Section header
        Row(
            modifier              = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 22.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Management Meniu",
                    fontSize      = 24.sp,
                    fontWeight    = FontWeight.ExtraBold,
                    color         = ETextPrimary,
                    letterSpacing = (-0.4).sp
                )
                Text("Administrați preparatele restaurantului", fontSize = 14.sp, color = ETextMuted)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                items?.let { list ->
                    EnterpriseCountBadge(count = list.size, label = "preparate", color = EOrange)
                }
                Button(
                    onClick  = { showAddDialog = true },
                    enabled  = db != null,
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = EOrange)
                ) {
                    Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Adaugă Preparat", fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(EGlassBorder))

        if (db == null) { FirebaseOfflineWarning(modifier = Modifier.fillMaxSize()); return@Column }

        when {
            items == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = EBrand, strokeWidth = 2.5.dp)
            }
            items!!.isEmpty() -> EnterpriseEmptyState(
                icon     = Icons.Rounded.MenuBook,
                title    = "Meniu gol",
                subtitle = "Adăugați primul preparat pentru a configura meniul restaurantului.",
                modifier = Modifier.fillMaxSize()
            )
            else -> LazyVerticalGrid(
                columns               = GridCells.Adaptive(minSize = 270.dp),
                modifier              = Modifier.fillMaxSize(),
                contentPadding        = PaddingValues(24.dp),
                verticalArrangement   = Arrangement.spacedBy(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                gridItems(items!!, key = { it.id }) { item ->
                    MenuItemCard(
                        item     = item,
                        onDelete = {
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        db.collection("menu").document(item.id).delete().get()
                                    }
                                    snackbarHostState.showSnackbar("\"${item.name}\" a fost eliminat din meniu.")
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar("Eroare: ${e.message}")
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddMenuItemDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, description, price, category ->
                showAddDialog = false
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            db!!.collection("menu").add(
                                mapOf(
                                    "name"        to name,
                                    "description" to description,
                                    "price"       to price,
                                    "category"    to category
                                )
                            ).get()
                        }
                        snackbarHostState.showSnackbar("\"$name\" a fost adăugat în meniu ✓")
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("Eroare: ${e.message}")
                    }
                }
            }
        )
    }
}

@Composable
private fun MenuItemCard(item: MenuItem, onDelete: () -> Unit) {
    ElevatedCard(
        shape     = RoundedCornerShape(18.dp),
        colors    = CardDefaults.elevatedCardColors(containerColor = ESurface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
        modifier  = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        item.name,
                        fontSize   = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color      = ETextPrimary,
                        maxLines   = 2,
                        overflow   = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(5.dp))
                    StatusBadge(item.category.ifBlank { "Altele" }, EOrange)
                }
                IconButton(
                    onClick  = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Rounded.Delete,
                        null,
                        tint     = Color(0xFFFF3B30).copy(alpha = 0.70f),
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            if (item.description.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    item.description,
                    fontSize   = 13.sp,
                    color      = ETextMuted,
                    maxLines   = 2,
                    overflow   = TextOverflow.Ellipsis,
                    lineHeight = 19.sp
                )
            }

            Spacer(Modifier.height(14.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(EGlassBorder))
            Spacer(Modifier.height(12.dp))

            Text(
                "%.2f RON".format(item.price),
                fontSize   = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color      = EOrange,
                letterSpacing = (-0.3).sp
            )
        }
    }
}

@Composable
private fun AddMenuItemDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String, price: Double, category: String) -> Unit
) {
    var name             by remember { mutableStateOf("") }
    var description      by remember { mutableStateOf("") }
    var priceText        by remember { mutableStateOf("") }
    var category         by remember { mutableStateOf(MENU_CATEGORIES[0]) }
    var categoryExpanded by remember { mutableStateOf(false) }

    val price      = priceText.replace(",", ".").toDoubleOrNull()
    val canConfirm = name.isNotBlank() && price != null && price > 0.0

    AlertDialog(
        onDismissRequest  = onDismiss,
        containerColor    = ESurface,
        titleContentColor = ETextPrimary,
        textContentColor  = ETextPrimary,
        title = {
            Text("Adaugă Preparat în Meniu", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                EnterpriseTextField(name, { name = it }, "Denumire preparat *")
                EnterpriseTextField(
                    value         = description,
                    onValueChange = { description = it },
                    label         = "Descriere (opțional)",
                    singleLine    = false,
                    minLines      = 2
                )
                OutlinedTextField(
                    value         = priceText,
                    onValueChange = { priceText = it },
                    label         = { Text("Preț (RON) *") },
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true,
                    shape         = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError       = priceText.isNotBlank() && price == null,
                    colors        = dialogFieldColors()
                )
                // Category picker
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Categorie *", fontSize = 12.sp, color = ETextMuted)
                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, EGlassBorder, RoundedCornerShape(12.dp))
                                .background(EGlass, RoundedCornerShape(12.dp))
                                .clickable { categoryExpanded = true }
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Text(category, fontSize = 14.sp, color = ETextPrimary)
                            Icon(Icons.Rounded.KeyboardArrowDown, null, tint = ETextMuted, modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(
                            expanded         = categoryExpanded,
                            onDismissRequest = { categoryExpanded = false },
                            modifier         = Modifier.background(Color(0xFF181928))
                        ) {
                            MENU_CATEGORIES.forEach { cat ->
                                DropdownMenuItem(
                                    text = {
                                        Text(cat, color = if (cat == category) EBrand else ETextPrimary, fontSize = 14.sp)
                                    },
                                    onClick = { category = cat; categoryExpanded = false },
                                    leadingIcon = if (cat == category) {
                                        { Icon(Icons.Rounded.Check, null, tint = EBrand, modifier = Modifier.size(16.dp)) }
                                    } else null
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick  = { onConfirm(name.trim(), description.trim(), price!!, category) },
                enabled  = canConfirm,
                shape    = RoundedCornerShape(10.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = EOrange)
            ) {
                Text("Adaugă", fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Anulează", color = ETextMuted)
            }
        }
    )
}

// ── Table configuration tab ───────────────────────────────────────────────────

@Composable
private fun TableManagerScreen(db: Firestore?, snackbarHostState: SnackbarHostState) {
    val scope = rememberCoroutineScope()
    var tables        by remember { mutableStateOf<List<RestaurantTable>?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(db) {
        if (db == null) { tables = emptyList(); return@LaunchedEffect }
        tablesConfigFlow(db).collect { tables = it }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Section header
        Row(
            modifier              = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 22.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Configurare Mese",
                    fontSize      = 24.sp,
                    fontWeight    = FontWeight.ExtraBold,
                    color         = ETextPrimary,
                    letterSpacing = (-0.4).sp
                )
                Text("Gestionați capacitatea și structura sălii", fontSize = 14.sp, color = ETextMuted)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                tables?.let { list ->
                    EnterpriseCountBadge(count = list.size, label = "mese", color = EBrand)
                }
                Button(
                    onClick  = { showAddDialog = true },
                    enabled  = db != null,
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = EBrand)
                ) {
                    Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Adaugă Masă", fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(EGlassBorder))

        if (db == null) { FirebaseOfflineWarning(modifier = Modifier.fillMaxSize()); return@Column }

        when {
            tables == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = EBrand, strokeWidth = 2.5.dp)
            }
            tables!!.isEmpty() -> EnterpriseEmptyState(
                icon     = Icons.Rounded.GridView,
                title    = "Nicio masă configurată",
                subtitle = "Adăugați mesele pentru a activa sistemul de rezervări și KDS.",
                modifier = Modifier.fillMaxSize()
            )
            else -> LazyVerticalGrid(
                columns               = GridCells.Adaptive(minSize = 200.dp),
                modifier              = Modifier.fillMaxSize(),
                contentPadding        = PaddingValues(24.dp),
                verticalArrangement   = Arrangement.spacedBy(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                gridItems(tables!!, key = { it.id }) { table ->
                    TableConfigCard(
                        table    = table,
                        onDelete = {
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        db.collection("tables").document(table.id).delete().get()
                                    }
                                    snackbarHostState.showSnackbar("Masa ${table.tableNumber} a fost eliminată.")
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar("Eroare: ${e.message}")
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddTableDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { tableNumber, capacity ->
                showAddDialog = false
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            db!!.collection("tables").add(
                                mapOf(
                                    "tableNumber" to tableNumber,
                                    "capacity"    to capacity,
                                    "status"      to "LIBERA"
                                )
                            ).get()
                        }
                        snackbarHostState.showSnackbar("Masa $tableNumber (${capacity} locuri) adăugată ✓")
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("Eroare: ${e.message}")
                    }
                }
            }
        )
    }
}

@Composable
private fun TableConfigCard(table: RestaurantTable, onDelete: () -> Unit) {
    val (statusColor, statusLabel) = when (table.status) {
        "OCUPATA"   -> EOrange to "Ocupată"
        "REZERVATA" -> EAmber  to "Rezervată"
        else        -> EGreen  to "Liberă"
    }

    ElevatedCard(
        shape     = RoundedCornerShape(18.dp),
        colors    = CardDefaults.elevatedCardColors(containerColor = ESurface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
        modifier  = Modifier.fillMaxWidth()
    ) {
        Column {
            // Coloured header band
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(86.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(statusColor.copy(alpha = 0.22f), statusColor.copy(alpha = 0.06f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Rounded.TableRestaurant,
                        null,
                        tint     = statusColor,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Masa ${table.tableNumber}",
                        fontSize   = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color      = ETextPrimary
                    )
                }
            }

            // Details
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(Icons.Rounded.Group, null, tint = ETextMuted, modifier = Modifier.size(14.dp))
                        Text("${table.capacity} locuri", fontSize = 13.sp, color = ETextMuted)
                    }
                    StatusBadge(statusLabel, statusColor)
                }

                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(EGlassBorder))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = onDelete,
                        colors  = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF3B30))
                    ) {
                        Icon(Icons.Rounded.Delete, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Șterge", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun AddTableDialog(
    onDismiss: () -> Unit,
    onConfirm: (tableNumber: Int, capacity: Int) -> Unit
) {
    var tableNumberText by remember { mutableStateOf("") }
    var capacityText    by remember { mutableStateOf("") }

    val tableNumber = tableNumberText.trim().toIntOrNull()
    val capacity    = capacityText.trim().toIntOrNull()
    val canConfirm  = tableNumber != null && tableNumber > 0 && capacity != null && capacity > 0

    AlertDialog(
        onDismissRequest  = onDismiss,
        containerColor    = ESurface,
        titleContentColor = ETextPrimary,
        textContentColor  = ETextPrimary,
        title = {
            Text("Adaugă Masă Nouă", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value         = tableNumberText,
                    onValueChange = { tableNumberText = it },
                    label         = { Text("Număr Masă *") },
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true,
                    shape         = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError       = tableNumberText.isNotBlank() && tableNumber == null,
                    colors        = dialogFieldColors()
                )
                OutlinedTextField(
                    value         = capacityText,
                    onValueChange = { capacityText = it },
                    label         = { Text("Capacitate (nr. locuri) *") },
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true,
                    shape         = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError       = capacityText.isNotBlank() && capacity == null,
                    colors        = dialogFieldColors()
                )
                Text(
                    "Masa va fi adăugată cu statusul LIBERĂ.",
                    fontSize = 12.sp,
                    color    = ETextMuted
                )
            }
        },
        confirmButton = {
            Button(
                onClick  = { onConfirm(tableNumber!!, capacity!!) },
                enabled  = canConfirm,
                shape    = RoundedCornerShape(10.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = EBrand)
            ) {
                Text("Adaugă", fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Anulează", color = ETextMuted)
            }
        }
    )
}

// Shared dialog text-field colours so both dialogs stay visually consistent.
@Composable
private fun dialogFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor      = EBrand,
    unfocusedBorderColor    = EGlassBorder,
    errorBorderColor        = Color(0xFFFF3B30),
    focusedLabelColor       = EBrand,
    unfocusedLabelColor     = ETextMuted,
    focusedTextColor        = ETextPrimary,
    unfocusedTextColor      = ETextPrimary,
    cursorColor             = EBrand,
    focusedContainerColor   = EGlass,
    unfocusedContainerColor = EGlass
)

// ── Shared: Portal scaffold (centering + back button) ─────────────────────────

@Composable
private fun PortalScaffold(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EBg),
        contentAlignment = Alignment.Center
    ) {
        // Back button — top left
        TextButton(
            onClick  = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(20.dp)
        ) {
            Icon(Icons.Rounded.ArrowBack, null, tint = ETextMuted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Înapoi", color = ETextMuted, fontSize = 14.sp)
        }

        ElevatedCard(
            shape     = RoundedCornerShape(28.dp),
            colors    = CardDefaults.elevatedCardColors(containerColor = ESurface),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 24.dp),
            modifier  = Modifier.width(540.dp)
        ) {
            Column(
                modifier            = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(36.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier         = Modifier
                            .size(50.dp)
                            .background(accentColor.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
                            .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, null, tint = accentColor, modifier = Modifier.size(26.dp))
                    }
                    Column {
                        Text(title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = ETextPrimary)
                        Text(subtitle, fontSize = 13.sp, color = ETextMuted)
                    }
                }

                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(EGlassBorder))

                content()
            }
        }
    }
}

// ── Shared: Success panel ─────────────────────────────────────────────────────

@Composable
private fun SuccessPanel(message: String, onReset: () -> Unit) {
    Column(
        modifier            = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier         = Modifier
                .size(72.dp)
                .background(EGreen.copy(alpha = 0.12f), CircleShape)
                .border(1.dp, EGreen.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.CheckCircle, null, tint = EGreen, modifier = Modifier.size(40.dp))
        }
        Text(message, fontSize = 15.sp, color = ETextPrimary, textAlign = TextAlign.Center, lineHeight = 23.sp)
        OutlinedButton(
            onClick = onReset,
            shape   = RoundedCornerShape(12.dp),
            border  = androidx.compose.foundation.BorderStroke(1.dp, EGlassBorder)
        ) {
            Text("Adaugă alt formular", color = ETextMuted, fontSize = 13.sp)
        }
    }
}

// ── Shared: Firebase offline warning ─────────────────────────────────────────

@Composable
private fun FirebaseOfflineWarning(modifier: Modifier = Modifier) {
    if (modifier == Modifier) {
        // Inline banner inside a form
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(EAmberDim)
                .border(1.dp, EAmber.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Rounded.Warning, null, tint = EAmber, modifier = Modifier.size(16.dp))
            Text(
                "Firebase nu este inițializat. Verificați service-account.json.",
                fontSize = 12.sp,
                color    = EAmber
            )
        }
    } else {
        // Full-screen variant used in admin tabs
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Rounded.Warning, null, tint = EAmber, modifier = Modifier.size(40.dp))
                Text(
                    "Firebase nu este inițializat.\nVerificați service-account.json și reporniți.",
                    fontSize   = 14.sp,
                    color      = ETextMuted,
                    textAlign  = TextAlign.Center,
                    lineHeight = 22.sp
                )
            }
        }
    }
}

// ── Shared: Empty state ───────────────────────────────────────────────────────

@Composable
private fun EnterpriseEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier            = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier         = Modifier.size(72.dp).background(EGlass, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = ETextDim, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = ETextPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, fontSize = 14.sp, color = ETextMuted, textAlign = TextAlign.Center, lineHeight = 21.sp)
    }
}

// ── Shared: Count badge ───────────────────────────────────────────────────────

@Composable
private fun EnterpriseCountBadge(count: Int, label: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(count.toString(), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = color)
        Text(label, fontSize = 13.sp, color = color, fontWeight = FontWeight.Medium)
    }
}

// ── Shared: Status badge ──────────────────────────────────────────────────────

@Composable
private fun StatusBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.13f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

// ── Shared: Info chip ─────────────────────────────────────────────────────────

@Composable
private fun InfoChip(emoji: String, label: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(EGlass)
            .border(1.dp, EGlassBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(emoji, fontSize = 12.sp)
        Text(label, fontSize = 12.sp, color = ETextPrimary, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ── Shared: Sidebar nav item ──────────────────────────────────────────────────

@Composable
private fun SidebarNavItem(icon: ImageVector, label: String, isSelected: Boolean, onClick: () -> Unit) {
    val bgColor      by animateColorAsState(if (isSelected) EBrand.copy(alpha = 0.15f) else Color.Transparent, tween(180))
    val contentColor by animateColorAsState(if (isSelected) EBrand else ETextMuted, tween(180))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, null, tint = contentColor, modifier = Modifier.size(19.dp))
        Text(
            label,
            fontSize   = 14.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color      = contentColor
        )
    }
}

// ── Shared: Form text field ───────────────────────────────────────────────────

@Composable
private fun EnterpriseTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    singleLine: Boolean = true,
    minLines: Int = 1
) {
    OutlinedTextField(
        value         = value,
        onValueChange = onValueChange,
        label         = { Text(label) },
        modifier      = Modifier.fillMaxWidth(),
        singleLine    = singleLine,
        minLines      = minLines,
        shape         = RoundedCornerShape(12.dp),
        colors        = OutlinedTextFieldDefaults.colors(
            focusedBorderColor    = EBrand,
            unfocusedBorderColor  = EGlassBorder,
            focusedLabelColor     = EBrand,
            unfocusedLabelColor   = ETextMuted,
            focusedTextColor      = ETextPrimary,
            unfocusedTextColor    = ETextPrimary,
            cursorColor           = EBrand,
            focusedContainerColor   = EGlass,
            unfocusedContainerColor = EGlass
        )
    )
}
