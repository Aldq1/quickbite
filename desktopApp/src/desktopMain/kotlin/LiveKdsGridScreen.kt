import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.cloud.firestore.Firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ── Data model ────────────────────────────────────────────────────────────────

private data class LiveTable(
    val id: String = "",
    val tableNumber: Int = 0,
    val capacity: Int = 0,
    val status: String = "LIBERA"
)

// ── Constants ─────────────────────────────────────────────────────────────────

private val TABLE_STATUSES = listOf("LIBERA", "OCUPATA", "REZERVATA")

private val KdsBg       = Color(0xFF111111)
private val KdsSurface  = Color(0xFF1C1C1E)
private val KdsFree     = Color(0xFF34C759)
private val KdsBusy     = Color(0xFFFF3B30)
private val KdsReserved = Color(0xFFFF9F0A)
private val KdsText     = Color(0xFFFFFFFF)
private val KdsMuted    = Color(0xFF8E8E93)
private val KdsOrange   = Color(0xFFE8430A)

// ── Firestore live flow ───────────────────────────────────────────────────────

private fun tablesFlow(db: Firestore, restaurantId: String): Flow<List<LiveTable>> = callbackFlow {
    val reg = db.collection("restaurants")
        .document(restaurantId)
        .collection("tables")
        .addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            trySend(
                snapshot.documents.mapNotNull { doc ->
                    LiveTable(
                        id          = doc.id,
                        tableNumber = doc.getLong("tableNumber")?.toInt() ?: return@mapNotNull null,
                        capacity    = doc.getLong("capacity")?.toInt() ?: 0,
                        status      = doc.getString("status") ?: "LIBERA"
                    )
                }.sortedBy { it.tableNumber }
            )
        }
    awaitClose { reg.remove() }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun LiveKdsGridScreen(
    db: Firestore?,
    restaurantId: String,
    isFullscreen: Boolean = false,
    onBack: (() -> Unit)? = null
) {
    var tables        by remember { mutableStateOf<List<LiveTable>?>(null) }
    var selectedTable by remember { mutableStateOf<LiveTable?>(null) }
    val scope         = rememberCoroutineScope()

    LaunchedEffect(db, restaurantId) {
        if (db == null || restaurantId.isBlank()) { tables = emptyList(); return@LaunchedEffect }
        tablesFlow(db, restaurantId).collect { tables = it }
    }

    val colorScheme = darkColorScheme(
        primary          = KdsOrange,
        secondary        = KdsFree,
        background       = KdsBg,
        surface          = KdsSurface,
        onBackground     = KdsText,
        onSurface        = KdsText,
        onSurfaceVariant = KdsMuted,
        outline          = Color(0xFF3A3A3C),
    )

    MaterialTheme(colorScheme = colorScheme) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(KdsBg)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ── Header ─────────────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(KdsSurface)
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (isFullscreen && onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.Rounded.ArrowBack, null, tint = KdsMuted, modifier = Modifier.size(20.dp))
                            }
                        }
                        Box(
                            modifier         = Modifier
                                .size(38.dp)
                                .background(KdsOrange, RoundedCornerShape(11.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("QB", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        }
                        Column {
                            Text(
                                "KDS Live",
                                fontSize      = 18.sp,
                                fontWeight    = FontWeight.ExtraBold,
                                color         = KdsText,
                                letterSpacing = (-0.3).sp
                            )
                            Text(
                                if (isFullscreen) "Vizualizare ospătari" else "Mese & Status",
                                fontSize = 12.sp,
                                color    = KdsMuted
                            )
                        }
                    }

                    // Legend + counter
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        KdsLegendChip("Liberă",    KdsFree)
                        KdsLegendChip("Ocupată",   KdsBusy)
                        KdsLegendChip("Rezervată", KdsReserved)
                        tables?.let { list ->
                            val free = list.count { it.status == "LIBERA" }
                            val busy = list.count { it.status == "OCUPATA" }
                            Text("$free libere · $busy ocupate", fontSize = 12.sp, color = KdsMuted)
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF2C2C2E)))

                // ── Content ────────────────────────────────────────────────────────
                when {
                    db == null -> KdsOfflineWarning(modifier = Modifier.fillMaxSize())

                    tables == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = KdsOrange, strokeWidth = 2.5.dp)
                    }

                    tables!!.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Rounded.TableRestaurant, null, tint = KdsMuted, modifier = Modifier.size(48.dp))
                            Text(
                                "Nicio masă configurată",
                                fontSize   = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color      = KdsMuted,
                                textAlign  = TextAlign.Center
                            )
                            Text(
                                "Adăugați mese din panoul de management\npentru a le vedea aici.",
                                fontSize   = 13.sp,
                                color      = Color(0xFF636366),
                                textAlign  = TextAlign.Center,
                                lineHeight = 20.sp
                            )
                        }
                    }

                    else -> LazyVerticalGrid(
                        columns               = GridCells.Adaptive(minSize = 180.dp),
                        modifier              = Modifier.fillMaxSize(),
                        contentPadding        = PaddingValues(20.dp),
                        verticalArrangement   = Arrangement.spacedBy(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        gridItems(tables!!, key = { it.id }) { table ->
                            LiveTableCard(table = table, onClick = { selectedTable = table })
                        }
                    }
                }
            }
        }

        // ── Status change dialog ───────────────────────────────────────────────
        selectedTable?.let { table ->
            TableStatusDialog(
                table     = table,
                onDismiss = { selectedTable = null },
                onSelect  = { newStatus ->
                    selectedTable = null
                    if (db != null) {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                db.collection("restaurants")
                                    .document(restaurantId)
                                    .collection("tables")
                                    .document(table.id)
                                    .update(mapOf("status" to newStatus))
                                    .get()
                            }
                        }
                    }
                }
            )
        }
    }
}

