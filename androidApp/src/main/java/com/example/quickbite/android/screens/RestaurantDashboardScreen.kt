package com.example.quickbite.android.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

private val Brand        = Color(0xFFE8430A)
private val TextDark     = Color(0xFF1C1C1E)
private val TextMuted    = Color(0xFF8A8A8E)
private val BgSurface    = Color(0xFFF7F7F7)
private val White        = Color(0xFFFFFFFF)
private val DividerColor = Color(0xFFF0F0F0)
private val RedAlert     = Color(0xFFE53935)

private val culinarySpecifics = listOf(
    "Românesc", "Libanez", "Italian", "Japonez",
    "Fast-food", "Vegan", "Grill", "Seafood", "Mexican", "Indian"
)

private data class Ingredient(val name: String, val weight: String)
private data class MenuItem(
    val category: String,
    val product: String,
    val ingredients: List<Ingredient>
)

// ── Root screen ───────────────────────────────────────────────────────────────

@Composable
fun RestaurantDashboardScreen(onSignOut: () -> Unit = {}) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Profil Locație", "Generator Meniu", "Setări")

    Scaffold(
        topBar = {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brand)
                        .statusBarsPadding()
                        .padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
                ) {
                    Text(
                        text = "Dashboard Restaurant",
                        color = White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.CenterStart).padding(vertical = 8.dp)
                    )
                    IconButton(
                        onClick = onSignOut,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ExitToApp,
                            contentDescription = "Deconectare",
                            tint = White
                        )
                    }
                }
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = White,
                    contentColor = Brand
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = title,
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }
            }
        },
        containerColor = BgSurface
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when (selectedTab) {
                0 -> ProfilLocatieTab()
                1 -> GeneratorMeniuTab()
                2 -> SetariTab(onSignOut = onSignOut)
            }
        }
    }
}

