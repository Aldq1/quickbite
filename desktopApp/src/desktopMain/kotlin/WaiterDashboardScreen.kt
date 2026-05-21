import androidx.compose.animation.animateColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExitToApp
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.TableRestaurant
import androidx.compose.material.icons.rounded.Timer
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

// ── Colour palette ────────────────────────────────────────────────────────────

private val Orange  = Color(0xFFE8430A)
private val Red     = Color(0xFFFF3B30)
private val Yellow  = Color(0xFFFFCC00)
private val Green   = Color(0xFF34C759)
private val Amber   = Color(0xFFFF9F0A)
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
        db == null             -> WaiterDashboardMockScreen()   // demo / offline mode
        restaurantId.isBlank() -> RestaurantSetupScreen { id -> saveRestaurantId(id); restaurantId = id }
        else                   -> WaiterDashboardScreen(
            db           = db,
            restaurantId = restaurantId,
            onLogout     = { clearSavedRestaurantId(); restaurantId = "" }
        )
    }
}

// ── Live Firestore dashboard ──────────────────────────────────────────────────

@Composable
fun WaiterDashboardScreen(db: Firestore, restaurantId: String, onLogout: () -> Unit = {}) {
    var allOrders     by remember { mutableStateOf<List<Order>>(emptyList()) }
    var tableStatuses by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var selectedTable by remember { mutableStateOf<Int?>(null) }
    var isLive        by remember { mutableStateOf(false) }
    var activeTab     by remember { mutableStateOf(0) }
    val scope         = rememberCoroutineScope()

    val orders = remember(allOrders) { allOrders.filter { it.status != "ARCHIVED" && it.status != "WALKED_OUT" } }

    LaunchedEffect(restaurantId) {
        launch {
            ordersFlow(db, restaurantId).collect { incoming ->
                allOrders = incoming
                isLive = true
            }
        }
        launch {
            tableStatusFlow(db, restaurantId).collect { incoming ->
                tableStatuses = incoming
                if (selectedTable != null && incoming[selectedTable] == TableStatus.FREE) {
                    selectedTable = null
                }
            }
        }
    }

    val pendingCount  = orders.count { it.status == OrderStatus.PENDING }
    val historyOrders = remember(allOrders) {
        allOrders.filter { it.status == "ARCHIVED" || it.status == "WALKED_OUT" || it.status == OrderStatus.COMPLETED }
            .sortedByDescending { it.timestamp }
    }

    // Merge orders-derived occupancy with the Firestore tables collection.
    // A table with any non-COMPLETED order is always shown as OCCUPIED, even if the
    // tables sub-collection hasn't been written yet (race) or is missing entirely.
    // Firestore wins for PAYMENT_REQUESTED and for explicit FREE marks (waiter freed it).
    val mergedTableStatuses: Map<Int, String> = remember(tableStatuses, orders) {
        val activeTableNums = orders
            .filter { it.status != OrderStatus.COMPLETED }
            .map { it.tableNumber }.toSet()
        (tableStatuses.keys + activeTableNums).associateWith { num ->
            val fs = tableStatuses[num]
            when {
                fs != null && fs != TableStatus.FREE -> fs          // Firestore: OCCUPIED / PAYMENT_REQUESTED
                num in activeTableNums               -> TableStatus.OCCUPIED  // order exists → show occupied
                else                                 -> TableStatus.FREE
            }
        }
    }

    WaiterDashboardScaffold(
        topBar        = { DashboardTopBar(isLive = isLive, pendingCount = pendingCount, onLogout = onLogout) },
        activeTab     = activeTab,
        onTabChange   = { activeTab = it },
        tableMapContent = {
            val selectedOrders     = orders.filter { it.tableNumber == selectedTable }
            val currentTableStatus = mergedTableStatuses[selectedTable] ?: TableStatus.FREE
            TableMapTab(
                allOrders      = orders,
                tableStatuses  = mergedTableStatuses,
                selectedTable  = selectedTable,
                onTableClick   = { t -> selectedTable = if (selectedTable == t) null else t },
                orders         = selectedOrders,
                tableStatus    = currentTableStatus,
                onCompleteOrder = { order ->
                    scope.launch {
                        db.collection("active_orders").document(order.id)
                            .update("status", OrderStatus.COMPLETED)
                    }
                },
                onFreeTable = {
                    selectedTable?.let { tableNum ->
                        scope.launch {
                            val batch = db.batch()
                            // Mark walked-out so history can distinguish unpaid closes from paid ones
                            orders.filter {
                                it.tableNumber == tableNum && it.status != "CANCELLED"
                            }.forEach { order ->
                                batch.update(
                                    db.collection("orders").document(order.id),
                                    "status", "WALKED_OUT"
                                )
                            }
                            batch.set(
                                db.collection("users").document(restaurantId)
                                    .collection("tables").document(tableNum.toString()),
                                mapOf("status" to TableStatus.FREE, "tableNumber" to tableNum)
                            )
                            batch.commit()
                        }
                    }
                },
                onMarkAllPaid = {
                    selectedTable?.let { tableNum ->
                        scope.launch {
                            val batch     = db.batch()
                            val toArchive = orders.filter { it.tableNumber == tableNum && it.status != "CANCELLED" }
                            toArchive.forEach { order ->
                                batch.update(
                                    db.collection("orders").document(order.id),
                                    "status", "ARCHIVED"
                                )
                            }
                            batch.set(
                                db.collection("users").document(restaurantId)
                                    .collection("tables").document(tableNum.toString()),
                                mapOf("status" to TableStatus.FREE, "tableNumber" to tableNum)
                            )
                            batch.commit()
                        }
                    }
                }
            )
        },
        historyContent = {
            OrderHistoryPanel(
                modifier      = Modifier.fillMaxSize(),
                historyOrders = historyOrders,
                onDeleteOrder = { order ->
                    scope.launch { db.collection("orders").document(order.id).delete() }
                },
                onDeleteAll = {
                    scope.launch {
                        historyOrders.chunked(499).forEach { chunk ->
                            val batch = db.batch()
                            chunk.forEach { o -> batch.delete(db.collection("orders").document(o.id)) }
                            batch.commit()
                        }
                    }
                }
            )
        },
        kdsContent     = { KitchenDisplayScreen(modifier = Modifier.fillMaxSize(), db = db, restaurantId = restaurantId) },
        qrContent      = { QrManagerScreen(modifier = Modifier.fillMaxSize(), restaurantId = restaurantId, db = db) }
    )

}

