package com.bentlybro.grabbit.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

object GrabbitColors {
    val Carrot = Color(0xFFFF8A3D)
    val CarrotDeep = Color(0xFFFF5E62)
    val Mint = Color(0xFF5EE6B5)
    val Ink = Color(0xFF0D0F14)
    val Surface = Color(0xFF151821)
    val SurfaceHigh = Color(0xFF1D212C)
    val SurfaceHighest = Color(0xFF262B38)
    val Outline = Color(0xFF343A4A)
    val TextMuted = Color(0xFF9AA3B5)
    val Danger = Color(0xFFFF6B6B)

    val accentGradient = Brush.linearGradient(listOf(Carrot, CarrotDeep))
}

private val scheme = darkColorScheme(
    primary = GrabbitColors.Carrot,
    onPrimary = Color(0xFF1A0E05),
    primaryContainer = Color(0xFF3A2112),
    onPrimaryContainer = Color(0xFFFFD2B5),
    secondary = GrabbitColors.Mint,
    onSecondary = Color(0xFF00281B),
    secondaryContainer = Color(0xFF123A2E),
    onSecondaryContainer = Color(0xFFB5F5DC),
    background = GrabbitColors.Ink,
    onBackground = Color(0xFFE9ECF2),
    surface = GrabbitColors.Ink,
    onSurface = Color(0xFFE9ECF2),
    surfaceVariant = GrabbitColors.SurfaceHigh,
    onSurfaceVariant = GrabbitColors.TextMuted,
    surfaceContainerLowest = Color(0xFF0A0C10),
    surfaceContainerLow = GrabbitColors.Surface,
    surfaceContainer = GrabbitColors.Surface,
    surfaceContainerHigh = GrabbitColors.SurfaceHigh,
    surfaceContainerHighest = GrabbitColors.SurfaceHighest,
    outline = GrabbitColors.Outline,
    outlineVariant = Color(0xFF262B38),
    error = GrabbitColors.Danger,
)

private val typography = Typography().let { t ->
    t.copy(
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val shapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun GrabbitTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes, content = content)
}

val MonoSmall = TextStyle(fontSize = 12.sp, letterSpacing = 0.2.sp)
