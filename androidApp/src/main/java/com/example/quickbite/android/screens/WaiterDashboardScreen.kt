package com.example.quickbite.android.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import com.google.firebase.firestore.FirebaseFirestore

private val WBrand       = Color(0xFFE8430A)
private val WTextDark    = Color(0xFF1C1C1E)
private val WTextMuted   = Color(0xFF8A8A8E)
private val WBgSurface   = Color(0xFFF7F7F7)
private val WWhite       = Color(0xFFFFFFFF)
private val WDivider     = Color(0xFFF0F0F0)
private val WSuccess     = Color(0xFF34C759)
private val WActiveTable = Color(0xFFFFF3F0)

private data class ActiveOrder(
    val id: String,
    val tableNumber: Int,
    val items: List<Map<String, Any>>,
    val timestamp: Long
)

// ── Root screen ───────────────────────────────────────────────────────────────
// Note: This is an Android Compose screen. The project has no desktop module;
// a two-panel layout (grid + detail side-by-side) is used on screens >= 720 dp.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaiterDashboardScreen(restaurantId: String) {
    var orders        by remember { mutableStateOf<List<ActiveOrder>>(emptyList()) }
    var selectedOrder by remember { mutableStateOf<ActiveOrder?>(null) }
    var isConnected   by remember { mutableStateOf(false) }

    // Real-time snapshot listener — cleaned up when the composable leaves composition
    DisposableEffect(restaurantId) {
        val reg = FirebaseFirestore.getInstance()
            .collection("active_orders")
            .whereEqualTo("restaurantId", restaurantId)
            .addSnapshotListener { snapshot, _ ->
                isConnected = true
                orders = snapshot?.documents?.mapNotNull { doc ->
                    val status = doc.getString("status") ?: return@mapNotNull null
                    if (status != "PENDING") return@mapNotNull null
                    val tableNumber = (doc.getLong("tableNumber") ?: return@mapNotNull null).toInt()
                    @Suppress("UNCHECKED_CAST")
                    val items = doc.get("items") as? List<Map<String, Any>> ?: emptyList()
                    val ts = doc.getLong("timestamp") ?: 0L
                    ActiveOrder(id = doc.id, tableNumber = tableNumber, items = items, timestamp = ts)
                } ?: emptyList()
                // Auto-clear selection when the order disappears (e.g. delivered elsewhere)
                if (selectedOrder != null && orders.none { it.id == selectedOrder!!.id }) {
                    selectedOrder = null
                }
            }
        onDispose { reg.remove() }
    }

    fun markDelivered(order: ActiveOrder) {
        FirebaseFirestore.getInstance()
            .collection("active_orders")
            .document(order.id)
            .update("status", "DELIVERED")
    }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WBrand)
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "Dashboard Ospătar",
                    color = WWhite,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
                // Live indicator
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isConnected) WSuccess else WTextMuted)
                    )
                    Text(
                        text = if (isConnected) "Live" else "Conectare…",
                        color = WWhite.copy(alpha = 0.9f),
                        fontSize = 13.sp
                    )
                }
            }
        },
        containerColor = WBgSurface
    ) { padding ->
        BoxWithConstraints(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (maxWidth >= 720.dp) {
                // ── Two-panel layout (tablet / landscape) ─────────────────────
                Row(modifier = Modifier.fillMaxSize()) {
                    TableGridPanel(
                        modifier = Modifier
                            .width(340.dp)
                            .fillMaxHeight(),
                        orders = orders,
                        selectedOrder = selectedOrder,
                        onTableClick = { order ->
                            selectedOrder = if (selectedOrder?.id == order?.id) null else order
                        }
                    )
                    Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(WDivider))
                    OrderDetailPanel(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        order = selectedOrder,
                        onMarkDelivered = { markDelivered(it) }
                    )
                }
            } else {
                // ── Single-column layout (phone) ──────────────────────────────
                var showDialog by remember { mutableStateOf(false) }

                Column(modifier = Modifier.fillMaxSize()) {
                    TableGridPanel(
                        modifier = Modifier.fillMaxWidth(),
                        orders = orders,
                        selectedOrder = selectedOrder,
                        onTableClick = { order ->
                            selectedOrder = order
                            if (order != null) showDialog = true
                        }
                    )
                }

                if (showDialog && selectedOrder != null) {
                    val order = selectedOrder!!
                    AlertDialog(
                        onDismissRequest = { showDialog = false },
                        shape = RoundedCornerShape(20.dp),
                        containerColor = WWhite,
                        title = {
                            Text(
                                text = "Masă ${order.tableNumber}",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = WTextDark
                            )
                        },
                        text = { OrderItemsList(order.items) },
                        confirmButton = {
                            Button(
                                onClick = {
                                    markDelivered(order)
                                    showDialog = false
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = WSuccess)
                            ) {
                                Icon(
                                    Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = WWhite,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Marcat Livrat", color = WWhite, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDialog = false }) {
                                Text("Închide", color = WTextMuted)
                            }
                        }
                    )
                }
            }
        }
    }
}

// ── Left panel: table grid ────────────────────────────────────────────────────

