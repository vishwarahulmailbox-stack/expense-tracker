package com.expensetracker.sms.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.SouthWest
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.runtime.remember
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    onOpenBills: () -> Unit = {},
    onPayBill: (com.expensetracker.sms.Bill) -> Unit = {},
    reviewCount: Int = 0,
    onReview: () -> Unit = {},
    name: String? = null,
    onTrends: () -> Unit = {},
    onCategories: () -> Unit = {},
    onSearch: () -> Unit = {},
    onRecap: (YearMonth) -> Unit = {},
    today: LocalDate = LocalDate.now(),
) {
    val lastMonth = YearMonth.from(today).minusMonths(1)
    val lastMonthSpent = if (s.month == YearMonth.from(today) && today.dayOfMonth <= 7) {
        s.all.filter { it.isDebit && !it.ignored && it.category != Categories.SELF && YearMonth.from(toLocalDate(it.timestamp)) == lastMonth }.sumOf { it.amount }
    } else 0.0
    LazyColumn(
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { HeroCard(s, onOpenBudgets) }
        item {
            ShortcutRow(
                listOf(
                    Triple("Trends", Icons.Rounded.Insights, onTrends),
                    Triple("Categories", Icons.Rounded.PieChart, onCategories),
                    Triple("Recap", Icons.Rounded.EmojiEvents, { onRecap(if (lastMonthSpent > 0) lastMonth else s.month) }),
                )
            )
        }
        if (lastMonthSpent > 0) item { RecapPromptCard(lastMonth, lastMonthSpent) { onRecap(lastMonth) } }
        if (reviewCount > 0) item { AiReviewCard(reviewCount, onReview) }
        item { UpcomingBillsCard(s.bills, onOpenBills, onPayBill) }
        if (s.byCategory.isNotEmpty() || s.incomeByCategory.isNotEmpty()) item { BreakdownCard(s) }
        if (s.spent > 0) item { DailySpendCard(s) }
        item {
            Column {
                SectionHeader("Recent", if (s.txns.isNotEmpty()) "See all" else null, onSeeAll)
                if (s.txns.isEmpty()) {
                    AppCard {
                        EmptyState(
                            Icons.Rounded.Inbox,
                            "No transactions yet",
                            "Bank and UPI SMS show up here automatically. Add cash spends with the + button.",
                        )
                    }
                } else {
                    AppCard(padding = PaddingValues(vertical = 4.dp)) {
                        val recent = s.txns.take(6)
                        recent.forEachIndexed { i, t ->
                            TxnRow(t) { onTxn(t) }
                            if (i < recent.lastIndex) RowDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RowDivider() = HorizontalDivider(Modifier.padding(start = 64.dp), color = MaterialTheme.colorScheme.outlineVariant)

@Composable
private fun HeroCard(s: UiState, onOpenBudgets: () -> Unit) {
    val limit = s.budgets[Categories.TOTAL]
    val monthName = s.month.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH)
    val elapsed = if (s.month == YearMonth.now()) LocalDate.now().dayOfMonth else s.month.lengthOfMonth()
    val daysLeft = if (s.month == YearMonth.now()) s.month.lengthOfMonth() - LocalDate.now().dayOfMonth + 1 else 0
    val white = Color.White
    val dim = white.copy(alpha = 0.75f)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(AppColors.heroStart, AppColors.heroEnd)))
            .clickable(onClick = onOpenBudgets)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("Spent in $monthName", color = dim, style = MaterialTheme.typography.labelMedium)
                Text(fmt(s.spent), color = white, style = MoneyStyle, fontSize = 32.sp, maxLines = 1)
            }
            if (limit != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text("Budget", color = dim, style = MaterialTheme.typography.labelMedium)
                    Text(fmt(limit), color = white, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        if (limit != null) {
            val ratio = s.spent / limit
            Spacer(Modifier.height(10.dp))
            ThinProgress(
                ratio,
                color = if (ratio >= 1.0) Color(0xFFFCA5A5) else if (ratio >= 0.8) Color(0xFFFCD34D) else white,
                track = white.copy(alpha = 0.2f),
                height = 6.dp,
            )
            Spacer(Modifier.height(6.dp))
            Row {
                Text(
                    if (s.spent > limit) "Over by ${fmt(s.spent - limit)}" else "${fmt(limit - s.spent)} left",
                    color = white, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f),
                )
                if (daysLeft > 0 && s.spent < limit) {
                    Text("${fmt(Math.floor((limit - s.spent) / daysLeft))}/day · $daysLeft days left", color = dim, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        // Stat strip
        Row(
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .clip(RoundedCornerShape(14.dp))
                .background(white.copy(alpha = 0.12f))
                .padding(vertical = 8.dp),
        ) {
            val hidden = LocalIncomeHidden.current
            val reveal = LocalRevealIncome.current
            HeroStat(
                if (hidden) "Income · locked" else "Income",
                incomeAmount(s.income),
                Modifier.weight(1f).then(if (hidden) Modifier.clickable { reveal {} } else Modifier),
            )
            VLine()
            HeroStat("Daily avg", fmt(Math.round(if (elapsed > 0) s.spent / elapsed else 0.0).toDouble()), Modifier.weight(1f))
            VLine()
            HeroStat(if (limit == null) "Set budget" else "Payments", if (limit == null) "Tap here" else "${s.active.count { it.isDebit }}", Modifier.weight(1f))
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall)
        Text(value, color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 1)
    }
}

@Composable
private fun VLine() = Box(Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.2f)))

@Composable
private fun BreakdownCard(s: UiState) {
    var income by rememberSaveable { mutableStateOf(false) }
    val data = if (income) s.incomeByCategory else s.byCategory
    val total = data.values.sum()
    val cats = data.entries.sortedByDescending { it.value }
    val shown = cats.take(5)
    val rest = cats.drop(5).sumOf { it.value }
    val otherColor = MaterialTheme.colorScheme.outline
    val slices = shown.map { Categories.color(it.key) to it.value } + if (rest > 0) listOf(otherColor to rest) else emptyList()
    val selfCount = s.selfTransfers.size

    AppCard(padding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Breakdown", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            SingleChoiceSegmentedButtonRow(Modifier.height(32.dp)) {
                SegmentedButton(
                    selected = !income, onClick = { income = false },
                    shape = SegmentedButtonDefaults.itemShape(0, 2), icon = {},
                ) { Text("Spent", style = MaterialTheme.typography.labelMedium) }
                SegmentedButton(
                    selected = income, onClick = { income = true },
                    shape = SegmentedButtonDefaults.itemShape(1, 2), icon = {},
                ) { Text("Income", style = MaterialTheme.typography.labelMedium) }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (income && LocalIncomeHidden.current) {
            val reveal = LocalRevealIncome.current
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { reveal {} }
                    .background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Income is hidden", style = MaterialTheme.typography.titleSmall)
                    Text("Tap to unlock with your phone's screen lock", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else if (cats.isEmpty()) {
            Text(
                if (income) "No income this month" else "No spending this month",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DonutChart(slices, Modifier.size(104.dp), thickness = 14.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(compact(total), style = MaterialTheme.typography.titleSmall)
                        Text(if (income) "earned" else "spent", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    shown.forEach { (cat, amt) -> LegendRow(Categories.color(cat), cat, amt, amt / total) }
                    if (rest > 0) LegendRow(otherColor, "Other", rest, rest / total)
                }
            }
        }
        if (selfCount > 0) {
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.SyncAlt, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text(
                    "$selfCount self transfer${if (selfCount == 1) "" else "s"} (${fmt(s.selfTransfers.filter { it.isDebit }.sumOf { it.amount })}) not counted",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** ₹1.2L / ₹45.3K style for tight spaces. */
fun compact(v: Double): String = when {
    v >= 1_00_00_000 -> "₹%.1fCr".format(Locale.ENGLISH, v / 1_00_00_000)
    v >= 1_00_000 -> "₹%.1fL".format(Locale.ENGLISH, v / 1_00_000)
    v >= 10_000 -> "₹%.1fK".format(Locale.ENGLISH, v / 1_000)
    else -> fmt(Math.round(v).toDouble())
}

@Composable
private fun LegendRow(color: Color, label: String, amount: Double, share: Double) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(
            "${Math.round(share * 100)}%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(34.dp),
        )
        Text(fmt(amount), style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
private fun DailySpendCard(s: UiState) {
    val days = s.month.lengthOfMonth()
    val daily = DoubleArray(days)
    s.active.filter { it.isDebit }.forEach { daily[toLocalDate(it.timestamp).dayOfMonth - 1] += it.amount }
    val today = LocalDate.now()
    val highlight = if (YearMonth.from(today) == s.month) today.dayOfMonth - 1 else -1
    val maxDay = daily.indices.maxByOrNull { daily[it] } ?: 0

    AppCard(padding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Daily spending", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(
                "Peak ${s.month.atDay(maxDay + 1).format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))} · ${fmt(daily[maxDay])}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(10.dp))
        DailyBars(daily, highlight, Modifier.fillMaxWidth().height(64.dp))
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(1, 8, 15, 22, days).forEach {
                Text("$it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private val timeFmt = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH)

@Composable
fun TxnRow(t: Txn, onClick: () -> Unit) {
    val time = Instant.ofEpochMilli(t.timestamp).atZone(ZoneId.systemDefault()).format(timeFmt)
    val isSelf = t.category == Categories.SELF
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .alpha(if (t.ignored) 0.6f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            CategoryBadge(t.category, 38.dp)
            DirectionDot(t, Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.merchant, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (t.aiConf != null && !t.aiReviewed) {
                    Icon(Icons.Rounded.AutoAwesome, "Category picked by AI", Modifier.size(12.dp), tint = AiPurple)
                    Spacer(Modifier.width(3.dp))
                }
                Text(
                    listOfNotNull(t.category, time, t.account?.let { "••$it" }, t.note.ifBlank { null }).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (t.isDebit) "−" + fmt(t.amount) else "+" + incomeAmount(t.amount),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = when {
                    t.ignored -> MaterialTheme.colorScheme.onSurfaceVariant
                    t.isDebit -> MaterialTheme.colorScheme.onSurface
                    else -> AppColors.income
                },
                textDecoration = if (t.ignored && !isSelf) TextDecoration.LineThrough else null,
            )
            if (t.ignored) {
                Text(if (isSelf) "Not counted" else "Excluded", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val keys = LocalRecurringKeys.current
                val tags = remember(t, keys) { com.expensetracker.sms.Insights.allTags(t, keys) }
                TagLine(tags.take(2), Modifier.widthIn(max = 110.dp))
            }
        }
    }
}

/** ↗ money out, ↙ money in, ⟲ refund, ⇄ own transfer — a small dot on the category badge. */
@Composable
fun DirectionDot(t: Txn, modifier: Modifier = Modifier) {
    val refund = !t.isDebit && (t.category == "Refund & cashback" || Regex("(?i)\\b(refund|reversal|reversed|cashback)\\b").containsMatchIn(t.body))
    val (icon, color) = when {
        t.category == Categories.SELF -> Icons.Rounded.SwapHoriz to MaterialTheme.colorScheme.onSurfaceVariant
        refund -> Icons.Rounded.Replay to Color(0xFFDB2777)
        t.isDebit -> Icons.Rounded.NorthEast to AppColors.danger
        else -> Icons.Rounded.SouthWest to AppColors.income
    }
    Box(
        modifier
            .size(17.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(2.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, if (t.isDebit) "Money out" else if (refund) "Refund" else "Money in", Modifier.size(10.dp), tint = color)
    }
}
