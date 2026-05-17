import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExitToApp
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.TableRestaurant
import androidx.compose.material3.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quickbite.models.Order
import com.example.quickbite.models.OrderItem
import com.example.quickbite.models.OrderStatus
import com.example.quickbite.models.TableStatus
import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.QueryDocumentSnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

// ── Colour palette ────────────────────────────────────────────────────────────

private val Orange  = Color(0xFFE8430A)   // brand / action accents
private val Red     = Color(0xFFFF3B30)   // OCCUPIED table
private val Yellow  = Color(0xFFFFCC00)   // (reserved — PAYMENT_REQUESTED)
private val Green   = Color(0xFF34C759)   // FREE table / completed orders
private val Dim     = Color(0xFF2C2C2E)
private val Bg      = Color(0xFF111111)
private val Surface = Color(0xFF1C1C1E)
private val White   = Color(0xFFFFFFFF)
private val Muted   = Color(0xFF8E8E93)
private val Divider = Color(0xFF3A3A3C)

private const val TABLE_COUNT = 20
private const val GRID_COLS   = 4

// ── Root composable ───────────────────────────────────────────────────────────

@Composable
fun WaiterApp(db: Firestore?) {
    var restaurantId by remember { mutableStateOf(loadSavedRestaurantId()) }

    when {
        db == null             -> FirebaseSetupScreen()
        restaurantId.isBlank() -> RestaurantSetupScreen { id -> saveRestaurantId(id); restaurantId = id }
        else                   -> WaiterDashboardScreen(db = db, restaurantId = restaurantId)
    }
}

// ── Main dashboard ────────────────────────────────────────────────────────────

@Composable
fun WaiterDashboardScreen(db: Firestore, restaurantId: String) {
    var orders         by remember { mutableStateOf<List<Order>>(emptyList()) }
    // Authoritative table colours come from Firestore, not derived from orders
    var tableStatuses  by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var selectedTable  by remember { mutableStateOf<Int?>(null) }
    var isLive         by remember { mutableStateOf(false) }
    var activeTab      by remember { mutableStateOf(0) }

    // Two concurrent snapshot listeners — both inside a single LaunchedEffect
    LaunchedEffect(restaurantId) {
        launch {
            ordersFlow(db, restaurantId).collect { incoming ->
                orders = incoming
                isLive = true
            }
        }
        launch {
            tableStatusFlow(db, restaurantId).collect { incoming ->
                tableStatuses = incoming
                // Auto-deselect when the table is freed
                if (selectedTable != null && incoming[selectedTable] == TableStatus.FREE) {
                    selectedTable = null
                }
            }
        }
    }

    val pendingCount  = orders.count { it.status == OrderStatus.PENDING }
    val historyOrders = remember(orders) {
        orders.filter { it.status == OrderStatus.COMPLETED }.sortedByDescending { it.timestamp }
    }

    Column(modifier = Modifier.fillMaxSize().background(Bg)) {
        DashboardTopBar(isLive = isLive, pendingCount = pendingCount)
        HorizontalDivider(color = Divider, thickness = 1.dp)

        // ── Tab bar ───────────────────────────────────────────────────────────
        TabRow(
            selectedTabIndex = activeTab,
            containerColor   = Surface,
            contentColor     = Orange
        ) {
            Tab(
                selected = activeTab == 0,
                onClick  = { activeTab = 0 },
                text     = {
                    Text(
                        "Comenzi Active",
                        fontWeight = if (activeTab == 0) FontWeight.Bold else FontWeight.Normal,
                        fontSize   = 14.sp
                    )
                }
            )
            Tab(
                selected = activeTab == 1,
                onClick  = { activeTab = 1 },
                text     = {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "Istoric Comenzi",
                            fontWeight = if (activeTab == 1) FontWeight.Bold else FontWeight.Normal,
                            fontSize   = 14.sp
                        )
                        if (historyOrders.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Orange.copy(alpha = 0.18f)
                            ) {
                                Text(
                                    historyOrders.size.toString(),
                                    fontSize   = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color      = Orange,
                                    modifier   = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            )
        }

        when (activeTab) {
            0 -> Row(modifier = Modifier.fillMaxSize()) {

                    // Left: table grid — colour driven by tableStatuses from Firestore
                    TableGridPanel(
                        modifier      = Modifier.width(400.dp).fillMaxHeight(),
                        tableStatuses = tableStatuses,
                        selectedTable = selectedTable,
                        onTableClick  = { table ->
                            selectedTable = if (selectedTable == table) null else table
                        }
                    )

                    Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Divider))

                    // Right: order detail
                    val selectedOrders = orders.filter { it.tableNumber == selectedTable }
                    val currentTableStatus = tableStatuses[selectedTable] ?: TableStatus.FREE

                    OrderDetailPanel(
                        modifier        = Modifier.weight(1f).fillMaxHeight(),
                        selectedTable   = selectedTable,
                        orders          = selectedOrders,
                        tableStatus     = currentTableStatus,
                        onMarkDelivered = { order ->
                            db.collection("active_orders").document(order.id)
                                .update("status", OrderStatus.DELIVERED)
                        },
                        onCompleteOrder = { order ->
                            db.collection("active_orders").document(order.id)
                                .update("status", OrderStatus.COMPLETED)
                        },
                        onFreeTable = {
                            selectedTable?.let { tableNum ->
                                db.collection("users").document(restaurantId)
                                    .collection("tables").document(tableNum.toString())
                                    .set(mapOf("status" to TableStatus.FREE, "tableNumber" to tableNum))
                            }
                        }
                    )
                }

            else -> OrderHistoryPanel(
                modifier      = Modifier.fillMaxSize(),
                historyOrders = historyOrders
            )
        }
    }
}