// ── Table card ────────────────────────────────────────────────────────────────

@Composable
private fun LiveTableCard(table: LiveTable, onClick: () -> Unit) {
    val (statusColor, statusLabel) = when (table.status) {
        "OCUPATA"   -> KdsBusy     to "Ocupată"
        "REZERVATA" -> KdsReserved to "Rezervată"
        else        -> KdsFree     to "Liberă"
    }

    ElevatedCard(
        shape     = RoundedCornerShape(18.dp),
        colors    = CardDefaults.elevatedCardColors(containerColor = KdsSurface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
        modifier  = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Colored gradient header band
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(
                        Brush.linearGradient(
                            listOf(statusColor.copy(alpha = 0.25f), statusColor.copy(alpha = 0.06f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${table.tableNumber}",
                        fontSize      = 40.sp,
                        fontWeight    = FontWeight.ExtraBold,
                        color         = KdsText,
                        letterSpacing = (-1).sp
                    )
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(Icons.Rounded.Group, null, tint = KdsMuted, modifier = Modifier.size(13.dp))
                        Text("${table.capacity}", fontSize = 12.sp, color = KdsMuted)
                    }
                }
            }

            // Status badge row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(KdsSurface)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text("Masa", fontSize = 11.sp, color = KdsMuted)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusColor.copy(alpha = 0.15f))
                        .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(statusLabel, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = statusColor)
                }
            }
        }
    }
}

// ── Status change dialog ──────────────────────────────────────────────────────

@Composable
private fun TableStatusDialog(
    table: LiveTable,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest  = onDismiss,
        containerColor    = KdsSurface,
        titleContentColor = KdsText,
        textContentColor  = KdsMuted,
        title = {
            Text(
                "Masa ${table.tableNumber} — Schimbă status",
                fontWeight = FontWeight.ExtraBold,
                fontSize   = 18.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TABLE_STATUSES.forEach { status ->
                    val (color, label) = when (status) {
                        "OCUPATA"   -> KdsBusy     to "Ocupată"
                        "REZERVATA" -> KdsReserved to "Rezervată"
                        else        -> KdsFree     to "Liberă"
                    }
                    val isCurrent = status == table.status
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isCurrent) color.copy(alpha = 0.14f) else Color(0xFF2C2C2E))
                            .border(
                                1.dp,
                                if (isCurrent) color.copy(alpha = 0.45f) else Color(0xFF3A3A3C),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { onSelect(status) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text(
                            label,
                            fontSize   = 15.sp,
                            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                            color      = if (isCurrent) color else KdsText
                        )
                        if (isCurrent) {
                            Icon(Icons.Rounded.Check, null, tint = color, modifier = Modifier.size(17.dp))
                        }
                    }
                }
            }
        },
        confirmButton  = {},
        dismissButton  = {
            TextButton(onClick = onDismiss) {
                Text("Anulează", color = KdsMuted)
            }
        }
    )
}

// ── Legend chip ───────────────────────────────────────────────────────────────

@Composable
private fun KdsLegendChip(label: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.30f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(modifier = Modifier.size(6.dp).background(color, RoundedCornerShape(2.dp)))
        Text(label, fontSize = 11.sp, color = color, fontWeight = FontWeight.SemiBold)
    }
}

// ── Offline warning ───────────────────────────────────────────────────────────

@Composable
private fun KdsOfflineWarning(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Rounded.Warning, null, tint = KdsReserved, modifier = Modifier.size(40.dp))
            Text(
                "Firebase nu este inițializat.\nKDS offline.",
                fontSize   = 14.sp,
                color      = KdsMuted,
                textAlign  = TextAlign.Center,
                lineHeight = 22.sp
            )
        }
    }
}
