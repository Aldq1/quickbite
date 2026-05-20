import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Timer
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
import com.example.quickbite.models.Order
import com.example.quickbite.models.OrderStatus
import com.google.cloud.firestore.Firestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

// ── Colours (mirrors WaiterDashboardScreen palette) ──────────────────────────

private val KdsOrange  = Color(0xFFE8430A)
private val KdsAmber   = Color(0xFFFF9F0A)
private val KdsGreen   = Color(0xFF34C759)
private val KdsRed     = Color(0xFFFF3B30)
private val KdsBg      = Color(0xFF111111)
private val KdsSurface = Color(0xFF1C1C1E)
private val KdsDim     = Color(0xFF2C2C2E)
private val KdsDivider = Color(0xFF3A3A3C)
private val KdsMuted   = Color(0xFF8E8E93)
private val KdsWhite   = Color(0xFFFFFFFF)

// Receipt paper colours — light ticket body, dark ink
private val KdsPaper     = Color(0xFFFFFBF0)   // warm cream (receipt paper)
private val KdsPaperInk  = Color(0xFF1C1C1E)   // near-black primary text
private val KdsPaperMuted= Color(0xFF6B6568)   // secondary/muted text
private val KdsPaperRow  = Color(0xFFF2EBD8)   // item row stripe

// ── Firestore-backed entry point ──────────────────────────────────────────────

@Composable
fun KitchenDisplayScreen(modifier: Modifier = Modifier, db: Firestore, restaurantId: String) {
    var kdsOrders by remember { mutableStateOf<List<Order>>(emptyList()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(restaurantId) {
        kdsOrdersFlow(db, restaurantId).collect { kdsOrders = it }
    }

    KitchenDisplayContent(
        modifier  = modifier,
        orders    = kdsOrders,
        onAccept  = { order ->
            scope.launch {
                db.collection("orders").document(order.id)
                    .update("status", OrderStatus.COOKING)
            }
        },
        onReady   = { order ->
            // COOKING → DELIVERED: ticket leaves KDS and appears as "GATA DE SERVIT" on Waiter panel
            scope.launch {
                db.collection("orders").document(order.id)
                    .update("status", OrderStatus.DELIVERED)
            }
        }
    )
}

// ── Pure-UI content (also used by demo / mock mode) ──────────────────────────

@Composable
internal fun KitchenDisplayContent(
    modifier: Modifier = Modifier,
    orders: List<Order>,
    onAccept: (Order) -> Unit,
    onReady: (Order) -> Unit
) {
    val pendingOrders = remember(orders) {
        orders.filter { it.status == OrderStatus.PENDING }.sortedBy { it.timestamp }
    }
    val cookingOrders = remember(orders) {
        orders.filter { it.status == OrderStatus.COOKING }.sortedBy { it.timestamp }
    }

    Column(modifier = modifier.background(KdsBg)) {
        KdsHeader(pendingCount = pendingOrders.size, cookingCount = cookingOrders.size)
        HorizontalDivider(color = KdsDivider)

        if (pendingOrders.isEmpty() && cookingOrders.isEmpty()) {
            KdsEmptyState(modifier = Modifier.fillMaxSize())
            return@Column
        }

        Row(modifier = Modifier.fillMaxSize()) {
            // ── PENDING column ────────────────────────────────────────────────
            KdsColumn(
                modifier       = Modifier.weight(1f).fillMaxHeight(),
                title          = "DE PREGĂTIT",
                count          = pendingOrders.size,
                accentColor    = KdsOrange,
                orders         = pendingOrders,
                primaryLabel   = "Accept — Intră în Preparare",
                onPrimary      = onAccept
            )

            Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(KdsDivider))

            // ── COOKING column ────────────────────────────────────────────────
            KdsColumn(
                modifier       = Modifier.weight(1f).fillMaxHeight(),
                title          = "ÎN PREPARARE",
                count          = cookingOrders.size,
                accentColor    = KdsAmber,
                orders         = cookingOrders,
                primaryLabel   = "Gata! — Cheamă Chelnerul",
                onPrimary      = onReady
            )
        }
    }
}

// ── Header ────────────────────────────────────────────────────────────────────

@Composable
private fun KdsHeader(pendingCount: Int, cookingCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(KdsSurface)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Rounded.Restaurant, contentDescription = null, tint = KdsOrange, modifier = Modifier.size(24.dp))
            Text("Kitchen Display System", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = KdsWhite)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (pendingCount > 0) KdsPill("$pendingCount în așteptare", KdsOrange)
            if (cookingCount > 0) KdsPill("$cookingCount în preparare", KdsAmber)
        }
    }
}