// ── Top bar ───────────────────────────────────────────────────────────────────

@Composable
private fun DashboardTopBar(isLive: Boolean, pendingCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Rounded.TableRestaurant, contentDescription = null, tint = Orange, modifier = Modifier.size(26.dp))
            Text("Waiter Command Center", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = White)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (pendingCount > 0) StatusPill("$pendingCount new order${if (pendingCount != 1) "s" else ""}", Red)
            LiveIndicator(isLive = isLive)
        }
    }
}

@Composable
private fun LiveIndicator(isLive: Boolean) {
    val dotColor by animateColorAsState(if (isLive) Green else Muted, tween(600))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(dotColor))
        Text(text = if (isLive) "Live" else "Connecting…", fontSize = 13.sp, color = if (isLive) Green else Muted)
    }
}

@Composable
private fun StatusPill(label: String, color: Color) {
    Surface(shape = RoundedCornerShape(20.dp), color = color.copy(alpha = 0.18f)) {
        Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp))
    }
}

// ── Table grid panel ──────────────────────────────────────────────────────────

@Composable
private fun TableGridPanel(
    modifier: Modifier,
    tableStatuses: Map<Int, String>,   // Firestore-sourced, authoritative
    selectedTable: Int?,
    onTableClick: (Int) -> Unit
) {
    Column(modifier = modifier.background(Surface)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Tables", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Muted, letterSpacing = 1.sp)
            Text("$TABLE_COUNT total", fontSize = 13.sp, color = Muted)
        }
        HorizontalDivider(color = Divider)

        val rows = (1..TABLE_COUNT).toList().chunked(GRID_COLS)
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            rows.forEach { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { tableNum ->
                        TableCell(
                            modifier      = Modifier.weight(1f),
                            tableNumber   = tableNum,
                            tableStatus   = tableStatuses[tableNum] ?: TableStatus.FREE,
                            isSelected    = selectedTable == tableNum,
                            onClick       = { onTableClick(tableNum) }
                        )
                    }
                    repeat(GRID_COLS - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        HorizontalDivider(color = Divider)
        TableLegend(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp))
    }
}

@Composable
private fun TableCell(
    modifier: Modifier,
    tableNumber: Int,
    tableStatus: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val isOccupied = tableStatus == TableStatus.OCCUPIED

    val baseColor = if (isOccupied) Red else Green
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) baseColor.copy(alpha = 0.80f) else baseColor,
        animationSpec = tween(300)
    )

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .then(
                if (isSelected)
                    Modifier.border(2.dp, White.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                else Modifier
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(
                text = tableNumber.toString(),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = White
            )
            Text(
                text = if (isOccupied) "OCCUPIED" else "FREE",
                fontSize = 7.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.8.sp,
                color = White.copy(alpha = if (isOccupied) 0.85f else 0.60f)
            )
        }
    }
}

@Composable
private fun TableLegend(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendItem(color = Green, label = "Free")
        LegendItem(color = Red,   label = "Occupied")
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Text(text = label, fontSize = 12.sp, color = Muted)
    }
}

// ── Order detail panel ────────────────────────────────────────────────────────

