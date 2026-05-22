package com.example.quickbite.android.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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

// ── Palette (dark, matches ClientHomeFeedScreen) ──────────────────────────────

private val PBg          = Color(0xFF0C0C0C)
private val PSurface     = Color(0xFF161616)
private val PGlass       = Color(0x14FFFFFF)
private val PGlassBorder = Color(0x1AFFFFFF)
private val PBrand       = Color(0xFFE8430A)
private val PTextPrimary = Color(0xFFFFFFFF)
private val PTextMuted   = Color(0xFF9A9A9A)

// ── Screen ────────────────────────────────────────────────────────────────────

/**
 * Read-only restaurant menu. Users can browse but NOT add to cart.
 * The QR scan CTA at the bottom unlocks ordering in [ClientActiveSessionScreen].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestaurantPreviewScreen(
    restaurantId   : String,
    onScanQrClicked: () -> Unit,
    onBack         : () -> Unit,
    vm             : ClientViewModel = viewModel()
) {
    val menuState        by vm.menuState.collectAsStateWithLifecycle()
    var selectedCategory by remember { mutableStateOf("Toate") }

    LaunchedEffect(restaurantId) { vm.loadMenu(restaurantId) }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PBg)
                    .statusBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Înapoi", tint = PTextPrimary)
                }
                Column(
                    modifier            = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Meniu Restaurant",
                        fontSize   = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color      = PTextPrimary
                    )
                    Text("Navigare · Fără comandă", fontSize = 11.sp, color = PTextMuted)
                }
            }
        },
        bottomBar = {
            // Prominent QR scan CTA — the only way to start ordering
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, PBg))
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Button(
                    onClick  = onScanQrClicked,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape    = RoundedCornerShape(16.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = PBrand)
                ) {
                    Icon(Icons.Rounded.QrCodeScanner, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Scanează QR-ul de pe masă pentru a comanda",
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color      = Color.White
                    )
                }
            }
        },
        containerColor = PBg
    ) { padding ->
        when (val state = menuState) {
            is MenuUiState.Loading -> Box(
                modifier         = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = PBrand, strokeWidth = 2.5.dp, modifier = Modifier.size(40.dp))
            }

            is MenuUiState.Error -> Box(
                modifier         = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier            = Modifier.padding(32.dp)
                ) {
                    Text("⚠️", fontSize = 44.sp)
                    Text(
                        "Meniu indisponibil",
                        fontSize   = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color      = PTextPrimary,
                        textAlign  = TextAlign.Center
                    )
                    Text(state.message, fontSize = 13.sp, color = PTextMuted, textAlign = TextAlign.Center)
                }
            }

            is MenuUiState.Empty -> Box(
                modifier         = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("🍽️", fontSize = 48.sp)
                    Text("Meniu gol", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = PTextPrimary)
                    Text(
                        "Restaurantul nu are produse disponibile momentan.",
                        fontSize = 13.sp,
                        color    = PTextMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
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

                Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    // Category filter row
                    LazyRow(
                        contentPadding        = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            PreviewFilterChip(
                                label    = "🍽️ Toate",
                                selected = selectedCategory == "Toate",
                                onClick  = { selectedCategory = "Toate" }
                            )
                        }
                        items(allCategories) { cat ->
                            PreviewFilterChip(
                                label    = "${previewCategoryEmoji(cat)} $cat",
                                selected = selectedCategory == cat,
                                onClick  = { selectedCategory = cat }
                            )
                        }
                    }

                    // Menu list — read-only, no add-to-cart controls
                    LazyColumn(
                        modifier            = Modifier.fillMaxSize(),
                        contentPadding      = PaddingValues(
                            start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        grouped.forEach { (category, catItems) ->
                            if (selectedCategory == "Toate" && category.isNotBlank()) {
                                item(key = "hdr_$category") {
                                    Row(
                                        verticalAlignment     = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier              = Modifier.padding(top = 12.dp, bottom = 2.dp)
                                    ) {
                                        Text(previewCategoryEmoji(category), fontSize = 13.sp)
                                        Text(
                                            category.uppercase(),
                                            fontSize      = 11.sp,
                                            fontWeight    = FontWeight.ExtraBold,
                                            color         = PTextMuted,
                                            letterSpacing = 1.sp
                                        )
                                    }
                                }
                            }
                            items(catItems, key = { "${category}_${it.name}" }) { entry ->
                                PreviewMenuItemRow(entry = entry)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Read-only menu item row ────────────────────────────────────────────────────

@Composable
private fun PreviewMenuItemRow(entry: MenuEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(PSurface)
            .border(1.dp, PGlassBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                entry.name,
                fontSize   = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color      = PTextPrimary,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
            if (entry.description.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    entry.description,
                    fontSize   = 12.sp,
                    color      = PTextMuted,
                    maxLines   = 2,
                    overflow   = TextOverflow.Ellipsis,
                    lineHeight = 17.sp
                )
            }
        }
        if (entry.price > 0.0) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(PBrand.copy(alpha = 0.12f))
                    .border(1.dp, PBrand.copy(alpha = 0.28f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    "${"%.0f".format(entry.price)} RON",
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color      = PBrand
                )
            }
        }
    }
}

// ── Filter chip ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreviewFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick  = onClick,
        label    = { Text(label, fontSize = 12.sp) },
        shape    = RoundedCornerShape(20.dp),
        colors   = FilterChipDefaults.filterChipColors(
            selectedContainerColor = PBrand,
            selectedLabelColor     = Color.White,
            containerColor         = PGlass,
            labelColor             = PTextMuted
        ),
        border   = FilterChipDefaults.filterChipBorder(
            borderColor         = PGlassBorder,
            selectedBorderColor = PBrand,
            borderWidth         = 1.dp,
            selectedBorderWidth = 1.5.dp
        )
    )
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun previewCategoryEmoji(category: String) = when (category.trim().lowercase()) {
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