// ── Demo / mock dashboard (no Firebase needed) ────────────────────────────────

@Composable
internal fun WaiterDashboardMockScreen() {
    var orders        by remember { mutableStateOf(mockOrders) }
    var tableStatuses by remember { mutableStateOf(mockTableStatuses) }
    var selectedTable by remember { mutableStateOf<Int?>(null) }
    var activeTab     by remember { mutableStateOf(0) }

    val pendingCount  = orders.count { it.status == OrderStatus.PENDING }
    val historyOrders = remember(orders) {
        orders.filter { it.status == OrderStatus.COMPLETED }.sortedByDescending { it.timestamp }
    }

    WaiterDashboardScaffold(
        topBar      = { DemoBanner(pendingCount = pendingCount) },
        activeTab   = activeTab,
        onTabChange = { activeTab = it },
        tableMapContent = {
            val selectedOrders     = orders.filter { it.tableNumber == selectedTable }
            val currentTableStatus = tableStatuses[selectedTable] ?: TableStatus.FREE
            TableMapTab(
                allOrders       = orders,
                tableStatuses   = tableStatuses,
                selectedTable   = selectedTable,
                onTableClick    = { t -> selectedTable = if (selectedTable == t) null else t },
                orders          = selectedOrders,
                tableStatus     = currentTableStatus,
                onCompleteOrder = { order ->
                    orders = orders.map { if (it.id == order.id) it.copy(status = OrderStatus.COMPLETED) else it }
                },
                onFreeTable     = {
                    selectedTable?.let { t ->
                        tableStatuses = tableStatuses + (t to TableStatus.FREE)
                        selectedTable = null
                    }
                },
                onMarkAllPaid   = {
                    selectedTable?.let { t ->
                        orders = orders.map {
                            if (it.tableNumber == t && it.status != OrderStatus.COMPLETED)
                                it.copy(status = OrderStatus.COMPLETED)
                            else it
                        }
                        tableStatuses = tableStatuses + (t to TableStatus.FREE)
                        selectedTable = null
                    }
                }
            )
        },
        historyContent = {
            OrderHistoryPanel(
                modifier      = Modifier.fillMaxSize(),
                historyOrders = historyOrders,
                onDeleteOrder = { order -> orders = orders.filter { it.id != order.id } },
                onDeleteAll   = { orders = orders.filter { it.status != OrderStatus.COMPLETED } }
            )
        },
        kdsContent     = {
            KitchenDisplayContent(
                modifier  = Modifier.fillMaxSize(),
                orders    = orders,
                onAccept  = { order ->
                    orders = orders.map { if (it.id == order.id) it.copy(status = OrderStatus.COOKING) else it }
                },
                onReady   = { order ->
                    orders = orders.map { if (it.id == order.id) it.copy(status = OrderStatus.DELIVERED) else it }
                }
            )
        },
        qrContent      = { QrManagerScreen(modifier = Modifier.fillMaxSize()) }
    )
}

// ── Shared scaffold (live + demo share identical chrome) ──────────────────────