@Composable
private fun OrderDetailPanel(
    modifier: Modifier,
    selectedTable: Int?,
    orders: List<Order>,
    tableStatus: String,
    onMarkDelivered: (Order) -> Unit,
    onCompleteOrder: (Order) -> Unit,
    onFreeTable: () -> Unit
) {
    if (selectedTable == null) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Rounded.TableRestaurant, contentDescription = null, tint = Muted, modifier = Modifier.size(56.dp))
                Text("Select a table", fontSize = 18.sp, color = Muted, fontWeight = FontWeight.Medium)
                Text("Click any highlighted table to see its order.", fontSize = 14.sp, color = Muted.copy(alpha = 0.7f), textAlign = TextAlign.Center)
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier.background(Bg).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // ── Panel header ──────────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Table $selectedTable", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = White)
                    Text(
                        text = if (orders.isEmpty()) "No active orders"
                               else "${orders.size} order${if (orders.size != 1) "s" else ""} on this table",
                        fontSize = 14.sp,
                        color = Muted
                    )
                }
                val pendingOrders = orders.filter { it.status == OrderStatus.PENDING }
                if (pendingOrders.isNotEmpty()) StatusPill("${pendingOrders.size} pending", Red)
            }

            // ── FREE TABLE button — only when table is marked OCCUPIED ─────────
            // This is intentionally separate from order actions: guests may still
            // be eating / paying after their order is completed.
            if (tableStatus == TableStatus.OCCUPIED) {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onFreeTable,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Green)
                ) {
                    Icon(
                        Icons.Rounded.ExitToApp,
                        contentDescription = null,
                        tint = White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Clients Left · Free Table",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = White
                    )
                }
            }

            HorizontalDivider(color = Divider, modifier = Modifier.padding(top = 16.dp))
        }

        // ── Empty orders state ────────────────────────────────────────────────
        if (orders.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (tableStatus == TableStatus.OCCUPIED)
                            "Table is occupied — no orders placed yet."
                        else
                            "Table $selectedTable is free.",
                        fontSize = 15.sp,
                        color = Muted,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // ── Order cards (all statuses, newest first) ──────────────────────────
        items(orders.sortedByDescending { it.timestamp }) { order ->
            OrderCard(
                order           = order,
                onMarkDelivered = { onMarkDelivered(order) },
                onCompleteOrder = { onCompleteOrder(order) }
            )
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

// ── Order card ────────────────────────────────────────────────────────────────

@Composable
private fun OrderCard(order: Order, onMarkDelivered: () -> Unit, onCompleteOrder: () -> Unit) {
    val isPending   = order.status == OrderStatus.PENDING
    val isDelivered = order.status == OrderStatus.DELIVERED
    val isCompleted = order.status == OrderStatus.COMPLETED

    val accentColor = when {
        isPending   -> Orange
        isDelivered -> Yellow
        else        -> Green
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = accentColor.copy(alpha = 0.07f),
        modifier = Modifier.fillMaxWidth().border(1.dp, accentColor.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val timeLabel = remember(order.timestamp) { formatTimeAgo(order.timestamp) }
                Text(text = timeLabel, fontSize = 13.sp, color = Muted)
                StatusBadge(status = order.status)
            }

            HorizontalDivider(color = Divider)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                order.items.forEach { item -> OrderItemRow(item) }
                if (order.items.isEmpty()) Text("No items recorded.", fontSize = 13.sp, color = Muted)
            }

            // ── PENDING → Mark as Prepared / Delivered ────────────────────────
            if (isPending) {
                Button(
                    onClick = onMarkDelivered,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Green)
                ) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Mark as Prepared / Delivered", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = White)
                }
            }

            // ── DELIVERED → Complete Order (notifies client, table stays OCCUPIED)
            if (isDelivered) {
                Button(
                    onClick = onCompleteOrder,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Orange)
                ) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Complete Order", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = White)
                }
            }

            // ── COMPLETED → read-only confirmation row ────────────────────────
            if (isCompleted) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Green.copy(alpha = 0.08f))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Green, modifier = Modifier.size(16.dp))
                    Text("Client notified — order completed", fontSize = 13.sp, color = Green, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun OrderItemRow(item: OrderItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(White.copy(alpha = 0.04f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = item.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = White)
            if (item.category.isNotBlank()) Text(text = item.category, fontSize = 12.sp, color = Muted)
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Orange.copy(alpha = 0.18f))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text("×${item.quantity}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Orange)
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val (color, label) = when (status) {
        OrderStatus.PENDING   -> Orange to "PENDING"
        OrderStatus.DELIVERED -> Yellow to "DELIVERED"
        else                  -> Green  to "COMPLETED"
    }
    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.18f)) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

// ── Setup / error screens ─────────────────────────────────────────────────────

@Composable
fun FirebaseSetupScreen() {
    Box(modifier = Modifier.fillMaxSize().background(Bg), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.widthIn(max = 480.dp).padding(32.dp)
        ) {
            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Orange, modifier = Modifier.size(56.dp))
            Text("Firebase Not Configured", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = White)
            Text(
                text = "A service-account.json file is required to connect to Firestore.\n\n" +
                       "1. Firebase Console → Project Settings → Service Accounts\n" +
                       "2. Generate new private key → download JSON\n" +
                       "3. Place it at:\n" +
                       "   ~/.config/quickbite/service-account.json\n" +
                       "   — or set GOOGLE_APPLICATION_CREDENTIALS env var\n" +
                       "4. Restart the application.",
                fontSize = 14.sp, color = Muted, lineHeight = 22.sp
            )
        }
    }
}

