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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalAtm
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalGroceryStore
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.SwapHoriz
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

fun categoryIcon(c: String): ImageVector = when (c) {
    "Food" -> Icons.Rounded.Restaurant
    "Groceries" -> Icons.Rounded.LocalGroceryStore
    "Shopping" -> Icons.Rounded.ShoppingBag
    "Travel" -> Icons.Rounded.DirectionsCar
    "Fuel" -> Icons.Rounded.LocalGasStation
    "Bills" -> Icons.Rounded.Receipt
    "Rent" -> Icons.Rounded.Home
    "Entertainment" -> Icons.Rounded.Movie
    "Health" -> Icons.Rounded.LocalHospital
    "Education" -> Icons.Rounded.School
    "EMI/Loan" -> Icons.Rounded.AccountBalance
    "Investment" -> Icons.AutoMirrored.Rounded.TrendingUp
    "Transfers" -> Icons.Rounded.SwapHoriz
    "ATM/Cash" -> Icons.Rounded.LocalAtm
    Categories.INCOME -> Icons.Rounded.Payments
    else -> Icons.Rounded.Category
}

@Composable
fun CategoryBadge(category: String, size: Dp = 44.dp) {
    val c = Categories.color(category)
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(c.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(categoryIcon(category), contentDescription = null, tint = c, modifier = Modifier.size(size * 0.52f))
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
        Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) { Text(action) }
        } else {
            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
fun MonthSwitcher(month: YearMonth, onPrev: () -> Unit, onNext: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrev) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous month")
                }
                Text(
                    month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)),
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(132.dp),
                )
                IconButton(onClick = onNext, enabled = month < YearMonth.now()) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next month")
                }
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
    LinearProgressIndicator(
        progress = { ratio.coerceIn(0.0, 1.0).toFloat() },
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