// ── Tab 1: Profil Locație ─────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ProfilLocatieTab() {
    val context = LocalContext.current
    var restaurantName    by remember { mutableStateOf("") }
    var address           by remember { mutableStateOf("") }
    var selectedSpecifics by remember { mutableStateOf(setOf<String>()) }
    var hasWifi           by remember { mutableStateOf(false) }
    var hasCatering       by remember { mutableStateOf(false) }
    var hasLiveMusic      by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        DashboardCard(title = "Informații Generale") {
            OutlinedTextField(
                value = restaurantName,
                onValueChange = { restaurantName = it },
                label = { Text("Nume Restaurant") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = brandTextFieldColors()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text("Adresă") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 3,
                colors = brandTextFieldColors()
            )
        }

        DashboardCard(title = "Specific Culinar") {
            Text(
                text = "Selectează tipurile de bucătărie",
                fontSize = 13.sp,
                color = TextMuted,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                culinarySpecifics.forEach { spec ->
                    val selected = spec in selectedSpecifics
                    FilterChip(
                        selected = selected,
                        onClick = {
                            selectedSpecifics = if (selected)
                                selectedSpecifics - spec
                            else
                                selectedSpecifics + spec
                        },
                        label = { Text(spec, fontSize = 13.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Brand,
                            selectedLabelColor = White
                        )
                    )
                }
            }
        }

        DashboardCard(title = "Servicii Disponibile") {
            ServiceSwitchRow(
                label = "Wi-Fi",
                description = "Acces internet gratuit pentru clienți",
                checked = hasWifi,
                onCheckedChange = { hasWifi = it }
            )
            Divider(modifier = Modifier.padding(vertical = 10.dp), color = DividerColor)
            ServiceSwitchRow(
                label = "Catering",
                description = "Servicii de catering pentru evenimente",
                checked = hasCatering,
                onCheckedChange = { hasCatering = it }
            )
            Divider(modifier = Modifier.padding(vertical = 10.dp), color = DividerColor)
            ServiceSwitchRow(
                label = "Muzică Live",
                description = "Evenimente cu artiști live",
                checked = hasLiveMusic,
                onCheckedChange = { hasLiveMusic = it }
            )
        }

        Button(
            onClick = {
                val uid = FirebaseAuth.getInstance().currentUser?.uid
                if (uid == null) {
                    Toast.makeText(context, "Utilizator neautentificat.", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                val data: Map<String, Any> = mapOf(
                    "restaurantName" to restaurantName,
                    "address" to address,
                    "specifics" to selectedSpecifics.toList(),
                    "services" to mapOf(
                        "wifi" to hasWifi,
                        "catering" to hasCatering,
                        "liveMusic" to hasLiveMusic
                    )
                )
                FirebaseFirestore.getInstance()
                    .collection("users").document(uid)
                    .collection("restaurant_profile").document("details")
                    .set(data)
                    .addOnSuccessListener {
                        Toast.makeText(context, "Profil salvat cu succes!", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e: Exception ->
                        Toast.makeText(context, "Eroare: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Brand)
        ) {
            Text(
                text = "Salvează Profilul",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = White
            )
        }

        Spacer(Modifier.height(8.dp))
    }
}

// ── Tab 2: Generator Meniu ────────────────────────────────────────────────────

@Composable
private fun GeneratorMeniuTab() {
    val context = LocalContext.current
    var categoryInput      by remember { mutableStateOf("") }
    var productInput       by remember { mutableStateOf("") }
    var priceInput         by remember { mutableStateOf("") }
    var ingredientName     by remember { mutableStateOf("") }
    var ingredientWeight   by remember { mutableStateOf("") }
    var currentIngredients by remember { mutableStateOf(listOf<Ingredient>()) }
    var menuItems          by remember { mutableStateOf(listOf<MenuItem>()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Builder card ──────────────────────────────────────────────────────
        DashboardCard(title = "Adaugă Element în Meniu") {
            OutlinedTextField(
                value = categoryInput,
                onValueChange = { categoryInput = it },
                label = { Text("Categorie (ex: Pizza, Burgeri)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = brandTextFieldColors()
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = productInput,
                onValueChange = { productInput = it },
                label = { Text("Denumire Produs (ex: Carbonara)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = brandTextFieldColors()
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = priceInput,
                onValueChange = { v ->
                    if (v.isEmpty() || v.matches(Regex("^\\d{0,5}(\\.\\d{0,2})?\$"))) priceInput = v
                },
                label = { Text("Preț (RON)") },
                placeholder = { Text("ex: 32.50") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                ),
                colors = brandTextFieldColors()
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Ingrediente",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextDark
                )
                if (currentIngredients.isNotEmpty()) {
                    Text(
                        text = "${currentIngredients.size} adăugate",
                        fontSize = 12.sp,
                        color = Brand,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Ingredient input row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = ingredientName,
                    onValueChange = { ingredientName = it },
                    label = { Text("Ingredient") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = brandTextFieldColors()
                )
                OutlinedTextField(
                    value = ingredientWeight,
                    onValueChange = { ingredientWeight = it },
                    label = { Text("Cant.") },
                    modifier = Modifier.width(82.dp),
                    singleLine = true,
                    colors = brandTextFieldColors()
                )
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (ingredientName.isNotBlank() && ingredientWeight.isNotBlank())
                                Brand else Brand.copy(alpha = 0.35f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = {
                            if (ingredientName.isNotBlank() && ingredientWeight.isNotBlank()) {
                                currentIngredients = currentIngredients +
                                        Ingredient(ingredientName.trim(), ingredientWeight.trim())
                                ingredientName = ""
                                ingredientWeight = ""
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Adaugă ingredient",
                            tint = White
                        )
                    }
                }
            }

            // Added ingredients list
            if (currentIngredients.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    currentIngredients.forEachIndexed { index, ingredient ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(BgSurface)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(Brand)
                                )
                                Text(
                                    text = ingredient.name,
                                    fontSize = 14.sp,
                                    color = TextDark,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = ingredient.weight,
                                    fontSize = 13.sp,
                                    color = Brand,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(
                                    onClick = {
                                        currentIngredients = currentIngredients
                                            .toMutableList()
                                            .also { it.removeAt(index) }
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Elimină",
                                        tint = TextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    val uid = FirebaseAuth.getInstance().currentUser?.uid
                    if (uid == null) {
                        Toast.makeText(context, "Utilizator neautentificat.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (categoryInput.isBlank() || productInput.isBlank()) return@Button
                    val savedCategory    = categoryInput.trim()
                    val savedProduct     = productInput.trim()
                    val savedPrice       = priceInput.toDoubleOrNull() ?: 0.0
                    val savedIngredients = currentIngredients.toList()
                    val ingredientsList  = savedIngredients.map {
                        mapOf("name" to it.name, "weight" to it.weight)
                    }
                    val data: Map<String, Any> = buildMap {
                        put("category",    savedCategory)
                        put("product",     savedProduct)
                        put("ingredients", ingredientsList)
                        if (savedPrice > 0.0) put("price", savedPrice)
                    }
                    FirebaseFirestore.getInstance()
                        .collection("users").document(uid)
                        .collection("restaurant_menu")
                        .add(data)
                        .addOnSuccessListener {
                            Toast.makeText(context, "Produs salvat cu succes!", Toast.LENGTH_SHORT).show()
                            menuItems = menuItems + MenuItem(
                                category    = savedCategory,
                                product     = savedProduct,
                                ingredients = savedIngredients
                            )
                            categoryInput      = ""
                            productInput       = ""
                            priceInput         = ""
                            currentIngredients = emptyList()
                        }
                        .addOnFailureListener { e: Exception ->
                            Toast.makeText(context, "Eroare: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                },
                enabled = categoryInput.isNotBlank() && productInput.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Brand,
                    disabledContainerColor = Brand.copy(alpha = 0.35f)
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    tint = White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Salvează Produsul",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = White
                )
            }
        }

        // ── Saved menu items grouped by category ──────────────────────────────
        if (menuItems.isNotEmpty()) {
            Text(
                text = "Meniu (${menuItems.size} ${if (menuItems.size == 1) "produs" else "produse"})",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark,
                modifier = Modifier.padding(top = 4.dp)
            )
            menuItems.groupBy { it.category }.forEach { (category, items) ->
                DashboardCard(title = category) {
                    items.forEachIndexed { index, item ->
                        if (index > 0) {
                            Divider(
                                modifier = Modifier.padding(vertical = 10.dp),
                                color = DividerColor
                            )
                        }
                        MenuItemRow(item = item, onDelete = { menuItems = menuItems - item })
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

// ── Tab 3: Setări ─────────────────────────────────────────────────────────────

@Composable
private fun SetariTab(onSignOut: () -> Unit = {}) {
    var notificationsEnabled by remember { mutableStateOf(true) }
    var emailReports         by remember { mutableStateOf(false) }
    var autoAcceptOrders     by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        DashboardCard(title = "Notificări") {
            ServiceSwitchRow(
                label = "Notificări Comenzi",
                description = "Alertă la fiecare comandă nouă",
                checked = notificationsEnabled,
                onCheckedChange = { notificationsEnabled = it }
            )
            Divider(modifier = Modifier.padding(vertical = 10.dp), color = DividerColor)
            ServiceSwitchRow(
                label = "Rapoarte Email",
                description = "Rezumat zilnic al activității",
                checked = emailReports,
                onCheckedChange = { emailReports = it }
            )
        }

        DashboardCard(title = "Comenzi") {
            ServiceSwitchRow(
                label = "Acceptare Automată",
                description = "Confirmă comenzile fără intervenție manuală",
                checked = autoAcceptOrders,
                onCheckedChange = { autoAcceptOrders = it }
            )
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = onSignOut,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.5.dp, RedAlert.copy(alpha = 0.5f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = RedAlert)
        ) {
            Icon(
                imageVector = Icons.Rounded.ExitToApp,
                contentDescription = null,
                tint = RedAlert,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Deconectare",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = RedAlert
            )
        }

        Spacer(Modifier.height(8.dp))
    }
}

// ── Shared composables ────────────────────────────────────────────────────────

@Composable
private fun DashboardCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark
            )
            Divider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = DividerColor
            )
            content()
        }
    }
}

@Composable
private fun ServiceSwitchRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextDark)
            Text(description, fontSize = 12.sp, color = TextMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = White,
                checkedTrackColor = Brand
            )
        )
    }
}

@Composable
private fun MenuItemRow(item: MenuItem, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = item.product,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextDark
            )
            if (item.ingredients.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = item.ingredients.joinToString(" · ") { "${it.name} ${it.weight}" },
                    fontSize = 12.sp,
                    color = TextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = "Șterge produs",
                tint = RedAlert,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun brandTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Brand,
    focusedLabelColor = Brand,
    cursorColor = Brand
)