@Composable
private fun WaiterDashboardScaffold(
    topBar:          @Composable () -> Unit,
    activeTab:       Int,
    onTabChange:     (Int) -> Unit,
    tableMapContent: @Composable () -> Unit,
    historyContent:  @Composable () -> Unit,
    kdsContent:      @Composable () -> Unit,
    qrContent:       @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(Bg)) {
        topBar()
        HorizontalDivider(color = Divider, thickness = 1.dp)

        Row(modifier = Modifier.fillMaxSize()) {
            NavigationRail(
                modifier       = Modifier.fillMaxHeight(),
                containerColor = Surface
            ) {
                Spacer(Modifier.height(12.dp))
                navDestinations.forEachIndexed { index, (icon, label) ->
                    NavigationRailItem(
                        selected        = activeTab == index,
                        onClick         = { onTabChange(index) },
                        icon            = { Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp)) },
                        label           = { Text(label, fontSize = 9.sp, textAlign = TextAlign.Center, lineHeight = 12.sp) },
                        alwaysShowLabel = true,
                        colors          = NavigationRailItemDefaults.colors(
                            selectedIconColor   = Orange,
                            selectedTextColor   = Orange,
                            indicatorColor      = Orange.copy(alpha = 0.15f),
                            unselectedIconColor = Muted,
                            unselectedTextColor = Muted
                        )
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }

            Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Divider))

            when (activeTab) {
                0    -> tableMapContent()
                1    -> historyContent()
                2    -> kdsContent()
                else -> qrContent()
            }
        }
    }
}

// ── Table map tab (left grid + right detail) ──────────────────────────────────

@Composable
private fun TableMapTab(
    allOrders:      List<Order>,
    tableStatuses:  Map<Int, String>,
    selectedTable:  Int?,
    onTableClick:   (Int) -> Unit,
    orders:         List<Order>,
    tableStatus:    String,
    onCompleteOrder: (Order) -> Unit,
    onFreeTable:    () -> Unit,
    onMarkAllPaid:  () -> Unit
) {
    val readyTableNumbers = remember(allOrders) {
        allOrders.filter { it.status == OrderStatus.DELIVERED }.map { it.tableNumber }.toSet()
    }
    val tableTimestamps = remember(allOrders) {
        allOrders.filter { it.status != OrderStatus.COMPLETED }
            .groupBy { it.tableNumber }
            .mapValues { (_, list) -> list.minOf { it.timestamp } }
    }
    Row(modifier = Modifier.fillMaxSize()) {
        TableGridPanel(
            modifier          = Modifier.width(400.dp).fillMaxHeight(),
            tableStatuses     = tableStatuses,
            selectedTable     = selectedTable,
            onTableClick      = onTableClick,
            readyTableNumbers = readyTableNumbers,
            tableTimestamps   = tableTimestamps
        )
        Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Divider))
        OrderDetailPanel(
            modifier        = Modifier.weight(1f).fillMaxHeight(),
            selectedTable   = selectedTable,
            orders          = orders,
            tableStatus     = tableStatus,
            onCompleteOrder = onCompleteOrder,
            onFreeTable     = onFreeTable,
            onMarkAllPaid   = onMarkAllPaid
        )
    }
}

// ── Top bars ──────────────────────────────────────────────────────────────────