@Composable
private fun KdsPill(label: String, color: Color) {
    Surface(shape = RoundedCornerShape(20.dp), color = color.copy(alpha = 0.18f)) {
        Text(
            label,
            color      = color,
            fontSize   = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier   = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
        )
    }
}

// ── Kanban column ─────────────────────────────────────────────────────────────

@Composable
private fun KdsColumn(
    modifier: Modifier,
    title: String,
    count: Int,
    accentColor: Color,
    orders: List<Order>,
    primaryLabel: String,
    onPrimary: (Order) -> Unit
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(accentColor.copy(alpha = 0.09f))
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                fontSize      = 12.sp,
                fontWeight    = FontWeight.ExtraBold,
                color         = accentColor,
                letterSpacing = 1.2.sp
            )
            if (count > 0) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentColor.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(count.toString(), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = accentColor)
                }
            }
        }
        HorizontalDivider(color = accentColor.copy(alpha = 0.25f), thickness = 1.dp)

        if (orders.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nicio comandă", fontSize = 14.sp, color = KdsMuted, textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(
                modifier            = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                contentPadding      = PaddingValues(vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(orders, key = { it.id }) { order ->
                    KdsTicketCard(
                        order        = order,
                        accentColor  = accentColor,
                        primaryLabel = primaryLabel,
                        onPrimary    = { onPrimary(order) }
                    )
                }
            }
        }
    }
}

// ── Receipt-style decorations ─────────────────────────────────────────────────

@Composable
private fun ZigzagEdge(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxWidth().height(10.dp)) {
        val tw   = 16.dp.toPx()
        val h    = size.height
        val w    = size.width
        val path = Path()
        path.moveTo(0f, 0f)
        path.lineTo(w, 0f)
        // Zigzag teeth pointing downward, traced right → left
        var x = w
        while (x > 0f) {
            val mid  = maxOf(x - tw / 2f, 0f)
            val next = maxOf(x - tw, 0f)
            path.lineTo(mid, h)
            path.lineTo(next, 0f)
            x -= tw
        }
        path.close()
        drawPath(path, color = color)
    }
}

@Composable
private fun DashedDivider(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxWidth().height(1.dp)) {
        drawLine(
            color       = color,
            start       = Offset(0f, 0f),
            end         = Offset(size.width, 0f),
            strokeWidth = 1.dp.toPx(),
            pathEffect  = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
        )
    }
}

// ── Ticket card ───────────────────────────────────────────────────────────────

