package com.example.quickbite.android.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Brand      = Color(0xFFE8430A)
private val BrandLight = Color(0xFFFF7043)
private val TextDark   = Color(0xFF1C1C1E)
private val TextMuted  = Color(0xFF8A8A8E)
private val Surface    = Color(0xFFF7F7F7)
private val White      = Color(0xFFFFFFFF)
private val CardBg     = Color(0xFFFFFFFF)
private val StarYellow = Color(0xFFFFC107)

private data class Category(val emoji: String, val label: String)
private data class Restaurant(
    val name: String,
    val cuisine: String,
    val rating: Double,
    val deliveryTime: String,
    val deliveryFee: String,
    val emoji: String,
    val tag: String
)

private val categories = listOf(
    Category("🍔", "Burger"),
    Category("🍕", "Pizza"),
    Category("🍣", "Sushi"),
    Category("🌮", "Tacos"),
    Category("🍜", "Noodles"),
    Category("🥗", "Salate"),
    Category("🍦", "Desert"),
    Category("🥤", "Băuturi"),
)

private val restaurants = listOf(
    Restaurant("Burger Palace",  "Burgeri americani",  4.8, "20-30 min", "Gratuit",  "🍔", "Nou"),
    Restaurant("La Bella Pizza", "Pizza italiană",     4.6, "25-35 min", "5 lei",    "🍕", "Popular"),
    Restaurant("Tokyo Sushi",    "Bucătărie japoneză", 4.9, "30-40 min", "Gratuit",  "🍣", "Top"),
    Restaurant("TacoLoco",       "Mexican autentic",   4.4, "15-25 min", "8 lei",    "🌮", "Rapid"),
    Restaurant("Pho Garden",     "Bucătărie vietnameză",4.7,"35-45 min", "10 lei",   "🍜", ""),
    Restaurant("Green Bowl",     "Salate & Healthy",   4.5, "20-30 min", "Gratuit",  "🥗", "Healthy"),
)

@Composable
fun HomeScreen() {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Burger") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface)
            .verticalScroll(rememberScrollState())
    ) {
        // ── Top bar with gradient ─────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Brand, BrandLight)))
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Livrare la",
                            color = White.copy(alpha = 0.80f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.LocationOn,
                                contentDescription = null,
                                tint = White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "Strada Florilor 12, Cluj",
                                color = White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(White.copy(alpha = 0.20f))
                            .clickable { },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = "Profil",
                            tint = White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Search bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(White)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = TextDark,
                                fontSize = 14.sp
                            ),
                            decorationBox = { inner ->
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Caută restaurant sau mâncare…",
                                        color = TextMuted,
                                        fontSize = 14.sp
                                    )
                                }
                                inner()
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        // ── Promo banner ──────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFF1C1C1E), Color(0xFF3A3A3C))))
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "30% reducere",
                        color = Brand,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Prima ta comandă\neste specială!",
                        color = White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 24.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Brand)
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = "Comandă acum",
                            color = White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(text = "🍔", fontSize = 64.sp)
            }
        }

        // ── Categories ────────────────────────────────────────────────────────
        SectionHeader(title = "Categorii", actionLabel = "Vezi tot")

        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            categories.forEach { cat ->
                CategoryChip(
                    category = cat,
                    selected = selectedCategory == cat.label,
                    onClick = { selectedCategory = cat.label }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Popular restaurants ───────────────────────────────────────────────
        SectionHeader(title = "Restaurante populare", actionLabel = "Vezi tot")

        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            restaurants.forEach { restaurant ->
                RestaurantCard(restaurant = restaurant)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SectionHeader(title: String, actionLabel: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark
        )
        Text(
            text = actionLabel,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Brand
        )
    }
}

@Composable
private fun CategoryChip(category: Category, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .then(
                if (selected)
                    Modifier.background(Brand)
                else
                    Modifier
                        .background(White)
                        .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(14.dp))
            )
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(text = category.emoji, fontSize = 22.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = category.label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) White else TextDark
        )
    }
}

@Composable
private fun RestaurantCard(restaurant: Restaurant) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(18.dp), clip = false)
            .clip(RoundedCornerShape(18.dp))
            .background(CardBg)
            .clickable { }
    ) {
        Column {
            // Hero area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF2C2C2E), Color(0xFF3A3A3C))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(text = restaurant.emoji, fontSize = 64.sp)

                if (restaurant.tag.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Brand)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = restaurant.tag,
                            color = White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Info area
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = restaurant.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = null,
                            tint = StarYellow,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = restaurant.rating.toString(),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = restaurant.cuisine,
                    fontSize = 13.sp,
                    color = TextMuted
                )

                Spacer(modifier = Modifier.height(10.dp))
                Divider(color = Color(0xFFF0F0F0))
                Spacer(modifier = Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    InfoPill(emoji = "⏱", text = restaurant.deliveryTime)
                    InfoPill(emoji = "🛵", text = restaurant.deliveryFee)
                }
            }
        }
    }
}

@Composable
private fun InfoPill(emoji: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = emoji, fontSize = 13.sp)
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = text, fontSize = 12.sp, color = TextMuted, fontWeight = FontWeight.Medium)
    }
}