@Composable
private fun DashboardTopBar(isLive: Boolean, pendingCount: Int, onLogout: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.TableRestaurant, contentDescription = null, tint = Orange, modifier = Modifier.size(26.dp))
            Text("Waiter Command Center", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = White)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (pendingCount > 0) StatusPill("$pendingCount ${if (pendingCount != 1) "comenzi noi" else "comandă nouă"}", Red)
            LiveIndicator(isLive = isLive)
            IconButton(onClick = onLogout) {
                Icon(
                    imageVector        = Icons.Rounded.ExitToApp,
                    contentDescription = "Logout / Reset session",
                    tint               = Muted,
                    modifier           = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun DemoBanner(pendingCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.TableRestaurant, contentDescription = null, tint = Orange, modifier = Modifier.size(26.dp))
            Text("Waiter Command Center", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = White)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (pendingCount > 0) StatusPill("$pendingCount comenzi noi", Red)
            Surface(shape = RoundedCornerShape(20.dp), color = Yellow.copy(alpha = 0.15f)) {
                Text(
                    "DEMO MODE",
                    color = Yellow, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun LiveIndicator(isLive: Boolean) {
    val dotColor by animateColorAsState(if (isLive) Green else Muted, tween(600))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(dotColor))
        Text(if (isLive) "Live" else "Connecting…", fontSize = 13.sp, color = if (isLive) Green else Muted)
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
    tableStatuses: Map<Int, String>,
    selectedTable: Int?,
    onTableClick: (Int) -> Unit,
    readyTableNumbers: Set<Int> = emptySet(),
    tableTimestamps: Map<Int, Long> = emptyMap()
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
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { tableNum ->
                        TableCell(
                            modifier    = Modifier.weight(1f),
                            tableNumber = tableNum,
                            tableStatus = tableStatuses[tableNum] ?: TableStatus.FREE,
                            isSelected  = selectedTable == tableNum,
                            isReady     = tableNum in readyTableNumbers,
                            timestamp   = tableTimestamps[tableNum] ?: 0L,
                            onClick     = { onTableClick(tableNum) }
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
    isReady: Boolean = false,
    timestamp: Long = 0L,
    onClick: () -> Unit
) {
    val isOccupied = tableStatus == TableStatus.OCCUPIED
    val baseColor  = if (isOccupied) Red else Green

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_$tableNumber")
    val pulseColor by infiniteTransition.animateColor(
        initialValue  = Green.copy(alpha = 0.55f),
        targetValue   = Green,
        animationSpec = infiniteRepeatable(
            animation  = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_color"
    )

    val bgColor = when {
        isSelected -> (if (isReady) Green else baseColor).copy(alpha = 0.85f)
        isReady    -> pulseColor
        else       -> baseColor
    }

    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(timestamp) {
        if (timestamp > 0L) while (true) { delay(30_000L); tick++ }
    }
    val elapsedText = remember(tick, timestamp) {
        if (timestamp == 0L || !isOccupied) "" else formatElapsedShort(timestamp)
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .then(if (isSelected) Modifier.border(2.dp, White.copy(alpha = 0.7f), RoundedCornerShape(14.dp)) else Modifier)
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(tableNumber.toString(), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = White)
            Text(
                text = when {
                    !isOccupied -> "FREE"
                    isReady     -> "READY!"
                    else        -> "OCCUPIED"
                },
                fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp,
                color = White.copy(alpha = if (isOccupied) 0.85f else 0.60f)
            )
        }
        if (elapsedText.isNotEmpty()) {
            Text(
                elapsedText,
                fontSize   = 8.sp,
                fontWeight = FontWeight.Bold,
                color      = White.copy(alpha = 0.85f),
                modifier   = Modifier.align(Alignment.BottomEnd).padding(4.dp)
            )
        }
    }
}

@Composable
private fun TableLegend(modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendItem(color = Green, label = "Free")
        LegendItem(color = Red,   label = "Occupied")
        LegendItem(color = Green, label = "Gata de Servit (pulsează)")
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Text(label, fontSize = 12.sp, color = Muted)
    }
}

// ── Order detail panel ────────────────────────────────────────────────────────

@Composable
private fun OrderDetailPanel(
    modifier:        Modifier,
    selectedTable:   Int?,
    orders:          List<Order>,
    tableStatus:     String,
    onCompleteOrder: (Order) -> Unit,
    onFreeTable:     () -> Unit,
    onMarkAllPaid:   () -> Unit
) {
    if (selectedTable == null) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.TableRestaurant, contentDescription = null, tint = Muted, modifier = Modifier.size(56.dp))
                Text("Select a table", fontSize = 18.sp, color = Muted, fontWeight = FontWeight.Medium)
                Text("Click any highlighted table to see its order.", fontSize = 14.sp, color = Muted.copy(alpha = 0.7f), textAlign = TextAlign.Center)
            }
        }
        return
    }

    LazyColumn(
        modifier            = modifier.background(Bg).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // ── Panel header ──────────────────────────────────────────────────────
        item {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column {
                    Text("Table $selectedTable", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = White)
                    Text(
                        text = if (orders.isEmpty()) "No active orders"
                               else "${orders.size} order${if (orders.size != 1) "s" else ""} on this table",
                        fontSize = 14.sp, color = Muted
                    )
                }
                val pendingOrders = orders.filter { it.status == OrderStatus.PENDING }
                if (pendingOrders.isNotEmpty()) StatusPill("${pendingOrders.size} pending", Red)
            }

            // ── Table action buttons (OCCUPIED only) ──────────────────────────
            if (tableStatus == TableStatus.OCCUPIED) {
                Spacer(Modifier.height(16.dp))

                val canFinish = orders.none {
                    it.status == OrderStatus.PENDING || it.status == OrderStatus.COOKING
                }

                // Primary: Cash payment — marks all orders ARCHIVED + frees table
                Button(
                    onClick  = { if (canFinish) onMarkAllPaid() },
                    enabled  = canFinish,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor         = Green,
                        disabledContainerColor = Green.copy(alpha = 0.20f),
                        disabledContentColor   = Green.copy(alpha = 0.45f)
                    )
                ) {
                    Icon(Icons.Rounded.Payments, contentDescription = null, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Plată Cash · Finalizează Masa", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(8.dp))

                // Secondary: Guests left without explicit payment step
                Button(
                    onClick  = onFreeTable,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = Muted.copy(alpha = 0.14f),
                        contentColor   = Muted
                    )
                ) {
                    Icon(Icons.Rounded.ExitToApp, contentDescription = null, tint = Muted, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Clienți Plecați · Eliberează Masa", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Muted)
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
                        fontSize = 15.sp, color = Muted, textAlign = TextAlign.Center
                    )
                }
            }
        }

        // ── Order cards ───────────────────────────────────────────────────────
        items(orders.sortedByDescending { it.timestamp }) { order ->
            OrderCard(order = order, onCompleteOrder = { onCompleteOrder(order) })
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

// ── Order card ────────────────────────────────────────────────────────────────

@Composable
private fun OrderCard(order: Order, onCompleteOrder: () -> Unit) {
    val isPending   = order.status == OrderStatus.PENDING
    val isCooking   = order.status == OrderStatus.COOKING
    val isDelivered = order.status == OrderStatus.DELIVERED
    val isCompleted = order.status == OrderStatus.COMPLETED

    val accentColor = when {
        isPending   -> Orange
        isCooking   -> Amber
        isDelivered -> Green
        else        -> Green
    }

    ElevatedCard(
        shape    = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, accentColor.copy(alpha = 0.25f), RoundedCornerShape(16.dp)),
        colors   = CardDefaults.elevatedCardColors(containerColor = accentColor.copy(alpha = 0.07f))
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {

            // ── Header: timestamp + status badge ──────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                val timeLabel = remember(order.timestamp) { formatTimeAgo(order.timestamp) }
                Text(timeLabel, fontSize = 13.sp, color = Muted)
                StatusBadge(status = order.status)
            }

            HorizontalDivider(color = Divider)

            // ── Items ─────────────────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                order.items.forEach { item -> OrderItemRow(item) }
                if (order.items.isEmpty()) Text("No items recorded.", fontSize = 13.sp, color = Muted)
            }

            // ── Status-specific action area ────────────────────────────────────
            // PENDING: Kitchen is handling it — waiter is read-only
            if (isPending) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Orange.copy(alpha = 0.08f))
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.Timer, contentDescription = null, tint = Orange, modifier = Modifier.size(16.dp))
                    Text(
                        "Așteptare bucătărie — gestionată automat de KDS",
                        fontSize = 13.sp, color = Orange, fontWeight = FontWeight.Medium
                    )
                }
            }

            // COOKING: Kitchen is preparing — waiter is notified
            if (isCooking) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Amber.copy(alpha = 0.08f))
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.Restaurant, contentDescription = null, tint = Amber, modifier = Modifier.size(16.dp))
                    Text(
                        "Bucătăria pregătește comanda",
                        fontSize = 13.sp, color = Amber, fontWeight = FontWeight.Medium
                    )
                }
            }

            // DELIVERED: Kitchen marked as ready — waiter must carry it to the table
            if (isDelivered) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Green.copy(alpha = 0.14f))
                            .border(1.dp, Green.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Green, modifier = Modifier.size(22.dp))
                        Column {
                            Text(
                                "GATA DE SERVIT!",
                                fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                                color = Green, letterSpacing = 0.5.sp
                            )
                            Text(
                                "Bucătăria a finalizat comanda — duceți la masă!",
                                fontSize = 12.sp, color = Green.copy(alpha = 0.75f)
                            )
                        }
                    }
                    Button(
                        onClick  = onCompleteOrder,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = Green)
                    ) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Servit · Marchează Plătit", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = White)
                    }
                }
            }

            // COMPLETED: Confirmation row
            if (isCompleted) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Green.copy(alpha = 0.08f))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Green, modifier = Modifier.size(16.dp))
                    Text("Client notificat — comandă finalizată", fontSize = 13.sp, color = Green, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

// ── Order item row ────────────────────────────────────────────────────────────

@Composable
private fun OrderItemRow(item: OrderItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(White.copy(alpha = 0.04f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(item.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = White)
            if (item.category.isNotBlank()) Text(item.category, fontSize = 12.sp, color = Muted)
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

// ── Status badge ──────────────────────────────────────────────────────────────

@Composable
private fun StatusBadge(status: String) {
    val (color, label) = when (status) {
        OrderStatus.PENDING   -> Orange to "AȘTEPTARE"
        OrderStatus.COOKING   -> Amber  to "ÎN PREPARARE"
        OrderStatus.DELIVERED -> Green  to "GATA DE SERVIT"
        else                  -> Green  to "FINALIZAT"
    }
    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.18f)) {
        Text(
            label,
            fontSize      = 11.sp,
            fontWeight    = FontWeight.ExtraBold,
            letterSpacing = 0.8.sp,
            color         = color,
            modifier      = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
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
                    label   = { Text("Restaurant Code") },
                    isError = hasError,
                    singleLine = true,
                    modifier   = Modifier.fillMaxWidth(),
                    colors     = OutlinedTextFieldDefaults.colors(
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
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = Orange)
                ) {
                    Text("Connect", fontWeight = FontWeight.Bold, color = White)
                }
            }
        }
    }
}

