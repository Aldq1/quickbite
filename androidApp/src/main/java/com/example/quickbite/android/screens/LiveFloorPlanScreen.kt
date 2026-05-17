package com.example.quickbite.android.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.TableRestaurant
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quickbite.models.TableStatus
import com.google.firebase.firestore.FirebaseFirestore

private val FLPBrand       = Color(0xFFE8430A)
private val FLPDarkBg      = Color(0xFF0C0C0C)
private val FLPSurface     = Color(0xFF161616)
private val FLPTextPrimary = Color(0xFFFFFFFF)
private val FLPTextMuted   = Color(0xFF9A9A9A)
private val FLPGreen       = Color(0xFF34C759)
private val FLPRed         = Color(0xFFFF3B30)
private val FLPWhite       = Color(0xFFFFFFFF)

private const val FLP_TABLE_COUNT = 20
private const val FLP_GRID_COLS   = 4

// ── Root screen ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveFloorPlanScreen(
    restaurantId: String,
    onNavigateToOrdering: (restaurantId: String, tableNumber: Int) -> Unit,
    onBack: () -> Unit
) {
    var tableStatuses  by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var restaurantName by remember { mutableStateOf("") }
    var isConnected    by remember { mutableStateOf(false) }
    // Non-null = show dialog for this (tableNumber, status) pair
    var tappedTable    by remember { mutableStateOf<Pair<Int, String>?>(null) }

    // Zero-latency real-time listener
    DisposableEffect(restaurantId) {
        val reg = FirebaseFirestore.getInstance()
            .collection("users").document(restaurantId)
            .collection("tables")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot == null) return@addSnapshotListener
                tableStatuses = buildMap {
                    snapshot.documents.forEach { doc ->
                        val tableNum = doc.getLong("tableNumber")?.toInt() ?: return@forEach
                        val status   = doc.getString("status") ?: TableStatus.FREE
                        put(tableNum, status)
                    }
                }
                isConnected = true
            }
        onDispose { reg.remove() }
    }

    // Fetch restaurant display name once
    LaunchedEffect(restaurantId) {
        FirebaseFirestore.getInstance()
            .collection("users").document(restaurantId)
            .collection("restaurant_profile").document("details")
            .get()
            .addOnSuccessListener { doc ->
                restaurantName = doc.getString("restaurantName")?.takeIf { it.isNotBlank() } ?: ""
            }
    }

    // ── Table tap dialog ─────────────────────────────────────────────────────
    tappedTable?.let { (tableNum, status) ->
        TableStatusDialog(
            tableNumber = tableNum,
            isFree      = status != TableStatus.OCCUPIED,
            onDismiss   = { tappedTable = null },
            onOrder     = {
                tappedTable = null
                onNavigateToOrdering(restaurantId, tableNum)
            }
        )
    }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FLPBrand)
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 10.dp)
            ) {
                IconButton(
                    onClick  = onBack,
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Înapoi", tint = FLPWhite)
                }

                Column(
                    modifier              = Modifier.align(Alignment.Center),
                    horizontalAlignment   = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Mese Disponibile",
                        color      = FLPWhite,
                        fontSize   = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (restaurantName.isNotBlank()) {
                        Text(
                            restaurantName,
                            color    = FLPWhite.copy(alpha = 0.80f),
                            fontSize = 12.sp
                        )
                    }
                }

                // Live connection indicator
                Row(
                    modifier              = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val dotColor by animateColorAsState(
                        targetValue   = if (isConnected) FLPGreen else FLPWhite.copy(alpha = 0.40f),
                        animationSpec = tween(600),
                        label         = "liveDot"
                    )
                    Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(dotColor))
                    Text(
                        text       = if (isConnected) "Live" else "...",
                        color      = FLPWhite.copy(alpha = 0.85f),
                        fontSize   = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        containerColor = FLPDarkBg
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Legend + availability summary ─────────────────────────────────
            val freeCount = (1..FLP_TABLE_COUNT).count { num ->
                (tableStatuses[num] ?: TableStatus.FREE) == TableStatus.FREE
            }

            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .background(FLPSurface)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FLPLegendDot(color = FLPGreen, label = "Liberă")
                Spacer(Modifier.width(20.dp))
                FLPLegendDot(color = FLPRed,   label = "Ocupată")
                Spacer(Modifier.weight(1f))
                Text(
                    text       = "$freeCount / $FLP_TABLE_COUNT libere",
                    fontSize   = 12.sp,
                    color      = FLPTextMuted,
                    fontWeight = FontWeight.Medium
                )
            }

            // ── Table grid ────────────────────────────────────────────────────
            LazyVerticalGrid(
                columns               = GridCells.Fixed(FLP_GRID_COLS),
                contentPadding        = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement   = Arrangement.spacedBy(10.dp),
                modifier              = Modifier.fillMaxSize()
            ) {
                items((1..FLP_TABLE_COUNT).toList()) { tableNum ->
                    val status = tableStatuses[tableNum] ?: TableStatus.FREE
                    FLPTableCell(
                        tableNumber = tableNum,
                        status      = status,
                        onClick     = { tappedTable = Pair(tableNum, status) }
                    )
                }
            }
        }
    }
}