@Composable
private fun TableGridPanel(
    modifier: Modifier,
    orders: List<ActiveOrder>,
    selectedOrder: ActiveOrder?,
    onTableClick: (ActiveOrder?) -> Unit
) {
    val activeTableNums = orders.map { it.tableNumber }.toSet()

    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        // Panel header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Harta Meselor",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = WTextDark
            )
            if (orders.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = WBrand
                ) {
                    Text(
                        text = "${orders.size} active",
                        color = WWhite,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Divider(color = WDivider)
        Spacer(Modifier.height(12.dp))

        // 4-column grid of tables (16 tables total)
        val tableRows = (1..16).toList().chunked(4)
        tableRows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { tableNum ->
                    val order = orders.firstOrNull { it.tableNumber == tableNum }
                    val isActive   = order != null
                    val isSelected = selectedOrder?.tableNumber == tableNum
                    TableCell(
                        modifier = Modifier.weight(1f),
                        tableNumber = tableNum,
                        isActive = isActive,
                        isSelected = isSelected,
                        onClick = { onTableClick(order) }
                    )
                }
                // Pad the last row if it's not full
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        // Active orders list below the grid
        if (orders.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Divider(color = WDivider)
            Text(
                text = "COMENZI ACTIVE",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = WTextMuted,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            orders.sortedBy { it.tableNumber }.forEach { order ->
                OrderListRow(
                    order = order,
                    isSelected = selectedOrder?.id == order.id,
                    onClick = {
                        onTableClick(if (selectedOrder?.id == order.id) null else order)
                    }
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Rounded.TableRestaurant,
                        contentDescription = null,
                        tint = WTextMuted,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = "Nicio comandă activă",
                        fontSize = 14.sp,
                        color = WTextMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

// ── Table cell ────────────────────────────────────────────────────────────────

@Composable
private fun TableCell(
    modifier: Modifier,
    tableNumber: Int,
    isActive: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bg = when {
        isSelected -> WBrand
        isActive   -> WActiveTable
        else       -> WBgSurface
    }
    val numColor = when {
        isSelected -> WWhite
        isActive   -> WBrand
        else       -> WTextMuted
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .then(
                if (isActive && !isSelected)
                    Modifier.border(1.5.dp, WBrand, RoundedCornerShape(12.dp))
                else Modifier
            )
            .clickable(enabled = isActive) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = tableNumber.toString(),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = numColor
            )
            if (isActive) {
                Text(
                    text = "NEW",
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = if (isSelected) WWhite.copy(alpha = 0.8f) else WBrand
                )
            }
        }
    }
}

// ── Active order row (list below grid) ───────────────────────────────────────

@Composable
private fun OrderListRow(order: ActiveOrder, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) Color(0xFFFFF0EC) else WWhite)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(WActiveTable),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = order.tableNumber.toString(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = WBrand
                )
            }
            Column {
                Text(
                    text = "Masă ${order.tableNumber}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WTextDark
                )
                Text(
                    text = "${order.items.size} produs${if (order.items.size != 1) "e" else ""}",
                    fontSize = 12.sp,
                    color = WTextMuted
                )
            }
        }
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = WBrand
        ) {
            Text(
                text = "PENDING",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                color = WWhite,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
    Divider(color = WDivider)
}

// ── Right panel: order detail ─────────────────────────────────────────────────

@Composable
private fun OrderDetailPanel(
    modifier: Modifier,
    order: ActiveOrder?,
    onMarkDelivered: (ActiveOrder) -> Unit
) {
    if (order == null) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Rounded.TableRestaurant,
                    contentDescription = null,
                    tint = WTextMuted,
                    modifier = Modifier.size(52.dp)
                )
                Text(
                    text = "Selectează o masă activă",
                    fontSize = 16.sp,
                    color = WTextMuted
                )
                Text(
                    text = "Detaliile comenzii apar aici.",
                    fontSize = 13.sp,
                    color = WTextMuted.copy(alpha = 0.7f)
                )
            }
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFFFF0EC)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = order.tableNumber.toString(),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = WBrand
                )
            }
            Column {
                Text(
                    text = "Masă ${order.tableNumber}",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = WTextDark
                )
                val timeLabel = remember(order.timestamp) {
                    val mins = ((System.currentTimeMillis() - order.timestamp) / 60_000L).toInt()
                    when {
                        order.timestamp == 0L -> "acum"
                        mins < 1              -> "acum câteva secunde"
                        mins == 1             -> "acum 1 min"
                        else                  -> "acum $mins min"
                    }
                }
                Text(text = timeLabel, fontSize = 13.sp, color = WTextMuted)
            }
        }

        Divider(color = WDivider)

        Text(
            text = "PRODUSE COMANDATE",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = WTextMuted
        )

        OrderItemsList(order.items)

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = { onMarkDelivered(order) },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = WSuccess)
        ) {
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = WWhite,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Marcat ca Livrat",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = WWhite
            )
        }
    }
}

// ── Shared: order items list ──────────────────────────────────────────────────

@Composable
private fun OrderItemsList(items: List<Map<String, Any>>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (items.isEmpty()) {
            Text("Nicio linie de comandă.", fontSize = 14.sp, color = WTextMuted)
            return@Column
        }
        items.forEach { item ->
            val name = item["name"] as? String ?: return@forEach
            val qty = when (val q = item["quantity"]) {
                is Long -> q.toInt()
                is Int  -> q
                else    -> 1
            }
            val category = item["category"] as? String ?: ""

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(WBgSurface)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = WTextDark
                    )
                    if (category.isNotBlank()) {
                        Text(text = category, fontSize = 11.sp, color = WTextMuted)
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(WBrand.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "×$qty",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = WBrand
                    )
                }
            }
        }
    }
}
