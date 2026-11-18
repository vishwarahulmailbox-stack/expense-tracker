package com.expensetracker.sms.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.expensetracker.sms.Categories
import com.expensetracker.sms.Txn
import com.expensetracker.sms.UiState
import com.expensetracker.sms.fmt
import com.expensetracker.sms.toLocalDate
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(
    s: UiState,
    onTxn: (Txn) -> Unit,
    onSeeAll: () -> Unit,
    onOpenBudgets: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { HeroCard(s, onOpenBudgets) }
        item { QuickStats(s) }
        if (s.byCategory.isNotEmpty()) item { CategoryBreakdown(s) }
        if (s.spent > 0) item { DailySpendCard(s) }
        item { SectionHeader("Recent transactions", if (s.txns.isNotEmpty()) "See all" else null, onSeeAll) }
        item {
            if (s.txns.isEmpty()) {
                AppCard {
                    EmptyState(
                        Icons.Rounded.Inbox,
                        "No transactions yet",
                        "Bank and UPI SMS will show up here automatically. You can also add a cash expense with the + button.",
                    )
                }
            } else {
                AppCard(padding = PaddingValues(vertical = 6.dp)) {
                    val recent = s.txns.take(6)
                    recent.forEachIndexed { i, t ->
                        TxnRow(t) { onTxn(t) }
                        if (i < recent.lastIndex) {
                            HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroCard(s: UiState, onOpenBudgets: () -> Unit) {
    val limit = s.budgets[Categories.TOTAL]
    val monthName = s.month.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH)
    val onHero = Color.White
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(AppColors.heroStart, AppColors.heroEnd)))
            .clickable(onClick = onOpenBudgets)
            .padding(20.dp),
    ) {
        Column {
            Text("Spent in $monthName", color = onHero.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Text(fmt(s.spent), color = onHero, style = MoneyStyle, fontSize = 38.sp)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(22.dp).clip(CircleShape).background(onHero.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.ArrowDownward, null, tint = onHero, modifier = Modifier.size(14.dp)) }
                Spacer(Modifier.width(6.dp))
                Text("Income ", color = onHero.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium)
                Text(fmt(s.income), color = onHero, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(16.dp))
            if (limit != null) {
                val ratio = s.spent / limit
                val daysLeft = if (s.month == YearMonth.now()) s.month.lengthOfMonth() - LocalDate.now().dayOfMonth + 1 else 0
                ThinProgress(
                    ratio,
                    color = if (ratio >= 1.0) Color(0xFFFCA5A5) else if (ratio >= 0.8) Color(0xFFFCD34D) else onHero,
                    track = onHero.copy(alpha = 0.2f),
                    height = 8.dp,
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    Text(
                        if (s.spent > limit) "Over budget by ${fmt(s.spent - limit)}" else "${fmt(limit - s.spent)} left of ${fmt(limit)}",
                        color = onHero,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    if (daysLeft > 0) {
                        Text(
                            "$daysLeft day${if (daysLeft == 1) "" else "s"} left",
                            color = onHero.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            } else {
                Row(
                    Modifier.clip(CircleShape).background(onHero.copy(alpha = 0.16f)).padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Savings, null, tint = onHero, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Set a monthly budget", color = onHero, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun QuickStats(s: UiState) {
    val elapsed = if (s.month == YearMonth.now()) LocalDate.now().dayOfMonth else s.month.lengthOfMonth()
    val avg = if (elapsed > 0) s.spent / elapsed else 0.0
    val top = s.byCategory.maxByOrNull { it.value }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatTile(
            Modifier.weight(1f),
            icon = Icons.Rounded.CalendarToday,
            tint = MaterialTheme.colorScheme.primary,
            label = "Daily average",
            value = fmt(Math.round(avg).toDouble()),
        )
        StatTile(
            Modifier.weight(1f),
            icon = top?.let { categoryIcon(it.key) } ?: Icons.Rounded.Savings,
            tint = top?.let { Categories.color(it.key) } ?: MaterialTheme.colorScheme.primary,
            label = top?.let { "Top: ${it.key}" } ?: "Top category",
            value = top?.let { fmt(it.value) } ?: "—",
        )
    }
}

@Composable
private fun StatTile(modifier: Modifier, icon: ImageVector, tint: Color, label: String, value: String) {
    AppCard(modifier) {
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.height(10.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}

@Composable
private fun CategoryBreakdown(s: UiState) {
    val cats = s.byCategory.entries.sortedByDescending { it.value }
    val shown = cats.take(5)
    val rest = cats.drop(5).sumOf { it.value }
    val slices = shown.map { Categories.color(it.key) to it.value } +
        if (rest > 0) listOf(MaterialTheme.colorScheme.outline to rest) else emptyList()

    AppCard {
        Text("Spending by category", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            DonutChart(slices, Modifier.size(132.dp), thickness = 18.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${cats.size}", style = MaterialTheme.typography.headlineSmall)
                    Text("categories", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                shown.forEach { (cat, amt) -> LegendRow(Categories.color(cat), cat, amt, amt / s.spent) }
                if (rest > 0) LegendRow(MaterialTheme.colorScheme.outline, "Other", rest, rest / s.spent)
            }
        }
    }
}

@Composable
private fun LegendRow(color: Color, label: String, amount: Double, share: Double) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(fmt(amount), style = MaterialTheme.typography.labelLarge)
            Text("${Math.round(share * 100)}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DailySpendCard(s: UiState) {
    val days = s.month.lengthOfMonth()
    val daily = DoubleArray(days)
    s.active.filter { it.isDebit }.forEach { daily[toLocalDate(it.timestamp).dayOfMonth - 1] += it.amount }
    val today = LocalDate.now()
    val highlight = if (YearMonth.from(today) == s.month) today.dayOfMonth - 1 else daily.indices.maxByOrNull { daily[it] } ?: -1
    val maxDay = daily.indices.maxByOrNull { daily[it] } ?: 0

    AppCard {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("Daily spending", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Highest on ${s.month.atDay(maxDay + 1).format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))} · ${fmt(daily[maxDay])}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        DailyBars(daily, highlight, Modifier.fillMaxWidth().height(96.dp))
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(1, 8, 15, 22, days).forEach {
                Text("$it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private val timeFmt = DateTimeFormatter.ofPattern("d MMM · h:mm a", Locale.ENGLISH)

@Composable
fun TxnRow(t: Txn, onClick: () -> Unit) {
    val time = Instant.ofEpochMilli(t.timestamp).atZone(ZoneId.systemDefault()).format(timeFmt)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .alpha(if (t.ignored) 0.55f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryBadge(t.category, 42.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                t.merchant,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(t.category, time, t.note.ifBlank { null }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                (if (t.isDebit) "−" else "+") + fmt(t.amount),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (t.isDebit) MaterialTheme.colorScheme.onSurface else AppColors.income,
                textDecoration = if (t.ignored) TextDecoration.LineThrough else null,
            )
            if (t.ignored) {
                Text("Excluded", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (t.account != null) {
                Text("••${t.account}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
