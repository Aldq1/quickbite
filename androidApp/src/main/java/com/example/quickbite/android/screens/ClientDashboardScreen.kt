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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExitToApp
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.TableRestaurant
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore

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

// ── Data model ────────────────────────────────────────────────────────────────

private data class ClientMenuItem(
    val product: String,
    val category: String,
    val ingredients: List<Map<String, String>>,
    val restaurantId: String,
    val restaurantName: String = "",
    val price: Double = 0.0
)

private sealed interface MenuUiState {
    object Loading : MenuUiState
    object Empty : MenuUiState
    data class Success(val items: List<ClientMenuItem>) : MenuUiState
}

// ── Root screen ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientDashboardScreen(
    onNavigateToOrdering: (restaurantId: String, tableNumber: Int) -> Unit,
    onViewFloorPlan: (restaurantId: String) -> Unit,
    onSignOut: () -> Unit
) {
    var uiState             by remember { mutableStateOf<MenuUiState>(MenuUiState.Loading) }
    var showQrDialog        by remember { mutableStateOf(false) }
    var showFloorPlanPicker by remember { mutableStateOf(false) }
    var restaurantIds       by remember { mutableStateOf<List<String>>(emptyList()) }
    var restaurantNames     by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var selectedCategory    by remember { mutableStateOf("Toate") }

    LaunchedEffect(Unit) {
        FirebaseFirestore.getInstance()
            .collectionGroup("restaurant_menu")
            .get()
            .addOnSuccessListener { snapshot ->
                val rawItems = snapshot.documents.mapNotNull { doc ->
                    val product      = doc.getString("product")  ?: return@mapNotNull null
                    val category     = doc.getString("category") ?: ""
                    val restaurantId = doc.reference.parent.parent?.id ?: return@mapNotNull null
                    @Suppress("UNCHECKED_CAST")
                    val ingredients  = doc.get("ingredients") as? List<Map<String, String>> ?: emptyList()
                    val price        = doc.getDouble("price") ?: 0.0
                    ClientMenuItem(product, category, ingredients, restaurantId, price = price)
                }

                val uniqueIds = rawItems.map { it.restaurantId }.distinct()
                restaurantIds = uniqueIds

                if (rawItems.isEmpty()) { uiState = MenuUiState.Empty; return@addOnSuccessListener }

                val nameMap = mutableMapOf<String, String>()
                var pending = uniqueIds.size

                fun publish() {
                    val enriched = rawItems.map { item ->
                        item.copy(restaurantName = nameMap[item.restaurantId] ?: item.restaurantId.take(8))
                    }
                    restaurantNames = nameMap.toMap()
                    uiState = MenuUiState.Success(enriched)
                }

                uniqueIds.forEach { rid ->
                    FirebaseFirestore.getInstance()
                        .collection("users").document(rid)
                        .collection("restaurant_profile").document("details")
                        .get()
                        .addOnCompleteListener { task ->
                            nameMap[rid] = if (task.isSuccessful)
                                task.result?.getString("restaurantName")?.takeIf { it.isNotBlank() } ?: rid.take(8)
                            else rid.take(8)
                            pending--
                            if (pending == 0) publish()
                        }
                }
            }
            .addOnFailureListener { uiState = MenuUiState.Empty }
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
                Row(modifier = Modifier.align(Alignment.CenterEnd)) {
                    if (restaurantIds.isNotEmpty()) {
                        IconButton(onClick = {
                            if (restaurantIds.size == 1) onViewFloorPlan(restaurantIds[0])
                            else showFloorPlanPicker = true
                        }) {
                            Icon(Icons.Rounded.TableRestaurant, contentDescription = "Mese disponibile", tint = White)
                        }
                    }
                    IconButton(onClick = onSignOut) {
                        Icon(Icons.Rounded.ExitToApp, contentDescription = "Deconectare", tint = White)
                    }
                }
            }
        },
        floatingActionButton = {
            if (uiState is MenuUiState.Success) {
                FloatingActionButton(
                    onClick = { showQrDialog = true },
                    containerColor = Brand,
                    contentColor = White,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Rounded.QrCodeScanner, contentDescription = "Scanează QR / Comandă")
                }
            }
        },
        containerColor = DarkBg
    ) { padding ->
        when (val state = uiState) {
            is MenuUiState.Loading -> Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = Brand) }

            is MenuUiState.Empty -> Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { EmptyMenuState() }

            is MenuUiState.Success -> {
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

    if (showFloorPlanPicker) {
        RestaurantPickerDialog(
            restaurantIds   = restaurantIds,
            restaurantNames = restaurantNames,
            onDismiss       = { showFloorPlanPicker = false },
            onConfirm       = { rid ->
                showFloorPlanPicker = false
                onViewFloorPlan(rid)
            }
        )
    }

    if (showQrDialog) {
        QrScanDialog(
            restaurantIds = restaurantIds,
            onDismiss     = { showQrDialog = false },
            onConfirm     = { rid, tableNum ->
                showQrDialog = false
                onNavigateToOrdering(rid, tableNum)
            }
        )
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
            Divider(color = GlassDivider)
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

// ── QR simulation dialog ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QrScanDialog(
    restaurantIds: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (restaurantId: String, tableNumber: Int) -> Unit
) {
    var selectedRestaurantId by remember { mutableStateOf(restaurantIds.firstOrNull() ?: "") }
    var tableInput           by remember { mutableStateOf("1") }
    var expanded             by remember { mutableStateOf(false) }

    val tableNumber = tableInput.toIntOrNull()?.coerceIn(1, 99) ?: 1

    // Dialog surfaces in dark glass style
    val dialogBg     = Color(0xFF1A1A1A)
    val dialogBorder = Color(0xFF2A2A2A)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = dialogBg,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Simulare Scanare QR", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text("Alege restaurantul și numărul mesei.", fontSize = 13.sp, color = TextSecondary)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

                if (restaurantIds.size > 1) {
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = selectedRestaurantId.take(20) + if (selectedRestaurantId.length > 20) "…" else "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Restaurant", color = TextSecondary) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor   = Brand,
                                unfocusedBorderColor = dialogBorder,
                                focusedLabelColor    = Brand,
                                unfocusedTextColor   = TextPrimary,
                                focusedTextColor     = TextPrimary
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.background(dialogBg)
                        ) {
                            restaurantIds.forEachIndexed { i, rid ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("Restaurant ${i + 1}", fontSize = 14.sp, color = TextPrimary)
                                            Text(
                                                rid.take(24) + if (rid.length > 24) "…" else "",
                                                fontSize = 11.sp, color = TextSecondary
                                            )
                                        }
                                    },
                                    onClick = { selectedRestaurantId = rid; expanded = false }
                                )
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = selectedRestaurantId,
                        onValueChange = { selectedRestaurantId = it },
                        label = { Text("ID Restaurant", color = TextSecondary) },
                        placeholder = { Text("UID-ul restaurantului", color = TextSecondary) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = Brand,
                            unfocusedBorderColor = dialogBorder,
                            focusedLabelColor    = Brand,
                            unfocusedTextColor   = TextPrimary,
                            focusedTextColor     = TextPrimary,
                            cursorColor          = Brand
                        )
                    )
                }

                OutlinedTextField(
                    value = tableInput,
                    onValueChange = { v ->
                        if (v.all { it.isDigit() } && v.length <= 2) tableInput = v
                    },
                    label = { Text("Număr masă (1–99)", color = TextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = Brand,
                        unfocusedBorderColor = dialogBorder,
                        focusedLabelColor    = Brand,
                        unfocusedTextColor   = TextPrimary,
                        focusedTextColor     = TextPrimary,
                        cursorColor          = Brand
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedRestaurantId.trim(), tableNumber) },
                enabled = selectedRestaurantId.isNotBlank() && tableInput.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Brand)
            ) {
                Text("Comandă", color = White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Anulează", color = TextSecondary) }
        }
    )
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

// ── Restaurant picker dialog (for multi-restaurant floor plan nav) ─────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RestaurantPickerDialog(
    restaurantIds: List<String>,
    restaurantNames: Map<String, String>,
    onDismiss: () -> Unit,
    onConfirm: (restaurantId: String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape          = RoundedCornerShape(24.dp),
        containerColor = Color(0xFF1A1A1A),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Alege Restaurantul",
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color      = TextPrimary
                )
                Text(
                    "Selectează restaurantul pentru a vedea mesele disponibile.",
                    fontSize = 13.sp,
                    color    = TextSecondary
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                restaurantIds.forEachIndexed { index, rid ->
                    val name = restaurantNames[rid]?.takeIf { it.isNotBlank() }
                        ?: "Restaurant ${index + 1}"
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(GlassWhite)
                            .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                            .clickable { onConfirm(rid) }
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier         = Modifier.size(36.dp).background(BrandDim, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.Restaurant,
                                    contentDescription = null,
                                    tint     = Brand,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text(
                                    name,
                                    fontSize   = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color      = TextPrimary
                                )
                                Text(
                                    "Atinge pentru a vedea mesele",
                                    fontSize = 11.sp,
                                    color    = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Anulează", color = TextSecondary)
            }
        }
    )
}
