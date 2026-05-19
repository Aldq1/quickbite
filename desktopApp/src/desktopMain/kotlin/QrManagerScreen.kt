import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
private val QrBg      = Color(0xFF111111)
private val QrSurface = Color(0xFF1C1C1E)
private val QrDim     = Color(0xFF2C2C2E)
private val QrDivider = Color(0xFF3A3A3C)
private val QrMuted   = Color(0xFF8E8E93)
private val QrWhite   = Color(0xFFFFFFFF)

private const val QR_PIXEL_SIZE = 400

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun QrManagerScreen(modifier: Modifier = Modifier, restaurantId: String = "") {
    var tableInput   by remember { mutableStateOf("") }
    var hasError     by remember { mutableStateOf(false) }
    var qrBitmap     by remember { mutableStateOf<ImageBitmap?>(null) }
    var qrRawImage   by remember { mutableStateOf<BufferedImage?>(null) }
    var qrDeepLink   by remember { mutableStateOf("") }
    var saveMessage  by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .background(QrBg)
            .padding(36.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        // ── Section header ────────────────────────────────────────────────────
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Rounded.QrCode2, contentDescription = null, tint = QrOrange, modifier = Modifier.size(26.dp))
            Column {
                Text("Management Mese & QR", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = QrWhite)
                Text("Generează coduri QR pentru mesele restaurantului", fontSize = 13.sp, color = QrMuted)
            }
        }

        HorizontalDivider(color = QrDivider)

        // ── Generator card ────────────────────────────────────────────────────
        Surface(
            shape    = RoundedCornerShape(20.dp),
            color    = QrSurface,
            modifier = Modifier.widthIn(max = 680.dp)
        ) {
            Column(
                modifier            = Modifier.padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text("Generator QR", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = QrWhite)

                // Input row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value          = tableInput,
                        onValueChange  = { tableInput = it.filter { c -> c.isDigit() }; hasError = false; saveMessage = null },
                        label          = { Text("Număr masă") },
                        leadingIcon    = { Icon(Icons.Rounded.TableRestaurant, null, modifier = Modifier.size(18.dp)) },
                        isError        = hasError,
                        singleLine     = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier       = Modifier.width(200.dp),
                        colors         = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor  = QrOrange,
                            focusedLabelColor   = QrOrange,
                            cursorColor         = QrOrange
                        )
                    )

                    Button(
                        onClick = {
                            val num = tableInput.trim().toIntOrNull()
                            if (num == null || num < 1) { hasError = true; return@Button }
                            val rid  = restaurantId.ifBlank { "demo_quickbite_central" }
                            val link = "quickbite://order/$rid/$num"
                            val img  = renderQrBufferedImage(link)
                            qrRawImage = img
                            qrBitmap   = img.toComposeImageBitmap()
                            qrDeepLink = link
                            saveMessage = null
                        },
                        modifier = Modifier.height(56.dp),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = QrOrange)
                    ) {
                        Icon(Icons.Rounded.QrCode2, null, tint = QrWhite, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Generează QR", fontWeight = FontWeight.Bold, color = QrWhite, fontSize = 14.sp)
                    }
                }

                if (hasError) {
                    Text(
                        "Introdu un număr de masă valid (≥ 1).",
                        fontSize = 12.sp,
                        color    = Color(0xFFFF3B30)
                    )
                }
            }
        }

        // ── QR display ────────────────────────────────────────────────────────
        if (qrBitmap != null) {
            Surface(
                shape    = RoundedCornerShape(20.dp),
                color    = QrSurface,
                modifier = Modifier.widthIn(max = 680.dp)
            ) {
                Column(
                    modifier            = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Text(
                        "QR pentru Masa ${tableInput.trim()}",
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = QrWhite
                    )

                    // QR bitmap in a white rounded frame (needed for scanner contrast)
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .border(1.dp, QrDivider, RoundedCornerShape(16.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap      = qrBitmap!!,
                            contentDescription = "QR Masă ${tableInput.trim()}",
                            modifier    = Modifier.fillMaxSize()
                        )
                    }

                    // Deep-link label
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = QrDim
                    ) {
                        Text(
                            qrDeepLink,
                            fontSize   = 13.sp,
                            color      = QrMuted,
                            fontWeight = FontWeight.Medium,
                            modifier   = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            textAlign  = TextAlign.Center
                        )
                    }

                    HorizontalDivider(color = QrDivider)

                    // Save button
                    Button(
                        onClick = {
                            val raw = qrRawImage ?: return@Button
                            val msg = saveQrToDisk(raw, tableInput.trim())
                            saveMessage = msg
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = QrDim)
                    ) {
                        Icon(Icons.Rounded.Download, null, tint = QrWhite, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Printează / Salvează PNG", fontWeight = FontWeight.SemiBold, color = QrWhite, fontSize = 14.sp)
                    }

                    if (saveMessage != null) {
                        val isOk    = saveMessage!!.startsWith("Salvat")
                        val msgColor = if (isOk) QrGreen else Color(0xFFFF3B30)
                        Text(saveMessage!!, fontSize = 12.sp, color = msgColor, textAlign = TextAlign.Center)
                    }
                }
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