@Composable
private fun KdsTicketCard(
    order: Order,
    accentColor: Color,
    primaryLabel: String,
    onPrimary: () -> Unit
) {
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(order.timestamp) {
        while (true) { delay(30_000L); tick++ }
    }

    val elapsedMinutes = remember(tick, order.timestamp) {
        if (order.timestamp == 0L) 0L else (System.currentTimeMillis() - order.timestamp) / 60_000L
    }
    val elapsedText  = remember(tick, order.timestamp) { formatKdsElapsed(order.timestamp) }
    val isOverdue    = elapsedMinutes >= 10
    val ticketAccent = if (isOverdue) KdsRed else accentColor

    Column(modifier = Modifier.fillMaxWidth()) {
        // ── Receipt zigzag top strip ──────────────────────────────────────────
        ZigzagEdge(color = ticketAccent.copy(alpha = 0.82f))

        // ── Ticket body (light receipt paper) ─────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
                .background(KdsPaper)
                .border(
                    width = 1.5.dp,
                    color = ticketAccent.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp)
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Header ────────────────────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Surface(shape = RoundedCornerShape(10.dp), color = accentColor.copy(alpha = 0.15f)) {
                    Text(
                        "MASA ${order.tableNumber}",
                        fontSize      = 22.sp,
                        fontWeight    = FontWeight.ExtraBold,
                        color         = accentColor,
                        modifier      = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        letterSpacing = 0.5.sp
                    )
                }
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Rounded.Timer,
                        contentDescription = null,
                        tint     = if (isOverdue) KdsRed else KdsPaperMuted,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        elapsedText,
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = if (isOverdue) KdsRed else KdsPaperInk
                    )
                }
            }

            // Dashed separator (receipt rule line)
            DashedDivider(color = KdsPaperMuted.copy(alpha = 0.50f))

            // ── Items (large qty on left for kitchen readability) ─────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (order.items.isEmpty()) {
                    Text("Niciun produs înregistrat.", fontSize = 13.sp, color = KdsPaperMuted)
                } else {
                    order.items.forEach { item ->
                        Row(
                            modifier          = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(KdsPaperRow)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Large quantity — readable from across the kitchen
                            Text(
                                "${item.quantity}×",
                                fontSize   = 28.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color      = ticketAccent,
                                modifier   = Modifier.width(56.dp),
                                textAlign  = TextAlign.Center
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = KdsPaperInk)
                                if (item.category.isNotBlank()) {
                                    Text(item.category, fontSize = 11.sp, color = KdsPaperMuted)
                                }
                            }
                        }
                    }
                }
            }

            // Dashed separator before button
            DashedDivider(color = KdsPaperMuted.copy(alpha = 0.35f))

            // ── Action button ─────────────────────────────────────────────────
            Button(
                onClick  = onPrimary,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = accentColor)
            ) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = KdsWhite, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(primaryLabel, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = KdsWhite)
            }
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun KdsEmptyState(modifier: Modifier) {
    Box(modifier = modifier.background(KdsBg), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Rounded.Restaurant, contentDescription = null, tint = KdsMuted, modifier = Modifier.size(56.dp))
            Text("Nicio comandă activă", fontSize = 18.sp, color = KdsMuted, fontWeight = FontWeight.Medium)
            Text(
                "Comenzile noi vor apărea automat.",
                fontSize  = 14.sp,
                color     = KdsMuted.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

// ── Firestore flow: PENDING + COOKING orders ──────────────────────────────────

private fun kdsOrdersFlow(db: Firestore, restaurantId: String): Flow<List<Order>> = callbackFlow {
    val registration = db.collection("orders")
        .whereEqualTo("restaurantId", restaurantId)
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                println("DESKTOP KDS ERROR: Firestore listener error — ${error.javaClass.simpleName}: ${error.message}")
                return@addSnapshotListener
            }
            if (snapshot == null) {
                println("DESKTOP KDS: Snapshot is null")
                return@addSnapshotListener
            }
            println("DESKTOP KDS: Snapshot received with ${snapshot.documents.size} documents for restaurantId=$restaurantId")
            val active = snapshot.documents
                .mapNotNull { doc ->
                    try {
                        doc.toOrder()
                    } catch (e: Exception) {
                        println("DESKTOP KDS ERROR: Failed to parse document ${doc.id} — ${e.javaClass.simpleName}: ${e.message}")
                        null
                    }
                }
                .filter { it.status == OrderStatus.PENDING || it.status == OrderStatus.COOKING }
                .sortedBy { it.timestamp }
            trySend(active)
        }
    awaitClose { registration.remove() }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun formatKdsElapsed(timestamp: Long): String {
    if (timestamp == 0L) return "—"
    val totalSeconds = (System.currentTimeMillis() - timestamp) / 1000L
    val mins = totalSeconds / 60
    return if (mins == 0L) "<1 min" else "$mins min"
}
