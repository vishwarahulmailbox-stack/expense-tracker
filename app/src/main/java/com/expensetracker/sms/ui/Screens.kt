@file:OptIn(ExperimentalMaterial3Api::class)

package com.expensetracker.sms.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.expensetracker.sms.Categories
import com.expensetracker.sms.Txn
import com.expensetracker.sms.UiState
import com.expensetracker.sms.dayLabel
import com.expensetracker.sms.fmt
import com.expensetracker.sms.shortTime
import com.expensetracker.sms.toLocalDate
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

val Green = Color(0xFF2E7D32)
private val Orange = Color(0xFFEF6C00)
private val Red = Color(0xFFD32F2F)

fun progressColor(ratio: Double): Color = when {
    ratio >= 1.0 -> Red
    ratio >= 0.8 -> Orange
    else -> Green
}

@Composable
fun MonthSelector(month: YearMonth, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month") }
        Text(
            month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        IconButton(onClick = onNext, enabled = month < YearMonth.now()) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month")
        }
    }
}

// ---------------------------------------------------------------- Overview

@Composable
fun HomeScreen(s: UiState, onTxn: (Txn) -> Unit, onSeeAll: () -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SummaryCard(s) }
        item { DailyChartCard(s) }
        item { CategoryCard(s) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Recent", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (s.txns.isNotEmpty()) TextButton(onClick = onSeeAll) { Text("See all") }
            }
        }
        if (s.txns.isEmpty()) item { EmptyHint() }
        items(s.txns.take(8), key = { it.id }) { t -> TxnRow(t) { onTxn(t) } }
    }
}

