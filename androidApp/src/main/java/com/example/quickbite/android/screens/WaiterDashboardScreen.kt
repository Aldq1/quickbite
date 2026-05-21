package com.example.quickbite.android.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.LocalAtm
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
import com.example.quickbite.android.services.FirestoreService
import com.example.quickbite.models.TableStatus
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

private val WBrand       = Color(0xFFE8430A)
private val WTextDark    = Color(0xFF1C1C1E)
private val WTextMuted   = Color(0xFF8A8A8E)
private val WBgSurface   = Color(0xFFF7F7F7)
private val WWhite       = Color(0xFFFFFFFF)
private val WDivider     = Color(0xFFF0F0F0)
private val WSuccess     = Color(0xFF34C759)
private val WActiveTable = Color(0xFFFFF3F0)
private val WError       = Color(0xFFFF3B30)

private data class ActiveOrder(
    val id: String,
    val tableNumber: Int,
    val items: List<Map<String, Any>>,
    val timestamp: Long,
    val occupantUid: String? = null,
    val status: String = "PENDING"
)

private data class RecentOrder(
    val id: String,
    val tableNumber: Int,
    val items: List<Map<String, Any>>,
    val totalPrice: Double,
    val status: String,          // "COMPLETED" or "CANCELLED"
    val timestamp: Long,
    val cancelReason: String? = null,
    val staffNote: String? = null
)

