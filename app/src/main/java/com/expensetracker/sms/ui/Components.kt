package com.expensetracker.sms.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.expensetracker.sms.Categories
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Icons available for categories (key -> icon). Keys are stored in the database. */
val CategoryIcons: List<Pair<String, ImageVector>> = listOf(
    "restaurant" to Icons.Rounded.Restaurant,
    "grocery" to Icons.Rounded.LocalGroceryStore,
    "shopping" to Icons.Rounded.ShoppingBag,
    "car" to Icons.Rounded.DirectionsCar,
    "fuel" to Icons.Rounded.LocalGasStation,
    "receipt" to Icons.Rounded.Receipt,
    "home" to Icons.Rounded.Home,
    "movie" to Icons.Rounded.Movie,
    "hospital" to Icons.Rounded.LocalHospital,
    "school" to Icons.Rounded.School,
    "bank" to Icons.Rounded.AccountBalance,
    "trending" to Icons.AutoMirrored.Rounded.TrendingUp,
    "swap" to Icons.Rounded.SwapHoriz,
    "atm" to Icons.Rounded.LocalAtm,
    "work" to Icons.Rounded.Work,
    "home_work" to Icons.Rounded.HomeWork,
    "pie" to Icons.Rounded.PieChart,
    "savings" to Icons.Rounded.Savings,
    "replay" to Icons.Rounded.Replay,
    "payments" to Icons.Rounded.Payments,
    "sync" to Icons.Rounded.SyncAlt,
    "cafe" to Icons.Rounded.LocalCafe,
    "bar" to Icons.Rounded.LocalBar,
    "flight" to Icons.Rounded.Flight,
    "train" to Icons.Rounded.Train,
    "bike" to Icons.Rounded.TwoWheeler,
    "pets" to Icons.Rounded.Pets,
    "fitness" to Icons.Rounded.FitnessCenter,
    "spa" to Icons.Rounded.Spa,
    "clothes" to Icons.Rounded.Checkroom,
    "child" to Icons.Rounded.ChildCare,
    "family" to Icons.Rounded.FamilyRestroom,
    "gift" to Icons.Rounded.CardGiftcard,
    "party" to Icons.Rounded.Celebration,
    "phone" to Icons.Rounded.PhoneAndroid,
    "wifi" to Icons.Rounded.Wifi,
    "laptop" to Icons.Rounded.Laptop,
    "games" to Icons.Rounded.SportsEsports,
    "repair" to Icons.Rounded.Build,
    "medicine" to Icons.Rounded.Medication,
    "charity" to Icons.Rounded.VolunteerActivism,
    "card" to Icons.Rounded.CreditCard,
    "money" to Icons.Rounded.AttachMoney,
    "star" to Icons.Rounded.Star,
    "shield" to Icons.Rounded.Shield,
    "fees" to Icons.Rounded.RequestQuote,
    "bolt" to Icons.Rounded.Bolt,
    "water" to Icons.Rounded.WaterDrop,
    "category" to Icons.Rounded.Category,
)
private val iconMap = CategoryIcons.toMap()

fun iconFor(key: String): ImageVector = iconMap[key] ?: Icons.Rounded.Category

fun categoryIcon(c: String): ImageVector = iconFor(Categories.iconKey(c))

/** Colours offered for custom categories. */
val CategoryColors: List<Long> = listOf(
    0xFFF97316, 0xFFEF4444, 0xFFEC4899, 0xFFD946EF, 0xFF8B5CF6, 0xFF6366F1,
    0xFF3B82F6, 0xFF0EA5E9, 0xFF06B6D4, 0xFF14B8A6, 0xFF10B981, 0xFF22C55E,
    0xFF84CC16, 0xFFEAB308, 0xFFB45309, 0xFF64748B,
)

@Composable
fun CategoryBadge(category: String, size: Dp = 40.dp) =
    IconBadge(Categories.iconKey(category), Categories.color(category), size)

@Composable
fun IconBadge(iconKey: String, color: Color, size: Dp = 40.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(color.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(iconFor(iconKey), contentDescription = null, tint = color, modifier = Modifier.size(size * 0.52f))
    }
}

/** White rounded card on the light-grey background. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Surface(
        modifier = modifier.fillMaxWidth().clip(shape).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(Modifier.padding(padding), content = content)
    }
}

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().height(40.dp).padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(action) }
        }
    }
}

/** Compact "‹ Nov 2026 ›" control that lives in the top bar. */
@Composable
fun MonthPill(month: YearMonth, onPrev: () -> Unit, onNext: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrev, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous month", Modifier.size(20.dp))
            }
            Text(
                month.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)),
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(72.dp),
            )
            IconButton(onClick = onNext, enabled = month < YearMonth.now(), modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next month", Modifier.size(20.dp))
            }
        }
    }
}

fun statusColor(ratio: Double, ok: Color, warn: Color, danger: Color) = when {
    ratio >= 1.0 -> danger
    ratio >= 0.8 -> warn
    else -> ok
}

@Composable
fun budgetColor(ratio: Double) = statusColor(ratio, AppColors.ok, AppColors.warning, AppColors.danger)

@Composable
fun ThinProgress(
    ratio: Double,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    track: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    // Bars fill in once when they appear and glide when the value changes.
    val target = ratio.coerceIn(0.0, 1.0).toFloat()
    val anim = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(target) {
        anim.animateTo(target, androidx.compose.animation.core.tween(durationMillis = 600, easing = androidx.compose.animation.core.FastOutSlowInEasing))
    }
    LinearProgressIndicator(
        progress = { anim.value },
        modifier = modifier.fillMaxWidth().height(height).clip(CircleShape),
        color = color,
        trackColor = track,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

/** Ring chart. Each slice is (colour, value). */
@Composable
fun DonutChart(
    slices: List<Pair<Color, Double>>,
    modifier: Modifier = Modifier,
    thickness: Dp = 16.dp,
    total: Double = slices.sumOf { it.second },
    center: @Composable BoxScope.() -> Unit = {},
) {
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = thickness.toPx()
            val d = size.minDimension - stroke
            val topLeft = Offset((size.width - d) / 2, (size.height - d) / 2)
            val arcSize = Size(d, d)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            if (total <= 0.0) return@Canvas
            val gap = if (slices.count { it.second > 0 } > 1) 3f else 0f
            var start = -90f
            slices.forEach { (color, v) ->
                val sweep = (v / total * 360.0).toFloat()
                if (sweep > gap) {
                    drawArc(
                        color, start + gap / 2, sweep - gap, false, topLeft, arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Butt),
                    )
                }
                start += sweep
            }
        }
        center()
    }
}

@Composable
fun DailyBars(values: DoubleArray, highlight: Int, modifier: Modifier = Modifier) {
    val max = values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val strong = MaterialTheme.colorScheme.primary
    val soft = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
    val empty = MaterialTheme.colorScheme.surfaceContainerHigh
    Canvas(modifier) {
        val n = values.size
        val w = size.width / n
        val barW = w * 0.56f
        values.forEachIndexed { i, v ->
            val h = if (v > 0) maxOf((v / max * size.height).toFloat(), 6f) else 6f
            drawRoundRect(
                color = when {
                    v <= 0 -> empty
                    i == highlight -> strong
                    else -> soft
                },
                topLeft = Offset(i * w + (w - barW) / 2, size.height - h),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2, barW / 2),
            )
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = modifier.clip(CircleShape).background(color.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
