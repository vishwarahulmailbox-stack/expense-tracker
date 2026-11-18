package com.expensetracker.sms.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF146C43),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEFD9),
    onPrimaryContainer = Color(0xFF00210F),
    secondary = Color(0xFF4D6356),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDEBE1),
    onSecondaryContainer = Color(0xFF0A1F14),
    tertiary = Color(0xFF3A6472),
    background = Color(0xFFF5F7F5),
    onBackground = Color(0xFF171C19),
    surface = Color(0xFFF5F7F5),
    onSurface = Color(0xFF171C19),
    surfaceVariant = Color(0xFFDDE5DE),
    onSurfaceVariant = Color(0xFF5B635D),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFBFA),
    surfaceContainer = Color(0xFFEFF3F0),
    surfaceContainerHigh = Color(0xFFE9EEEA),
    surfaceContainerHighest = Color(0xFFE3E9E4),
    outline = Color(0xFF737B75),
    outlineVariant = Color(0xFFDCE3DD),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7DD8A2),
    onPrimary = Color(0xFF00391D),
    primaryContainer = Color(0xFF0D5232),
    onPrimaryContainer = Color(0xFFCDEFD9),
    secondary = Color(0xFFB4CCBC),
    onSecondary = Color(0xFF203529),
    secondaryContainer = Color(0xFF2A3B31),
    onSecondaryContainer = Color(0xFFD0E8D8),
    tertiary = Color(0xFFA2CDDC),
    background = Color(0xFF101411),
    onBackground = Color(0xFFE0E4E0),
    surface = Color(0xFF101411),
    onSurface = Color(0xFFE0E4E0),
    surfaceVariant = Color(0xFF3F4942),
    onSurfaceVariant = Color(0xFFA9B2AB),
    surfaceContainerLowest = Color(0xFF181D1A),
    surfaceContainerLow = Color(0xFF1A1F1C),
    surfaceContainer = Color(0xFF1E2420),
    surfaceContainerHigh = Color(0xFF262C28),
    surfaceContainerHighest = Color(0xFF303632),
    outline = Color(0xFF89928B),
    outlineVariant = Color(0xFF2E3530),
    error = Color(0xFFFFB4AB),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = t.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun AppTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

/** Semantic colours that Material doesn't have. */
object AppColors {
    private val isLight: Boolean
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.background.luminance() > 0.5f

    val income: Color @Composable @ReadOnlyComposable get() = if (isLight) Color(0xFF15803D) else Color(0xFF6EE7A0)
    val warning: Color @Composable @ReadOnlyComposable get() = if (isLight) Color(0xFFD97706) else Color(0xFFFBBF24)
    val danger: Color @Composable @ReadOnlyComposable get() = if (isLight) Color(0xFFDC2626) else Color(0xFFF87171)
    val ok: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary
    val heroStart: Color @Composable @ReadOnlyComposable get() = if (isLight) Color(0xFF146C43) else Color(0xFF0F5C38)
    val heroEnd: Color @Composable @ReadOnlyComposable get() = if (isLight) Color(0xFF0B4A2D) else Color(0xFF0A3B24)
}

val MoneyStyle = TextStyle(fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