@Composable
fun RestaurantSetupScreen(onConfirm: (String) -> Unit) {
    var input    by remember { mutableStateOf("") }
    var hasError by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Bg), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(20.dp), color = Surface, modifier = Modifier.widthIn(max = 480.dp)) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Icon(Icons.Rounded.TableRestaurant, contentDescription = null, tint = Orange, modifier = Modifier.size(44.dp))
                Text("Connect to Restaurant", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = White)
                Text(
                    text = "Enter the Restaurant Code provided by your manager.\n(This is the restaurant owner's Firebase UID.)",
                    fontSize = 13.sp, color = Muted, textAlign = TextAlign.Center, lineHeight = 20.sp
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it; hasError = false },
                    label = { Text("Restaurant Code") },
                    isError = hasError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Orange,
                        focusedLabelColor  = Orange,
                        cursorColor        = Orange
                    )
                )
                Button(
                    onClick = {
                        if (input.trim().isBlank()) { hasError = true; return@Button }
                        onConfirm(input.trim())
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Orange)
                ) {
                    Text("Connect", fontWeight = FontWeight.Bold, color = White)
                }
            }
        }
    }
}

// ── Firestore Flows ───────────────────────────────────────────────────────────

private fun ordersFlow(db: Firestore, restaurantId: String): Flow<List<Order>> = callbackFlow {
    val registration = db.collection("active_orders")
        .whereEqualTo("restaurantId", restaurantId)
        .addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            trySend(snapshot.documents.mapNotNull { it.toOrder() })
        }
    awaitClose { registration.remove() }
}

// Listens to users/{restaurantId}/tables — the authoritative source for table colour.
// Written by the Android client (OCCUPIED on order place) and by this dashboard
// (FREE on "Clients Left").
private fun tableStatusFlow(db: Firestore, restaurantId: String): Flow<Map<Int, String>> = callbackFlow {
    val registration = db.collection("users").document(restaurantId)
        .collection("tables")
        .addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            val statuses = buildMap<Int, String> {
                snapshot.documents.forEach { doc ->
                    val tableNum = doc.getLong("tableNumber")?.toInt() ?: return@forEach
                    val status   = doc.getString("status")             ?: TableStatus.FREE
                    put(tableNum, status)
                }
            }
            trySend(statuses)
        }
    awaitClose { registration.remove() }
}

private fun DocumentSnapshot.toOrder(): Order? {
    val tableNumber  = getLong("tableNumber")?.toInt() ?: return null
    val restaurantId = getString("restaurantId")        ?: return null
    val status       = getString("status")              ?: OrderStatus.PENDING
    val timestamp    = getLong("timestamp")             ?: 0L
    @Suppress("UNCHECKED_CAST")
    val rawItems = get("items") as? List<Map<String, Any>> ?: emptyList()
    val items = rawItems.mapNotNull { map ->
        val name = map["name"] as? String     ?: return@mapNotNull null
        val cat  = map["category"] as? String ?: ""
        val qty  = when (val q = map["quantity"]) {
            is Long -> q.toInt()
            is Int  -> q
            else    -> 1
        }
        OrderItem(name = name, category = cat, quantity = qty)
    }
    return Order(
        id           = id,
        restaurantId = restaurantId,
        tableNumber  = tableNumber,
        items        = items,
        totalPrice   = getDouble("totalPrice") ?: 0.0,
        status       = status,
        timestamp    = timestamp
    )
}