// ── Root screen ───────────────────────────────────────────────────────────────
// Note: This is an Android Compose screen. The project has no desktop module;
// a two-panel layout (grid + detail side-by-side) is used on screens >= 720 dp.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaiterDashboardScreen(restaurantId: String) {
    val scope            = rememberCoroutineScope()
    var orders           by remember { mutableStateOf<List<ActiveOrder>>(emptyList()) }
    var selectedOrder    by remember { mutableStateOf<ActiveOrder?>(null) }
    var isConnected      by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }
    var recentOrders     by remember { mutableStateOf<List<RecentOrder>>(emptyList()) }
    var selectedTab      by remember { mutableStateOf(0) }
    var issueOrder              by remember { mutableStateOf<RecentOrder?>(null) }
    var showFinishBlockedDialog by remember { mutableStateOf(false) }

    // Real-time snapshot listener — cleaned up when the composable leaves composition
    DisposableEffect(restaurantId) {
        val reg = FirebaseFirestore.getInstance()
            .collection("active_orders")
            .whereEqualTo("restaurantId", restaurantId)
            .addSnapshotListener { snapshot, _ ->
                isConnected = true
                orders = snapshot?.documents?.mapNotNull { doc ->
                    val status = doc.getString("status") ?: return@mapNotNull null
                    if (status !in setOf("PENDING", "COOKING", "READY", "COMPLETED")) return@mapNotNull null
                    val tableNumber = (doc.getLong("tableNumber") ?: return@mapNotNull null).toInt()
                    @Suppress("UNCHECKED_CAST")
                    val items = doc.get("items") as? List<Map<String, Any>> ?: emptyList()
                    val ts = doc.getLong("timestamp") ?: 0L
                    ActiveOrder(id = doc.id, tableNumber = tableNumber, items = items, timestamp = ts, occupantUid = doc.getString("occupantUid"), status = status)
                } ?: emptyList()
                // Auto-clear selection when the order disappears (e.g. delivered elsewhere)
                val current = selectedOrder
                if (current != null && orders.none { it.id == current.id }) {
                    selectedOrder = null
                }
            }
        onDispose { reg.remove() }
    }

    // History listener — same collection, different status filter, client-side time window + sort.
    // No composite index needed: single-field whereEqualTo + client-side post-filter.
    DisposableEffect(restaurantId) {
        val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
        val reg = FirebaseFirestore.getInstance()
            .collection("active_orders")
            .whereEqualTo("restaurantId", restaurantId)
            .addSnapshotListener { snapshot, _ ->
                recentOrders = snapshot?.documents?.mapNotNull { doc ->
                    val status = doc.getString("status") ?: return@mapNotNull null
                    if (status != "COMPLETED" && status != "CANCELLED") return@mapNotNull null
                    val ts = doc.getLong("timestamp") ?: 0L
                    if (ts < cutoff) return@mapNotNull null
                    val tableNumber = (doc.getLong("tableNumber") ?: return@mapNotNull null).toInt()
                    @Suppress("UNCHECKED_CAST")
                    val items = doc.get("items") as? List<Map<String, Any>> ?: emptyList()
                    RecentOrder(
                        id           = doc.id,
                        tableNumber  = tableNumber,
                        items        = items,
                        totalPrice   = doc.getDouble("totalPrice") ?: 0.0,
                        status       = status,
                        timestamp    = ts,
                        cancelReason = doc.getString("cancelReason"),
                        staffNote    = doc.getString("staffNote")
                    )
                }?.sortedByDescending { it.timestamp } ?: emptyList()
            }
        onDispose { reg.remove() }
    }

    fun markDelivered(order: ActiveOrder) {
        scope.launch(Dispatchers.IO) {
            // Set order to COMPLETED so the client's notification listener fires
            suspendCancellableCoroutine { cont ->
                FirebaseFirestore.getInstance()
                    .collection("active_orders")
                    .document(order.id)
                    .update("status", "COMPLETED")
                    .addOnSuccessListener { cont.resumeWith(Result.success(Unit)) }
                    .addOnFailureListener { cont.resumeWith(Result.failure(it)) }
            }
            // Free the table and clear the session lock so new clients can sit down
            FirestoreService.updateTableStatusAsync(
                restaurantId = restaurantId,
                tableNumber  = order.tableNumber,
                status       = TableStatus.FREE,
                occupantUid  = null
            )
        }
    }

    fun cancelOrder(order: ActiveOrder, reason: String, ban: Boolean) {
        scope.launch(Dispatchers.IO) {
            try {
                val db = FirebaseFirestore.getInstance()
                // Ban the client first so they can't re-enter before table is freed
                if (ban && order.occupantUid != null) {
                    suspendCancellableCoroutine { cont ->
                        db.collection("banned_users")
                            .document(order.occupantUid)
                            .set(mapOf(
                                "uid"          to order.occupantUid,
                                "reason"       to reason,
                                "tableNumber"  to order.tableNumber,
                                "restaurantId" to restaurantId,
                                "bannedAt"     to System.currentTimeMillis()
                            ))
                            .addOnSuccessListener { cont.resumeWith(Result.success(Unit)) }
                            .addOnFailureListener { cont.resumeWith(Result.failure(it)) }
                    }
                }
                // Mark order as CANCELLED (client session listener will fire and clear the lock)
                suspendCancellableCoroutine { cont ->
                    db.collection("active_orders")
                        .document(order.id)
                        .update(mapOf("status" to "CANCELLED", "cancelReason" to reason))
                        .addOnSuccessListener { cont.resumeWith(Result.success(Unit)) }
                        .addOnFailureListener { cont.resumeWith(Result.failure(it)) }
                }
                // Free the table
                FirestoreService.updateTableStatusAsync(
                    restaurantId = restaurantId,
                    tableNumber  = order.tableNumber,
                    status       = TableStatus.FREE,
                    occupantUid  = null
                )
            } catch (_: Exception) { /* silent — order snapshot listener will correct state */ }
        }
    }

    fun resolveIssue(order: RecentOrder, note: String, reopen: Boolean) {
        scope.launch(Dispatchers.IO) {
            try {
                val updates = mutableMapOf<String, Any>()
                if (note.isNotBlank()) updates["staffNote"] = note
                if (reopen)           updates["status"]    = "PENDING"
                if (updates.isEmpty()) return@launch
                suspendCancellableCoroutine { cont ->
                    FirebaseFirestore.getInstance()
                        .collection("active_orders")
                        .document(order.id)
                        .update(updates)
                        .addOnSuccessListener { cont.resumeWith(Result.success(Unit)) }
                        .addOnFailureListener { cont.resumeWith(Result.failure(it)) }
                }
            } catch (_: Exception) { }
        }
    }

    fun finishTable(tableNumber: Int) {
        scope.launch(Dispatchers.IO) {
            try {
                val db = FirebaseFirestore.getInstance()
                // Fresh Firestore read — authoritative safety check, not relying on in-memory state
                val snapshot = suspendCancellableCoroutine { cont ->
                    db.collection("active_orders")
                        .whereEqualTo("restaurantId", restaurantId)
                        .whereEqualTo("tableNumber", tableNumber.toLong())
                        .get()
                        .addOnSuccessListener { cont.resumeWith(Result.success(it)) }
                        .addOnFailureListener { cont.resumeWith(Result.failure(it)) }
                }
                val activeDocs = snapshot.documents.filter { doc ->
                    val s = doc.getString("status") ?: return@filter false
                    s !in setOf("ARCHIVED", "CANCELLED")
                }
                val isBlocked = activeDocs.any { doc ->
                    val s = doc.getString("status") ?: ""
                    s == "PENDING" || s == "COOKING"
                }
                if (isBlocked) {
                    showFinishBlockedDialog = true
                    return@launch
                }
                val batch = db.batch()
                activeDocs.forEach { doc ->
                    batch.update(doc.reference, "status", "ARCHIVED")
                }
                val tableRef = db.collection("users")
                    .document(restaurantId)
                    .collection("tables")
                    .document(tableNumber.toString())
                batch.update(tableRef, "status", "FREE", "tableNumber", tableNumber, "occupantUid", null)
                suspendCancellableCoroutine { cont ->
                    batch.commit()
                        .addOnSuccessListener { cont.resumeWith(Result.success(Unit)) }
                        .addOnFailureListener { cont.resumeWith(Result.failure(it)) }
                }
            } catch (_: Exception) { }
        }
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
                    // Left: table map + active order list
                    TableGridPanel(
                        modifier = Modifier
                            .width(300.dp)
                            .fillMaxHeight(),
                        orders = orders,
                        selectedOrder = selectedOrder,
                        onTableClick = { order ->
                            selectedOrder = if (selectedOrder?.id == order?.id) null else order
                            if (order != null) selectedTab = 0  // jump to active tab on table selection
                        }
                    )
                    Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(WDivider))
                    // Right: tabbed panel — Tab 0 = active order, Tab 1 = recent history
                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        TabRow(
                            selectedTabIndex = selectedTab,
                            containerColor   = WWhite,
                            contentColor     = WBrand
                        ) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick  = { selectedTab = 0 },
                                selectedContentColor   = WBrand,
                                unselectedContentColor = WTextMuted
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.TableRestaurant, null, modifier = Modifier.size(15.dp))
                                    Text(
                                        text = "Comandă Activă",
                                        fontSize = 13.sp,
                                        fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                            Tab(
                                selected = selectedTab == 1,
                                onClick  = { selectedTab = 1 },
                                selectedContentColor   = WBrand,
                                unselectedContentColor = WTextMuted
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.Schedule, null, modifier = Modifier.size(15.dp))
                                    Text(
                                        text = "Comenzi Recente",
                                        fontSize = 13.sp,
                                        fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (recentOrders.isNotEmpty()) {
                                        Surface(
                                            shape = RoundedCornerShape(20.dp),
                                            color = WTextMuted.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = recentOrders.size.toString(),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = WTextMuted,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        when (selectedTab) {
                            0 -> OrderDetailPanel(
                                modifier        = Modifier.weight(1f).fillMaxWidth(),
                                order           = selectedOrder,
                                onMarkDelivered = { markDelivered(it) },
                                onCancelOrder   = { showCancelDialog = true },
                                onFinishTable   = { finishTable(it.tableNumber) }
                            )
                            else -> RecentOrdersPanel(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                orders   = recentOrders,
                                onIssue  = { issueOrder = it }
                            )
                        }
                    }
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
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                OrderItemsList(order.items)
                                OutlinedButton(
                                    onClick = { showDialog = false; showCancelDialog = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, WError),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = WError)
                                ) {
                                    Icon(
                                        Icons.Rounded.Block,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Anulează Comanda", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        },
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

    if (showCancelDialog && selectedOrder != null) {
        CancelOrderDialog(
            order     = selectedOrder!!,
            onDismiss = { showCancelDialog = false },
            onConfirm = { reason, ban ->
                cancelOrder(selectedOrder!!, reason, ban)
                showCancelDialog = false
                selectedOrder    = null
            }
        )
    }

    if (issueOrder != null) {
        IssueOrderDialog(
            order     = issueOrder!!,
            onDismiss = { issueOrder = null },
            onConfirm = { note, reopen ->
                resolveIssue(issueOrder!!, note, reopen)
                issueOrder = null
            }
        )
    }

    if (showFinishBlockedDialog) {
        AlertDialog(
            onDismissRequest = { showFinishBlockedDialog = false },
            shape            = RoundedCornerShape(20.dp),
            containerColor   = WWhite,
            icon = {
                Icon(
                    Icons.Rounded.Block,
                    contentDescription = null,
                    tint   = WError,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text       = "Acțiune blocată",
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color      = WError
                )
            },
            text = {
                Text(
                    text     = "Există comenzi nefinalizate la bucătărie! Toate comenzile mesei trebuie să fie FINALIZATE înainte de a încheia sesiunea.",
                    fontSize = 14.sp,
                    color    = WTextDark
                )
            },
            confirmButton = {
                Button(
                    onClick = { showFinishBlockedDialog = false },
                    shape   = RoundedCornerShape(12.dp),
                    colors  = ButtonDefaults.buttonColors(containerColor = WBrand)
                ) {
                    Text("Am înțeles", color = WWhite, fontWeight = FontWeight.Bold)
                }
            }
        )
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
    onMarkDelivered: (ActiveOrder) -> Unit,
    onCancelOrder: (ActiveOrder) -> Unit,
    onFinishTable: (ActiveOrder) -> Unit
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

        Button(
            onClick = { onFinishTable(order) },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00875A))
        ) {
            Icon(
                Icons.Rounded.LocalAtm,
                contentDescription = null,
                tint = WWhite,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Plată Cash - Finalizează Masa",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = WWhite
            )
        }

        OutlinedButton(
            onClick = { onCancelOrder(order) },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, WError),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = WError)
        ) {
            Icon(
                Icons.Rounded.Block,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Anulează Comanda",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ── Recent orders panel ───────────────────────────────────────────────────────

@Composable
private fun RecentOrdersPanel(
    modifier: Modifier,
    orders: List<RecentOrder>,
    onIssue: (RecentOrder) -> Unit
) {
    if (orders.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Rounded.Schedule, null, tint = WTextMuted, modifier = Modifier.size(44.dp))
                Text("Nicio comandă în ultimele 24 de ore", fontSize = 15.sp, color = WTextMuted)
                Text("Comenzile livrate sau anulate apar aici.", fontSize = 13.sp, color = WTextMuted.copy(alpha = 0.6f))
            }
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "ULTIMELE 24 DE ORE · ${orders.size} comenzi",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = WTextMuted,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        orders.forEach { order ->
            RecentOrderRow(order = order, onIssue = { onIssue(order) })
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ── Recent order row ──────────────────────────────────────────────────────────

@Composable
private fun RecentOrderRow(order: RecentOrder, onIssue: () -> Unit) {
    val isCompleted = order.status == "COMPLETED"
    val statusColor = if (isCompleted) WSuccess else WError
    val statusLabel = if (isCompleted) "LIVRAT" else "ANULAT"
    val timeLabel = remember(order.timestamp) {
        val mins = ((System.currentTimeMillis() - order.timestamp) / 60_000L).toInt()
        when {
            mins < 1   -> "acum câteva sec."
            mins < 60  -> "acum $mins min"
            mins < 120 -> "acum 1 oră"
            else       -> "acum ${mins / 60} ore"
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(WWhite)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f).padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Table badge
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(statusColor.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = order.tableNumber.toString(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Masă ${order.tableNumber}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = WTextDark)
                    Surface(shape = RoundedCornerShape(5.dp), color = statusColor.copy(alpha = 0.12f)) {
                        Text(
                            text = statusLabel,
                            fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(timeLabel, fontSize = 12.sp, color = WTextMuted)
                    Text("·", fontSize = 12.sp, color = WTextMuted)
                    Text(
                        "${"%.2f".format(order.totalPrice)} RON",
                        fontSize = 12.sp, color = WTextMuted, fontWeight = FontWeight.Medium
                    )
                    Text("·", fontSize = 12.sp, color = WTextMuted)
                    Text(
                        "${order.items.size} produs${if (order.items.size != 1) "e" else ""}",
                        fontSize = 12.sp, color = WTextMuted
                    )
                }
                if (order.cancelReason != null) {
                    Text(order.cancelReason, fontSize = 11.sp, color = WError.copy(alpha = 0.75f))
                }
                if (order.staffNote != null) {
                    Text("Notă: ${order.staffNote}", fontSize = 11.sp, color = WBrand.copy(alpha = 0.8f))
                }
            }
        }
        OutlinedButton(
            onClick = onIssue,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, WBrand.copy(alpha = 0.35f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = WBrand),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("Rezolvă", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── Issue / edit order dialog ─────────────────────────────────────────────────

@Composable
private fun IssueOrderDialog(
    order: RecentOrder,
    onDismiss: () -> Unit,
    onConfirm: (note: String, reopen: Boolean) -> Unit
) {
    val isCompleted = order.status == "COMPLETED"
    val statusColor = if (isCompleted) WSuccess else WError
    val statusLabel = if (isCompleted) "LIVRAT" else "ANULAT"

    var note   by remember { mutableStateOf(order.staffNote ?: "") }
    var reopen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = WWhite,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Masă ${order.tableNumber}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = WTextDark)
                    Surface(shape = RoundedCornerShape(6.dp), color = statusColor.copy(alpha = 0.12f)) {
                        Text(
                            text = statusLabel,
                            fontSize = 10.sp, fontWeight = FontWeight.Bold, color = statusColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Text(
                    text = "Total: ${"%.2f".format(order.totalPrice)} RON · ${order.items.size} produs${if (order.items.size != 1) "e" else ""}",
                    fontSize = 13.sp, color = WTextMuted
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OrderItemsList(order.items)

                if (order.cancelReason != null) {
                    Text(
                        "Motiv anulare: ${order.cancelReason}",
                        fontSize = 12.sp, color = WError.copy(alpha = 0.8f)
                    )
                }

                Divider(color = WDivider)

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Notă personal (vizibilă intern)", fontSize = 12.sp) },
                    placeholder = { Text("ex: reclamație client, reducere acordată…", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = WBrand,
                        unfocusedBorderColor = WDivider,
                        focusedLabelColor    = WBrand
                    )
                )

                // Reopen toggle — only meaningful for CANCELLED orders
                if (!isCompleted) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (reopen) WSuccess.copy(alpha = 0.07f) else WBgSurface)
                            .border(1.dp, if (reopen) WSuccess.copy(alpha = 0.3f) else WDivider, RoundedCornerShape(10.dp))
                            .clickable { reopen = !reopen }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Checkbox(
                            checked = reopen,
                            onCheckedChange = { reopen = it },
                            colors = CheckboxDefaults.colors(checkedColor = WSuccess, checkmarkColor = WWhite)
                        )
                        Column {
                            Text(
                                "Redeschide comanda",
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                color = if (reopen) WSuccess else WTextDark
                            )
                            Text("Setează statusul înapoi la PENDING", fontSize = 11.sp, color = WTextMuted)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick  = { onConfirm(note, reopen) },
                enabled  = note.isNotBlank() || reopen,
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = if (reopen) WSuccess else WBrand)
            ) {
                Text(
                    text = if (reopen) "Redeschide & Salvează" else "Salvează Notă",
                    color = WWhite, fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Închide", color = WTextMuted) }
        }
    )
}

// ── Cancel & ban dialog ───────────────────────────────────────────────────────

@Composable
private fun CancelOrderDialog(
    order: ActiveOrder,
    onDismiss: () -> Unit,
    onConfirm: (reason: String, ban: Boolean) -> Unit
) {
    val reasons = listOf("Client fugit / Neplată", "Eroare comandă", "Altul")
    var selectedReason by remember { mutableStateOf(reasons[0]) }
    var banClient      by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = WWhite,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Anulează Comanda",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = WTextDark
                )
                Text(
                    text = "Masă ${order.tableNumber}",
                    fontSize = 13.sp,
                    color = WTextMuted
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "MOTIV ANULARE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = WTextMuted
                )
                Spacer(Modifier.height(4.dp))
                reasons.forEach { reason ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { selectedReason = reason }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        RadioButton(
                            selected = selectedReason == reason,
                            onClick  = { selectedReason = reason },
                            colors   = RadioButtonDefaults.colors(selectedColor = WBrand)
                        )
                        Text(reason, fontSize = 14.sp, color = WTextDark)
                    }
                }

                Spacer(Modifier.height(8.dp))
                Divider(color = WDivider)
                Spacer(Modifier.height(8.dp))

                // Ban toggle — visually distinct with red accent
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (banClient) WError.copy(alpha = 0.06f) else WBgSurface)
                        .border(
                            width = 1.dp,
                            color = if (banClient) WError.copy(alpha = 0.35f) else WDivider,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { banClient = !banClient }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Checkbox(
                        checked = banClient,
                        onCheckedChange = { banClient = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor   = WError,
                            checkmarkColor = WWhite
                        )
                    )
                    Column {
                        Text(
                            text = "Banează clientul (Fraudă)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (banClient) WError else WTextDark
                        )
                        Text(
                            text = "Adaugă contul pe lista neagră QuickBite",
                            fontSize = 12.sp,
                            color = WTextMuted
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedReason, banClient) },
                shape  = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WError)
            ) {
                Text(
                    text = if (banClient) "Anulează & Banează" else "Anulează Comanda",
                    color = WWhite,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Înapoi", color = WTextMuted) }
        }
    )
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