// ── Firestore flows ───────────────────────────────────────────────────────────

private fun ordersFlow(db: Firestore, restaurantId: String): Flow<List<Order>> = callbackFlow {
    val registration = db.collection("orders")
        .whereEqualTo("restaurantId", restaurantId)
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                println("DESKTOP WAITER ERROR: Firestore listener error — ${error.javaClass.simpleName}: ${error.message}")
                return@addSnapshotListener
            }
            if (snapshot == null) {
                println("DESKTOP WAITER: Snapshot is null")
                return@addSnapshotListener
            }
            println("DESKTOP WAITER: Snapshot received with ${snapshot.documents.size} documents for restaurantId=$restaurantId")
            val parsed = snapshot.documents.mapNotNull { doc ->
                try {
                    doc.toOrder()
                } catch (e: Exception) {
                    println("DESKTOP WAITER ERROR: Failed to parse document ${doc.id} — ${e.javaClass.simpleName}: ${e.message}")
                    null
                }
            }
            println(
                "DESKTOP WAITER BREAKDOWN: " +
                "PENDING=${parsed.count { it.status == OrderStatus.PENDING }} " +
                "COOKING=${parsed.count { it.status == OrderStatus.COOKING }} " +
                "DELIVERED=${parsed.count { it.status == OrderStatus.DELIVERED }} " +
                "COMPLETED=${parsed.count { it.status == OrderStatus.COMPLETED }} " +
                "of ${snapshot.documents.size} docs"
            )
            trySend(parsed)
        }
    awaitClose { registration.remove() }
}

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