// ── Utilities ─────────────────────────────────────────────────────────────────

private fun formatTimeAgo(timestamp: Long): String {
    if (timestamp == 0L) return "Just now"
    val mins = ((System.currentTimeMillis() - timestamp) / 60_000L).toInt()
    return when {
        mins < 1  -> "Just now"
        mins == 1 -> "1 min ago"
        mins < 60 -> "$mins mins ago"
        else      -> "${mins / 60}h ${mins % 60}m ago"
    }
}

private val configFile = java.io.File(
    "${System.getProperty("user.home")}/.config/quickbite/restaurant-id.txt"
)

private fun loadSavedRestaurantId(): String =
    try { configFile.readText().trim() } catch (_: Exception) { "" }

private fun saveRestaurantId(id: String) {
    try { configFile.parentFile?.mkdirs(); configFile.writeText(id) } catch (_: Exception) { }
}

// ── Order history panel ───────────────────────────────────────────────────────

@Composable
private fun OrderHistoryPanel(modifier: Modifier, historyOrders: List<Order>) {
    if (historyOrders.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Rounded.History, contentDescription = null, tint = Muted, modifier = Modifier.size(56.dp))
                Text("Niciun istoric disponibil", fontSize = 18.sp, color = Muted, fontWeight = FontWeight.Medium)
                Text("Comenzile finalizate vor apărea aici.", fontSize = 14.sp, color = Muted.copy(alpha = 0.7f))
            }
        }
        return
    }

    val totalRevenue = historyOrders.sumOf { it.totalPrice }

    Column(modifier = modifier.background(Bg)) {
        // ── Revenue summary ───────────────────────────────────────────────────
        Surface(color = Surface, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier              = Modifier.padding(horizontal = 28.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(48.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                HistoryStatCard(
                    label  = "Comenzi Finalizate",
                    value  = historyOrders.size.toString(),
                    accent = Green
                )
                if (totalRevenue > 0.0) {
                    HistoryStatCard(
                        label  = "Venit Total",
                        value  = "${"%.2f".format(totalRevenue)} RON",
                        accent = Orange
                    )
                }
            }
        }
        HorizontalDivider(color = Divider)

        // ── Order log ─────────────────────────────────────────────────────────
        LazyColumn(
            modifier       = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "JURNAL COMENZI",
                    fontSize      = 11.sp,
                    fontWeight    = FontWeight.Bold,
                    color         = Muted,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(4.dp))
            }
            items(historyOrders) { order -> HistoryOrderCard(order = order) }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

// ── History stat card ─────────────────────────────────────────────────────────

@Composable
private fun HistoryStatCard(label: String, value: String, accent: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text          = label,
            fontSize      = 11.sp,
            color         = Muted,
            fontWeight    = FontWeight.Medium,
            letterSpacing = 0.5.sp
        )
        Text(
            text       = value,
            fontSize   = 28.sp,
            fontWeight = FontWeight.Bold,
            color      = accent
        )
    }
}

// ── History order card ────────────────────────────────────────────────────────

@Composable
private fun HistoryOrderCard(order: Order) {
    Surface(
        shape    = RoundedCornerShape(14.dp),
        color    = Green.copy(alpha = 0.06f),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Green.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier              = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.Top
        ) {
            // Left: table badge + timestamp + item summary
            Column(
                modifier            = Modifier.weight(1f).padding(end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(shape = RoundedCornerShape(8.dp), color = Green.copy(alpha = 0.15f)) {
                        Text(
                            "Masă ${order.tableNumber}",
                            fontSize   = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color      = Green,
                            modifier   = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Text(formatTimeAgo(order.timestamp), fontSize = 12.sp, color = Muted)
                }
                if (order.items.isNotEmpty()) {
                    Text(
                        text     = order.items.joinToString(" · ") { "${it.name} ×${it.quantity}" },
                        fontSize = 13.sp,
                        color    = White.copy(alpha = 0.60f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Right: revenue or completion check
            if (order.totalPrice > 0.0) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text       = "${"%.2f".format(order.totalPrice)}",
                        fontSize   = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color      = White
                    )
                    Text("RON", fontSize = 11.sp, color = Muted)
                }
            } else {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint     = Green,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