// ── Table cell ─────────────────────────────────────────────────────────────────

@Composable
private fun FLPTableCell(tableNumber: Int, status: String, onClick: () -> Unit) {
    val isFree    = status != TableStatus.OCCUPIED
    val cellColor = if (isFree) FLPGreen else FLPRed

    val bgColor by animateColorAsState(
        targetValue   = cellColor.copy(alpha = 0.15f),
        animationSpec = tween(400),
        label         = "cellBg$tableNumber"
    )
    val borderColor by animateColorAsState(
        targetValue   = cellColor.copy(alpha = 0.50f),
        animationSpec = tween(400),
        label         = "cellBorder$tableNumber"
    )

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector        = Icons.Rounded.TableRestaurant,
                contentDescription = null,
                tint               = cellColor,
                modifier           = Modifier.size(22.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text          = tableNumber.toString(),
                fontSize      = 20.sp,
                fontWeight    = FontWeight.ExtraBold,
                color         = FLPTextPrimary,
                letterSpacing = (-0.5).sp
            )
            Text(
                text          = if (isFree) "LIBERĂ" else "OCUPATĂ",
                fontSize      = 8.sp,
                fontWeight    = FontWeight.Black,
                letterSpacing = 0.6.sp,
                color         = cellColor.copy(alpha = 0.85f)
            )
        }
    }
}

// ── Table status dialog ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TableStatusDialog(
    tableNumber: Int,
    isFree: Boolean,
    onDismiss: () -> Unit,
    onOrder: () -> Unit
) {
    val accent = if (isFree) FLPGreen else FLPRed

    if (isFree) {
        AlertDialog(
            onDismissRequest = onDismiss,
            shape            = RoundedCornerShape(24.dp),
            containerColor   = Color(0xFF1A1A1A),
            icon = {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(FLPGreen.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint     = FLPGreen,
                        modifier = Modifier.size(36.dp)
                    )
                }
            },
            title = {
                Text(
                    "Masa $tableNumber este liberă!",
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color      = FLPTextPrimary,
                    textAlign  = TextAlign.Center
                )
            },
            text = {
                Text(
                    "Îndreaptă-te spre masă și scanează codul QR de pe aceasta pentru a plasa comanda.",
                    fontSize   = 14.sp,
                    color      = FLPTextMuted,
                    textAlign  = TextAlign.Center,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick  = onOrder,
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = FLPBrand)
                ) {
                    Icon(Icons.Rounded.TableRestaurant, null, tint = FLPWhite, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Comandă acum", color = FLPWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Înapoi", color = FLPTextMuted)
                }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            shape            = RoundedCornerShape(24.dp),
            containerColor   = Color(0xFF1A1A1A),
            icon = {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(FLPRed.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Cancel,
                        contentDescription = null,
                        tint     = FLPRed,
                        modifier = Modifier.size(36.dp)
                    )
                }
            },
            title = {
                Text(
                    "Masa $tableNumber este ocupată",
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color      = FLPTextPrimary,
                    textAlign  = TextAlign.Center
                )
            },
            text = {
                Text(
                    "Această masă este în prezent ocupată. Te rugăm să alegi o altă masă liberă.",
                    fontSize   = 14.sp,
                    color      = FLPTextMuted,
                    textAlign  = TextAlign.Center,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick  = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = FLPRed.copy(alpha = 0.85f))
                ) {
                    Text("Înțeles", color = FLPWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        )
    }
}

// ── Legend dot ────────────────────────────────────────────────────────────────

@Composable
private fun FLPLegendDot(color: Color, label: String) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
        Text(text = label, fontSize = 12.sp, color = FLPTextMuted)
    }
}
