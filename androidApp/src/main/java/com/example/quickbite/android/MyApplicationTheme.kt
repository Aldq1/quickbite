package com.example.quickbite.android

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── QuickBite brand tokens ────────────────────────────────────────────────────

object QBColors {
    val DeepOrange      = Color(0xFFFF6D00)   // primary
    val DeepOrangeDark  = Color(0xFFE65100)   // pressed / container
    val DarkSlate       = Color(0xFF263238)   // secondary
    val SoftAmber       = Color(0xFFFFB74D)   // tertiary / accent
    val ErrorRed        = Color(0xFFFF3B30)

    // Dark scheme surfaces
    val DarkBackground  = Color(0xFF0D0D0D)
    val DarkSurface     = Color(0xFF1A1A1A)
    val DarkSurfaceVar  = Color(0xFF252525)

    // Light scheme surfaces
    val LightBackground = Color(0xFFF7F7F7)
    val LightSurface    = Color(0xFFFFFFFF)
    val LightSurfaceVar = Color(0xFFF2F2F2)
}

private val QuickBiteDarkScheme = darkColorScheme(
    primary            = QBColors.DeepOrange,
    onPrimary          = Color.White,
    primaryContainer   = QBColors.DeepOrangeDark,
    onPrimaryContainer = Color(0xFFFFDBCC),

    secondary          = QBColors.DarkSlate,
    onSecondary        = Color.White,
    secondaryContainer = Color(0xFF37474F),
    onSecondaryContainer = Color(0xFFCFD8DC),

    tertiary           = QBColors.SoftAmber,
    onTertiary         = Color(0xFF1C1C1E),
    tertiaryContainer  = Color(0xFFE65100),
    onTertiaryContainer = Color(0xFFFFE0B2),

    background         = QBColors.DarkBackground,
    onBackground       = Color.White,
    surface            = QBColors.DarkSurface,
    onSurface          = Color.White,
    surfaceVariant     = QBColors.DarkSurfaceVar,
    onSurfaceVariant   = Color(0xFFAAAAAA),
    outline            = Color(0xFF3A3A3C),

    error              = QBColors.ErrorRed,
    onError            = Color.White,
)

private val QuickBiteLightScheme = lightColorScheme(
    primary            = QBColors.DeepOrange,
    onPrimary          = Color.White,
    primaryContainer   = Color(0xFFFFDBCC),
    onPrimaryContainer = Color(0xFF5C1500),

    secondary          = QBColors.DarkSlate,
    onSecondary        = Color.White,
    secondaryContainer = Color(0xFFCFD8DC),
    onSecondaryContainer = Color(0xFF102027),

    tertiary           = Color(0xFFE65100),
    onTertiary         = Color.White,
    tertiaryContainer  = Color(0xFFFFE0B2),
    onTertiaryContainer = Color(0xFF4A1500),

    background         = QBColors.LightBackground,
    onBackground       = Color(0xFF1C1C1E),
    surface            = QBColors.LightSurface,
    onSurface          = Color(0xFF1C1C1E),
    surfaceVariant     = QBColors.LightSurfaceVar,
    onSurfaceVariant   = Color(0xFF666666),
    outline            = Color(0xFFDDDDDD),

    error              = QBColors.ErrorRed,
    onError            = Color.White,
)

// ── Typography ────────────────────────────────────────────────────────────────

private val QuickBiteTypography = Typography(
    displayLarge  = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 57.sp, letterSpacing = (-0.5).sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold,    fontSize = 26.sp, letterSpacing = (-0.3).sp),
    headlineSmall  = TextStyle(fontWeight = FontWeight.Bold,    fontSize = 22.sp),

    titleLarge  = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    titleSmall  = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),

    bodyLarge  = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall  = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp),

    labelLarge  = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 0.3.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.5.sp),
    labelSmall  = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.8.sp),
)

// ── Shapes ────────────────────────────────────────────────────────────────────

private val QuickBiteShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small      = RoundedCornerShape(10.dp),
    medium     = RoundedCornerShape(16.dp),
    large      = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

// ── Theme entry point ─────────────────────────────────────────────────────────

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,   // always dark for competition demo
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) QuickBiteDarkScheme else QuickBiteLightScheme,
        typography  = QuickBiteTypography,
        shapes      = QuickBiteShapes,
        content     = content
    )
}
