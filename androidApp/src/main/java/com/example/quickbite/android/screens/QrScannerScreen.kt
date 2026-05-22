package com.example.quickbite.android.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.QrCode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

// ── Palette (matches ClientHomeFeedScreen dark theme) ─────────────────────────
private val QBg          = Color(0xFF0C0C0C)
private val QBrand       = Color(0xFFE8430A)
private val QWhite       = Color(0xFFFFFFFF)
private val QMuted       = Color(0xFF9A9A9A)
private val QGlass       = Color(0x14FFFFFF)
private val QGlassBorder = Color(0x1AFFFFFF)
private val QSurface     = Color(0xFF161616)
private val QRed         = Color(0xFFFF3B30)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(
    expectedRestaurantId: String = "",
    onRestaurantScanned : (restaurantId: String) -> Unit,
    onBack              : () -> Unit
) {
    val context = LocalContext.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    var manualId       by remember { mutableStateOf("") }
    var showManual     by remember { mutableStateOf(false) }
    var inputError     by remember { mutableStateOf<String?>(null) }
    var hasScanned     by remember { mutableStateOf(false) }
    var scanError      by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val onScanned: (String) -> Unit = remember {
        { rawValue ->
            if (!hasScanned && rawValue.isNotBlank()) {
                val parts = rawValue.trim().split("_")
                val scannedRestId = parts.getOrNull(0) ?: ""
                if (expectedRestaurantId.isNotBlank() && scannedRestId != expectedRestaurantId) {
                    scanError = "QR-ul aparține altui restaurant!"
                } else {
                    hasScanned = true
                    scanError  = null
                    onRestaurantScanned(rawValue.trim())
                }
            }
        }
    }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(QBg)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Înapoi", tint = QWhite)
                }
                Text(
                    text       = "Scanează Restaurant",
                    modifier   = Modifier.align(Alignment.Center),
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color      = QWhite
                )
            }
        },
        containerColor = QBg
    ) { padding ->
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(28.dp))

            Text(
                text      = "Pointează camera spre codul QR afișat la restaurant",
                fontSize  = 14.sp,
                color     = QMuted,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(28.dp))

            // ── Camera viewport ────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .border(2.dp, QBrand, RoundedCornerShape(20.dp))
            ) {
                when {
                    hasCameraPermission && !hasScanned -> {
                        CameraQrPreview(
                            modifier          = Modifier.fillMaxSize(),
                            onBarcodeDetected = onScanned
                        )
                        // Corner brackets overlay
                        QrViewfinderOverlay(modifier = Modifier.fillMaxSize())
                    }
                    !hasCameraPermission -> {
                        Box(
                            modifier         = Modifier
                                .fillMaxSize()
                                .background(QSurface),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.QrCode, null,
                                    tint     = QMuted,
                                    modifier = Modifier.size(52.dp)
                                )
                                Text(
                                    "Camera necesită permisiune",
                                    fontSize  = 13.sp,
                                    color     = QMuted,
                                    textAlign = TextAlign.Center
                                )
                                TextButton(
                                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }
                                ) {
                                    Text("Acordă acces", color = QBrand, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    else -> {
                        // Scanned — show confirmation overlay while navigating
                        Box(
                            modifier         = Modifier
                                .fillMaxSize()
                                .background(QBrand.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = QBrand)
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Wrong-restaurant error banner ──────────────────────────────
            AnimatedVisibility(visible = scanError != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(QRed.copy(alpha = 0.12f))
                        .border(1.dp, QRed.copy(alpha = 0.40f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier            = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            scanError ?: "",
                            color      = QRed,
                            fontWeight = FontWeight.SemiBold,
                            fontSize   = 14.sp,
                            textAlign  = TextAlign.Center
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "Scanați QR-ul de la masa restaurantului corect.",
                            color     = QMuted,
                            fontSize  = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Divider with label ─────────────────────────────────────────
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Divider(modifier = Modifier.weight(1f), color = QGlassBorder)
                Text("sau", fontSize = 12.sp, color = QMuted)
                Divider(modifier = Modifier.weight(1f), color = QGlassBorder)
            }

            Spacer(Modifier.height(16.dp))

            // ── Manual input toggle ────────────────────────────────────────
            OutlinedButton(
                onClick = { showManual = !showManual },
                shape   = RoundedCornerShape(14.dp),
                border  = androidx.compose.foundation.BorderStroke(1.dp, QGlassBorder),
                colors  = ButtonDefaults.outlinedButtonColors(contentColor = QMuted)
            ) {
                Icon(Icons.Rounded.QrCode, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (showManual) "Ascunde input manual" else "Introdu ID manual (fallback demo)",
                    fontSize = 13.sp
                )
            }

            Spacer(Modifier.height(16.dp))

            AnimatedVisibility(visible = showManual) {
                Column(
                    modifier            = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value         = manualId,
                        onValueChange = { manualId = it; inputError = null },
                        label         = { Text("Restaurant UID", color = QMuted) },
                        placeholder   = { Text("ex: 7QBG68DH1bciyywU...", color = QMuted.copy(alpha = 0.5f)) },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth(),
                        shape         = RoundedCornerShape(14.dp),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = QBrand,
                            unfocusedBorderColor = QGlassBorder,
                            focusedTextColor     = QWhite,
                            unfocusedTextColor   = QWhite,
                            cursorColor          = QBrand,
                            focusedLabelColor    = QBrand
                        )
                    )
                    if (inputError != null) {
                        Text(inputError!!, color = QRed, fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            val trimmed = manualId.trim()
                            if (trimmed.isBlank()) {
                                inputError = "ID-ul nu poate fi gol."
                            } else {
                                onRestaurantScanned(trimmed)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape  = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = QBrand)
                    ) {
                        Text(
                            "Conectează-te la Restaurant",
                            color      = QWhite,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize   = 15.sp
                        )
                    }
                }
            }
        }
    }
}

// ── Corner bracket overlay ────────────────────────────────────────────────────

@Composable
private fun QrViewfinderOverlay(modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(12.dp)) {
        // Top-left
        Box(Modifier.align(Alignment.TopStart).size(28.dp).border(
            width = 3.dp, color = QWhite,
            shape = RoundedCornerShape(topStart = 8.dp)
        ))
        // Top-right
        Box(Modifier.align(Alignment.TopEnd).size(28.dp).border(
            width = 3.dp, color = QWhite,
            shape = RoundedCornerShape(topEnd = 8.dp)
        ))
        // Bottom-left
        Box(Modifier.align(Alignment.BottomStart).size(28.dp).border(
            width = 3.dp, color = QWhite,
            shape = RoundedCornerShape(bottomStart = 8.dp)
        ))
        // Bottom-right
        Box(Modifier.align(Alignment.BottomEnd).size(28.dp).border(
            width = 3.dp, color = QWhite,
            shape = RoundedCornerShape(bottomEnd = 8.dp)
        ))
    }
}

// ── CameraX + ML Kit preview ──────────────────────────────────────────────────

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraQrPreview(
    modifier         : Modifier = Modifier,
    onBarcodeDetected: (String) -> Unit
) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val executor             = remember { Executors.newSingleThreadExecutor() }
    val barcodeScanner       = remember { BarcodeScanning.getClient() }

    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
            barcodeScanner.close()
        }
    }

    AndroidView(
        modifier = modifier,
        factory  = { ctx ->
            val previewView = PreviewView(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(1280, 720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                analysis.setAnalyzer(executor) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage != null) {
                        val image = InputImage.fromMediaImage(
                            mediaImage, imageProxy.imageInfo.rotationDegrees
                        )
                        barcodeScanner.process(image)
                            .addOnSuccessListener { barcodes ->
                                barcodes
                                    .firstOrNull {
                                        it.valueType == Barcode.TYPE_TEXT ||
                                        it.rawValue != null
                                    }
                                    ?.rawValue
                                    ?.let(onBarcodeDetected)
                            }
                            .addOnCompleteListener { imageProxy.close() }
                    } else {
                        imageProxy.close()
                    }
                }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                } catch (e: Exception) {
                    android.util.Log.e("QrScanner", "Camera bind failed", e)
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        }
    )
}