// Coerce any numeric type the Admin SDK might return to Long / Double.
private fun Any?.toLongSafe(): Long? = when (this) {
    is Long   -> this
    is Int    -> this.toLong()
    is Double -> this.toLong()
    is Number -> this.toLong()
    else      -> null
}

private fun Any?.toDoubleSafe(): Double? = when (this) {
    is Double -> this
    is Long   -> this.toDouble()
    is Int    -> this.toDouble()
    is Number -> this.toDouble()
    else      -> null
}

internal fun DocumentSnapshot.toOrder(): Order? {
    val docId = id
    return try {
        // Raw field extraction — using get() so we see the actual Java type
        val rawTableNumber  = get("tableNumber")
        val rawRestaurantId = get("restaurantId")
        val rawStatus       = get("status")
        val rawTimestamp    = get("timestamp")
        val rawTotalPrice   = get("totalPrice")
        val rawItems        = get("items")

        println(
            "DESKTOP PARSE: doc=$docId | " +
            "tableNumber=$rawTableNumber(${rawTableNumber?.javaClass?.simpleName}) | " +
            "restaurantId=$rawRestaurantId | " +
            "status=$rawStatus | " +
            "timestamp=$rawTimestamp(${rawTimestamp?.javaClass?.simpleName}) | " +
            "totalPrice=$rawTotalPrice | " +
            "items=${rawItems?.javaClass?.simpleName}"
        )

        val tableNumber = rawTableNumber.toLongSafe()?.toInt()
        if (tableNumber == null) {
            println("DESKTOP SKIP: doc=$docId — tableNumber missing or non-numeric (${rawTableNumber?.javaClass?.simpleName}: $rawTableNumber)")
            return null
        }

        val restaurantId = rawRestaurantId as? String
        if (restaurantId == null) {
            println("DESKTOP SKIP: doc=$docId — restaurantId missing or not a String (${rawRestaurantId?.javaClass?.simpleName}: $rawRestaurantId)")
            return null
        }

        val status: String = rawStatus as? String ?: run {
            println("DESKTOP WARN: doc=$docId — status missing, defaulting to PENDING")
            OrderStatus.PENDING
        }

        // timestamp is written by Android as System.currentTimeMillis() (Long),
        // but guard against Firestore Timestamp objects just in case.
        val timestamp: Long = when {
            rawTimestamp is Long   -> rawTimestamp
            rawTimestamp is Number -> rawTimestamp.toLong()
            rawTimestamp != null   -> try {
                val date = rawTimestamp.javaClass.getMethod("toDate").invoke(rawTimestamp) as? java.util.Date
                date?.time ?: 0L
            } catch (e: Exception) {
                println("DESKTOP WARN: doc=$docId — could not read timestamp from ${rawTimestamp.javaClass.simpleName}: ${e.message}")
                0L
            }
            else -> 0L
        }

        val totalPrice = rawTotalPrice.toDoubleSafe() ?: 0.0

        val rawItemsList = rawItems as? List<*> ?: emptyList<Any>()
        val items = rawItemsList.mapNotNull { element ->
            val map  = element as? Map<*, *> ?: return@mapNotNull null
            val name = map["name"] as? String ?: return@mapNotNull null
            val cat  = map["category"] as? String ?: ""
            val qty  = map["quantity"].toLongSafe()?.toInt() ?: 1
            OrderItem(name = name, category = cat, quantity = qty)
        }

        println("DESKTOP SUCCESS: Parsed order $docId | status=$status | table=$tableNumber | items=${items.size} | restaurantId=$restaurantId")
        Order(
            id           = docId,
            restaurantId = restaurantId,
            tableNumber  = tableNumber,
            items        = items,
            totalPrice   = totalPrice,
            status       = status,
            timestamp    = timestamp
        )
    } catch (e: Exception) {
        println("DESKTOP PARSE ERROR: doc=$docId — ${e.javaClass.simpleName}: ${e.message}")
        null
    }
}

