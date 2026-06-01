import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.TableRestaurant
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quickbite.models.TableStatus
import com.google.cloud.firestore.Firestore
import kotlinx.coroutines.launch
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

// ── Colours ───────────────────────────────────────────────────────────────────

private val QrOrange  = Color(0xFFE8430A)
private val QrGreen   = Color(0xFF34C759)
private val QrRed     = Color(0xFFFF3B30)
private val QrBg      = Color(0xFF111111)
private val QrSurface = Color(0xFF1C1C1E)
private val QrDim     = Color(0xFF2C2C2E)
private val QrDivider = Color(0xFF3A3A3C)
private val QrMuted   = Color(0xFF8E8E93)
private val QrWhite   = Color(0xFFFFFFFF)

private const val QR_PIXEL_SIZE = 400

private data class TableEntry(val number: Int, val status: String)

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun QrManagerScreen(
    modifier:     Modifier  = Modifier,
    restaurantId: String    = "",
    db:           Firestore? = null
) {
    var tables          by remember { mutableStateOf<List<TableEntry>>(emptyList()) }
    var selectedNum     by remember { mutableStateOf<Int?>(null) }
    var newTableInput   by remember { mutableStateOf("") }
    var addError        by remember { mutableStateOf(false) }
    var bulkInput       by remember { mutableStateOf("") }
    var bulkError       by remember { mutableStateOf(false) }
    var qrBitmap        by remember { mutableStateOf<ImageBitmap?>(null) }
    var qrRawImage      by remember { mutableStateOf<BufferedImage?>(null) }
    var qrDeepLink      by remember { mutableStateOf("") }
    var saveMessage     by remember { mutableStateOf<String?>(null) }
    val scope           = rememberCoroutineScope()

    // Live table listener from Firestore
    if (db != null && restaurantId.isNotBlank()) {
        DisposableEffect(restaurantId) {
            val reg = db.collection("restaurants").document(restaurantId)
                .collection("tables")
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot == null) return@addSnapshotListener
                    tables = snapshot.documents.mapNotNull { doc ->
                        val num    = doc.getLong("tableNumber")?.toInt() ?: return@mapNotNull null
                        val status = doc.getString("status") ?: TableStatus.FREE
                        TableEntry(num, status)
                    }.sortedBy { it.number }
                }
            onDispose { reg.remove() }
        }
    }

    // Regenerate QR whenever selection changes
    // Format: "restaurantId_tableDocumentId_tableNumber" — parsed by the in-app QR scanner
    LaunchedEffect(selectedNum, restaurantId) {
        val num = selectedNum
        if (num != null) {
            val rid     = restaurantId.ifBlank { "demo-restaurant" }
            val content = "${rid}_${num}_${num}"
            val img     = renderQrBufferedImage(content)
            qrRawImage = img
            qrBitmap   = img.toComposeImageBitmap()
            qrDeepLink = content
            saveMessage = null
        } else {
            qrBitmap = null
            qrRawImage = null
            qrDeepLink = ""
        }
    }

    // ── Layout: left panel + right panel ─────────────────────────────────────
    Row(modifier = modifier.background(QrBg)) {

        // ── Left: table list ─────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .width(320.dp)
                .fillMaxHeight()
                .background(QrSurface)
        ) {
            // Header
            Row(
                modifier              = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.TableRestaurant, contentDescription = null, tint = QrOrange, modifier = Modifier.size(20.dp))
                    Text("Mese", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = QrWhite)
                }
                Text("${tables.size} total", fontSize = 12.sp, color = QrMuted)
            }

            HorizontalDivider(color = QrDivider)

            // Add table row
            if (db != null) {
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value         = newTableInput,
                        onValueChange = { newTableInput = it.filter { c -> c.isDigit() }; addError = false },
                        label         = { Text("Nr. masă", fontSize = 12.sp) },
                        singleLine    = true,
                        isError       = addError,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier      = Modifier.weight(1f),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = QrOrange,
                            focusedLabelColor  = QrOrange,
                            cursorColor        = QrOrange
                        )
                    )
                    Button(
                        onClick = {
                            val num = newTableInput.trim().toIntOrNull()
                            if (num == null || num < 1) { addError = true; return@Button }
                            if (tables.any { it.number == num }) { addError = true; return@Button }
                            scope.launch {
                                db.collection("restaurants").document(restaurantId)
                                    .collection("tables").document(num.toString())
                                    .set(mapOf("tableNumber" to num, "status" to TableStatus.FREE))
                            }
                            newTableInput = ""
                        },
                        shape  = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = QrOrange),
                        modifier = Modifier.height(56.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
                if (addError) {
                    Text(
                        "Număr invalid sau deja existent.",
                        fontSize = 11.sp, color = QrRed,
                        modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp)
                    )
                }
                HorizontalDivider(color = QrDivider)

                // Bulk generate: create tables 1..N
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value         = bulkInput,
                        onValueChange = { bulkInput = it.filter { c -> c.isDigit() }; bulkError = false },
                        label         = { Text("Generează 1 – N", fontSize = 11.sp) },
                        singleLine    = true,
                        isError       = bulkError,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier      = Modifier.weight(1f),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = QrOrange,
                            focusedLabelColor  = QrOrange,
                            cursorColor        = QrOrange
                        )
                    )
                    Button(
                        onClick = {
                            val n = bulkInput.trim().toIntOrNull()
                            if (n == null || n < 1 || n > 200) { bulkError = true; return@Button }
                            scope.launch {
                                val colRef = db.collection("restaurants").document(restaurantId).collection("tables")
                                val existing = tables.map { it.number }.toSet()
                                val toCreate = (1..n).filter { it !in existing }
                                toCreate.chunked(499).forEach { chunk ->
                                    val batch = db.batch()
                                    chunk.forEach { num ->
                                        batch.set(colRef.document(num.toString()),
                                            mapOf("tableNumber" to num, "status" to TableStatus.FREE))
                                    }
                                    batch.commit().get()
                                }
                            }
                            bulkInput = ""
                        },
                        shape    = RoundedCornerShape(10.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = QrDim),
                        modifier = Modifier.height(56.dp)
                    ) {
                        Text("Gen", color = QrWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (bulkError) {
                    Text(
                        "Introdu un număr între 1 și 200.",
                        fontSize = 11.sp, color = QrRed,
                        modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp)
                    )
                }
                HorizontalDivider(color = QrDivider)
            }

            // Table list
            if (tables.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.TableRestaurant, contentDescription = null, tint = QrMuted, modifier = Modifier.size(40.dp))
                        Text(
                            if (db == null) "Pornești în mod demo" else "Nicio masă adăugată",
                            fontSize = 13.sp, color = QrMuted, textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier       = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(tables) { table ->
                        TableRow(
                            table      = table,
                            isSelected = selectedNum == table.number,
                            onSelect   = { selectedNum = if (selectedNum == table.number) null else table.number },
                            onDelete   = if (db != null) {
                                {
                                    scope.launch {
                                        db.collection("restaurants").document(restaurantId)
                                            .collection("tables").document(table.number.toString())
                                            .delete()
                                    }
                                    if (selectedNum == table.number) selectedNum = null
                                }
                            } else null
                        )
                    }
                }
            }
        }

        Box(Modifier.width(1.dp).fillMaxHeight().background(QrDivider))

        // ── Right: QR preview ─────────────────────────────────────────────────
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight().background(QrBg),
            contentAlignment = Alignment.Center
        ) {
            if (selectedNum == null || qrBitmap == null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Rounded.QrCode2, contentDescription = null, tint = QrMuted, modifier = Modifier.size(64.dp))
                    Text("Selectează o masă pentru a vedea QR-ul", fontSize = 15.sp, color = QrMuted, textAlign = TextAlign.Center)
                    Text("Selectează sau adaugă o masă din panoul din stânga.", fontSize = 13.sp, color = QrMuted.copy(alpha = 0.6f), textAlign = TextAlign.Center)
                }
            } else {
                Column(
                    modifier            = Modifier.widthIn(max = 400.dp).padding(36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Text(
                        "QR Masă $selectedNum",
                        fontSize = 18.sp, fontWeight = FontWeight.Bold, color = QrWhite
                    )

                    // QR in white frame
                    Box(
                        modifier = Modifier
                            .size(240.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White)
                            .border(1.dp, QrDivider, RoundedCornerShape(18.dp))
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap             = qrBitmap!!,
                            contentDescription = "QR Masă $selectedNum",
                            modifier           = Modifier.fillMaxSize()
                        )
                    }

                    // Deep-link label
                    Surface(shape = RoundedCornerShape(10.dp), color = QrDim) {
                        Text(
                            qrDeepLink,
                            fontSize  = 12.sp, color = QrMuted, fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier  = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    // Save button
                    Button(
                        onClick = {
                            val raw = qrRawImage ?: return@Button
                            saveMessage = saveQrToDisk(raw, selectedNum.toString())
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = QrDim)
                    ) {
                        Icon(Icons.Rounded.Download, null, tint = QrWhite, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Salvează / Printează PNG", fontWeight = FontWeight.SemiBold, color = QrWhite, fontSize = 14.sp)
                    }

                    saveMessage?.let { msg ->
                        Text(
                            msg,
                            fontSize = 12.sp,
                            color    = if (msg.startsWith("Salvat")) QrGreen else QrRed,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

// ── Table row ─────────────────────────────────────────────────────────────────

@Composable
private fun TableRow(
    table:     TableEntry,
    isSelected: Boolean,
    onSelect:  () -> Unit,
    onDelete:  (() -> Unit)?
) {
    val isOccupied = table.status == TableStatus.OCCUPIED
    val accent     = if (isOccupied) QrRed else QrGreen

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) QrOrange.copy(alpha = 0.12f) else Color.Transparent)
            .clickable { onSelect() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Status dot
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(accent)
        )
        // Table number + status
        Column(modifier = Modifier.weight(1f)) {
            Text("Masa ${table.number}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = QrWhite)
            Text(
                if (isOccupied) "OCUPATĂ" else "LIBERĂ",
                fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = accent, letterSpacing = 0.5.sp
            )
        }
        // QR indicator when selected
        if (isSelected) {
            Icon(Icons.Rounded.QrCode2, contentDescription = null, tint = QrOrange, modifier = Modifier.size(16.dp))
        }
        // Delete button
        if (onDelete != null) {
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.Delete, contentDescription = "Șterge masa", tint = QrMuted.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ── QR generation ─────────────────────────────────────────────────────────────

private fun renderQrBufferedImage(content: String): BufferedImage {
    val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN           to 2
    )
    val writer = QRCodeWriter()
    val matrix = writer.encode(content, BarcodeFormat.QR_CODE, QR_PIXEL_SIZE, QR_PIXEL_SIZE, hints)
    val bmp    = BufferedImage(QR_PIXEL_SIZE, QR_PIXEL_SIZE, BufferedImage.TYPE_INT_ARGB)
    for (x in 0 until QR_PIXEL_SIZE) {
        for (y in 0 until QR_PIXEL_SIZE) {
            bmp.setRGB(x, y, if (matrix[x, y]) 0xFF1C1C1E.toInt() else 0xFFFFFFFF.toInt())
        }
    }
    return bmp
}

// ── File save ─────────────────────────────────────────────────────────────────

private fun saveQrToDisk(image: BufferedImage, tableNumber: String): String {
    return try {
        val downloads = File("${System.getProperty("user.home")}/Downloads")
        val output    = File(downloads, "qr_masa_$tableNumber.png")
        ImageIO.write(image, "PNG", output)
        "Salvat: ${output.absolutePath}"
    } catch (e: Exception) {
        "Eroare la salvare: ${e.message}"
    }
}