@Composable
private fun SummaryCard(s: UiState) {
    val limit = s.budgets[Categories.TOTAL]
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("Spent this month", style = MaterialTheme.typography.labelLarge)
            Text(fmt(s.spent), fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Row {
                Text("Income  ", style = MaterialTheme.typography.bodyMedium)
                Text(fmt(s.income), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(16.dp))
                Text("${s.active.count { it.isDebit }} payments", style = MaterialTheme.typography.bodyMedium)
            }
            if (limit != null) {
                val ratio = s.spent / limit
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = { ratio.coerceIn(0.0, 1.0).toFloat() },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                    color = progressColor(ratio),
                    trackColor = MaterialTheme.colorScheme.surface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (s.spent > limit) "Over budget by ${fmt(s.spent - limit)}"
                    else "${fmt(limit - s.spent)} left of ${fmt(limit)} budget",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
            } else {
                Spacer(Modifier.height(8.dp))
                Text("Tip: set a monthly budget in the Budget tab", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun DailyChartCard(s: UiState) {
    val days = s.month.lengthOfMonth()
    val daily = DoubleArray(days)
    s.active.filter { it.isDebit }.forEach { daily[toLocalDate(it.timestamp).dayOfMonth - 1] += it.amount }
    val max = daily.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val today = LocalDate.now()
    val todayIndex = if (YearMonth.from(today) == s.month) today.dayOfMonth - 1 else -1
    val bar = MaterialTheme.colorScheme.primary
    val dim = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val elapsed = if (todayIndex >= 0) todayIndex + 1 else days

    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row {
                Text("Daily spending", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text("Avg ${fmt(Math.round(s.spent / elapsed).toDouble())}/day", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(12.dp))
            Canvas(Modifier.fillMaxWidth().height(110.dp)) {
                val w = size.width / days
                daily.forEachIndexed { i, v ->
                    val h = maxOf((v / max * size.height).toFloat(), if (v > 0) 3f else 1.5f)
                    drawRoundRect(
                        color = if (i == todayIndex || v == max) bar else dim,
                        topLeft = Offset(i * w + w * 0.18f, size.height - h),
                        size = Size(w * 0.64f, h),
                        cornerRadius = CornerRadius(4f, 4f),
                    )
                }
            }
            Row(Modifier.fillMaxWidth()) {
                Text("1", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                Text("Max ${fmt(if (max == 1.0 && s.spent == 0.0) 0.0 else max)}", style = MaterialTheme.typography.labelSmall)
                Text("$days", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CategoryCard(s: UiState) {
    val cats = s.byCategory.entries.sortedByDescending { it.value }
    if (cats.isEmpty()) return
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Where your money went", style = MaterialTheme.typography.titleSmall)
            cats.forEach { (cat, amt) ->
                val limit = s.budgets[cat]
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(Categories.emoji(cat), fontSize = 18.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(cat, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            fmt(amt) + (limit?.let { " / ${fmt(it)}" } ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "  ${(amt / s.spent * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    val ratio = if (limit != null) amt / limit else amt / s.spent
                    LinearProgressIndicator(
                        progress = { ratio.coerceIn(0.0, 1.0).toFloat() },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                        color = if (limit != null) progressColor(ratio) else Categories.color(cat),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyHint() {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🧾", fontSize = 40.sp)
        Text(
            "No transactions this month yet.\nPull new SMS with ⟳ at the top, or add one with +.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun TxnRow(t: Txn, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick).alpha(if (t.ignored) 0.45f else 1f),
        leadingContent = {
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(Categories.color(t.category).copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) { Text(Categories.emoji(t.category), fontSize = 20.sp) }
        },
        headlineContent = { Text(t.merchant, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                listOfNotNull(t.category, t.account?.let { "••$it" }, shortTime(t.timestamp), if (t.ignored) "ignored" else null)
                    .joinToString(" · "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Text(
                (if (t.isDebit) "−" else "+") + fmt(t.amount),
                color = if (t.isDebit) MaterialTheme.colorScheme.onSurface else Green,
                fontWeight = FontWeight.SemiBold,
            )
        },
    )
}

// ------------------------------------------------------------ Transactions

@Composable
fun TxnsScreen(s: UiState, onTxn: (Txn) -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableIntStateOf(0) } // 0 all, 1 spent, 2 received
    val list = s.txns.filter {
        (filter == 0 || (filter == 1) == it.isDebit) &&
            (q.isBlank() || it.merchant.contains(q, true) || it.category.contains(q, true) || it.note.contains(q, true))
    }
    val total = list.filter { !it.ignored }.sumOf { if (it.isDebit) it.amount else -it.amount }

    Column {
        OutlinedTextField(
            value = q,
            onValueChange = { q = it },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            placeholder = { Text("Search merchant, category, note") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf("All", "Spent", "Received").forEachIndexed { i, label ->
                FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(label) })
            }
            Spacer(Modifier.weight(1f))
            if (filter != 0) Text(fmt(kotlin.math.abs(total)), fontWeight = FontWeight.SemiBold)
        }
        if (list.isEmpty()) {
            EmptyHint()
            return
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
            list.groupBy { dayLabel(it.timestamp) }.forEach { (day, items) ->
                item(key = "h-$day") {
                    val daySpent = items.filter { it.isDebit && !it.ignored }.sumOf { it.amount }
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 2.dp)) {
                        Text(day, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        if (daySpent > 0) Text(fmt(daySpent), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(items, key = { it.id }) { t -> TxnRow(t) { onTxn(t) } }
            }
        }
    }
}

// ----------------------------------------------------------------- Budget

@Composable
fun BudgetScreen(s: UiState, onSet: (String, Double) -> Unit) {
    var editCat by remember { mutableStateOf<String?>(null) }
    val spentBy = s.byCategory
    val withLimit = Categories.expense.filter { s.budgets.containsKey(it) }
    val withoutLimit = Categories.expense.filter { !s.budgets.containsKey(it) }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            BudgetCard("💰", "Total monthly budget", s.spent, s.budgets[Categories.TOTAL], big = true) {
                editCat = Categories.TOTAL
            }
        }
        item {
            Text("Category limits", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            Text(
                "Tap a category to set a limit. You'll get a notification at 80% and 100%.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(withLimit + withoutLimit, key = { it }) { c ->
            BudgetCard(Categories.emoji(c), c, spentBy[c] ?: 0.0, s.budgets[c]) { editCat = c }
        }
    }

    editCat?.let { c ->
        AmountDialog(
            title = if (c == Categories.TOTAL) "Total monthly budget" else "${Categories.emoji(c)} $c — monthly limit",
            initial = s.budgets[c],
            onDismiss = { editCat = null },
            onSave = { onSet(c, it); editCat = null },
        )
    }
}

@Composable
private fun BudgetCard(emoji: String, title: String, spent: Double, limit: Double?, big: Boolean = false, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = if (big) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.cardColors(),
    ) {
        Column(Modifier.fillMaxWidth().padding(if (big) 20.dp else 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, fontSize = if (big) 26.sp else 20.sp)
                Spacer(Modifier.width(10.dp))
                Text(
                    title,
                    style = if (big) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                if (limit == null) {
                    Text(
                        if (spent > 0) "${fmt(spent)} · Set limit" else "Set limit",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text("${fmt(spent)} / ${fmt(limit)}", fontWeight = FontWeight.SemiBold)
                }
            }
            if (limit != null) {
                val ratio = spent / limit
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { ratio.coerceIn(0.0, 1.0).toFloat() },
                    modifier = Modifier.fillMaxWidth().height(if (big) 10.dp else 6.dp).clip(CircleShape),
                    color = progressColor(ratio),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (spent > limit) "Over by ${fmt(spent - limit)}" else "${fmt(limit - spent)} left · ${(ratio * 100).toInt()}% used",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (ratio >= 1.0) progressColor(ratio) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