// ── Utilities ─────────────────────────────────────────────────────────────────

private fun formatElapsedShort(timestamp: Long): String {
    if (timestamp == 0L) return ""
    val mins = ((System.currentTimeMillis() - timestamp) / 60_000L).toInt()
    return if (mins < 1) "<1m" else "${mins}m"
}

internal fun formatTimeAgo(timestamp: Long): String {
    if (timestamp == 0L) return "Just now"
    val mins = ((System.currentTimeMillis() - timestamp) / 60_000L).toInt()
    return when {
        mins < 1  -> "Just now"
        mins == 1 -> "1 min ago"
        mins < 60 -> "$mins mins ago"
        else      -> "${mins / 60}h ${mins % 60}m ago"
    }
}

// ── Navigation destinations ───────────────────────────────────────────────────

private val navDestinations = listOf(
    Icons.Rounded.TableRestaurant to "Harta\nMese",
    Icons.Rounded.History         to "Suport /\nIstoric",
    Icons.Rounded.Restaurant      to "Bucătărie\nKDS",
    Icons.Rounded.QrCode2         to "Gestiune\nQR",
)

// ── Persistence ───────────────────────────────────────────────────────────────

private val configFile = java.io.File(
    "${System.getProperty("user.home")}/.config/quickbite/restaurant-id.txt"
)

private fun loadSavedRestaurantId(): String =
    try { configFile.readText().trim() } catch (_: Exception) { "" }

private fun saveRestaurantId(id: String) {
    try { configFile.parentFile?.mkdirs(); configFile.writeText(id) } catch (_: Exception) { }
}

private fun clearSavedRestaurantId() {
    try { configFile.writeText("") } catch (_: Exception) { }
}

// ── Order history panel ───────────────────────────────────────────────────────

@Composable
private fun OrderHistoryPanel(
    modifier:      Modifier,
    historyOrders: List<Order>,
    onDeleteOrder: (Order) -> Unit,
    onDeleteAll:   () -> Unit
) {
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds   by remember { mutableStateOf(emptySet<String>()) }

    if (historyOrders.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.History, contentDescription = null, tint = Muted, modifier = Modifier.size(56.dp))
                Text("Niciun istoric disponibil", fontSize = 18.sp, color = Muted, fontWeight = FontWeight.Medium)
                Text("Comenzile finalizate vor apărea aici.", fontSize = 14.sp, color = Muted.copy(alpha = 0.7f))
            }
        }
        return
    }

    val paidOrders     = historyOrders.filter { it.status == "ARCHIVED" || it.status == OrderStatus.COMPLETED }
    val walkedOutCount = historyOrders.count { it.status == "WALKED_OUT" }
    val totalRevenue   = paidOrders.sumOf { it.totalPrice }
    val allSelected    = selectedIds.size == historyOrders.size

    Column(modifier = modifier.background(Bg)) {
        Surface(color = Surface, modifier = Modifier.fillMaxWidth()) {
            if (!selectionMode) {
                // ── Normal header ─────────────────────────────────────────────
                Row(
                    modifier              = Modifier.padding(horizontal = 28.dp, vertical = 20.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(48.dp), verticalAlignment = Alignment.CenterVertically) {
                        HistoryStatCard(label = "Comenzi Plătite", value = paidOrders.size.toString(), accent = Green)
                        if (totalRevenue > 0.0) {
                            HistoryStatCard(label = "Venit Total", value = "${"%.2f".format(totalRevenue)} RON", accent = Orange)
                        }
                        if (walkedOutCount > 0) {
                            HistoryStatCard(label = "Neplătite", value = walkedOutCount.toString(), accent = Red)
                        }
                    }
                    Button(
                        onClick = { selectionMode = true },
                        shape   = RoundedCornerShape(10.dp),
                        colors  = ButtonDefaults.buttonColors(containerColor = Red.copy(alpha = 0.15f), contentColor = Red)
                    ) {
                        Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Ștergere", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            } else {
                // ── Selection toolbar ─────────────────────────────────────────
                Row(
                    modifier              = Modifier.padding(horizontal = 20.dp, vertical = 14.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        text       = if (selectedIds.isEmpty()) "Selectează înregistrări"
                                     else "${selectedIds.size} selectat${if (selectedIds.size != 1) "e" else "ă"}",
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = if (selectedIds.isEmpty()) Muted else White
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            selectedIds = if (allSelected) emptySet()
                                          else historyOrders.map { it.id }.toSet()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Orange)
                    ) {
                        Text(if (allSelected) "Deselectează tot" else "Selectează tot", fontSize = 13.sp)
                    }
                    Button(
                        onClick = {
                            val toDelete = historyOrders.filter { it.id in selectedIds }
                            if (toDelete.size == historyOrders.size) {
                                onDeleteAll()
                            } else {
                                toDelete.forEach { onDeleteOrder(it) }
                            }
                            selectedIds   = emptySet()
                            selectionMode = false
                        },
                        enabled = selectedIds.isNotEmpty(),
                        shape   = RoundedCornerShape(10.dp),
                        colors  = ButtonDefaults.buttonColors(
                            containerColor         = Red,
                            disabledContainerColor = Muted.copy(alpha = 0.15f),
                            disabledContentColor   = Muted
                        )
                    ) {
                        Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text       = if (selectedIds.isEmpty()) "Șterge selecția"
                                         else "Șterge (${selectedIds.size})",
                            fontSize   = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    TextButton(
                        onClick = { selectionMode = false; selectedIds = emptySet() },
                        colors  = ButtonDefaults.textButtonColors(contentColor = Muted)
                    ) {
                        Text("Anulează", fontSize = 13.sp)
                    }
                }
            }
        }
        HorizontalDivider(color = Divider)

        LazyColumn(
            modifier            = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            contentPadding      = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!selectionMode) {
                item {
                    Text("JURNAL COMENZI", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Muted, letterSpacing = 1.sp)
                    Spacer(Modifier.height(4.dp))
                }
            }
            items(historyOrders) { order ->
                HistoryOrderCard(
                    order          = order,
                    selectionMode  = selectionMode,
                    isSelected     = order.id in selectedIds,
                    onToggleSelect = {
                        selectedIds = if (order.id in selectedIds) selectedIds - order.id
                                      else selectedIds + order.id
                    }
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun HistoryStatCard(label: String, value: String, accent: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontSize = 11.sp, color = Muted, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp)
        Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = accent)
    }
}

@Composable
private fun HistoryOrderCard(
    order:          Order,
    selectionMode:  Boolean = false,
    isSelected:     Boolean = false,
    onToggleSelect: () -> Unit = {}
) {
    val isWalkedOut = order.status == "WALKED_OUT"
    val accent      = if (isWalkedOut) Red else Green

    Surface(
        shape  = RoundedCornerShape(14.dp),
        color  = if (isSelected) accent.copy(alpha = 0.14f) else accent.copy(alpha = 0.06f),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) accent else accent.copy(alpha = 0.18f),
                shape = RoundedCornerShape(14.dp)
            )
            .then(if (selectionMode) Modifier.clickable { onToggleSelect() } else Modifier)
    ) {
        Row(
            modifier              = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Checkbox(
                    checked         = isSelected,
                    onCheckedChange = { onToggleSelect() },
                    colors          = CheckboxDefaults.colors(
                        checkedColor   = accent,
                        uncheckedColor = Muted.copy(alpha = 0.4f)
                    )
                )
                Spacer(Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f).padding(end = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = RoundedCornerShape(8.dp), color = accent.copy(alpha = 0.15f)) {
                        Text(
                            "Masă ${order.tableNumber}",
                            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = accent,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Text(formatTimeAgo(order.timestamp), fontSize = 12.sp, color = Muted)
                    if (isWalkedOut) {
                        Surface(shape = RoundedCornerShape(6.dp), color = Red.copy(alpha = 0.15f)) {
                            Text(
                                "NEPLĂTIT",
                                fontSize = 10.sp, fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp, color = Red,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
                if (order.items.isNotEmpty()) {
                    Text(
                        order.items.joinToString(" · ") { "${it.name} ×${it.quantity}" },
                        fontSize = 13.sp, color = White.copy(alpha = 0.60f),
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (!selectionMode) {
                if (isWalkedOut) {
                    Icon(Icons.Rounded.ExitToApp, contentDescription = null, tint = Red.copy(alpha = 0.65f), modifier = Modifier.size(22.dp))
                } else if (order.totalPrice > 0.0) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${"%.2f".format(order.totalPrice)}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = White)
                        Text("RON", fontSize = 11.sp, color = Muted)
                    }
                } else {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Green, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}
