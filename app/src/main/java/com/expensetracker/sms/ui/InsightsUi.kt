@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.expensetracker.sms.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.TrendingDown
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.expensetracker.sms.Account
import com.expensetracker.sms.Categories
import com.expensetracker.sms.Insights
import com.expensetracker.sms.MerchantTotal
import com.expensetracker.sms.Txn
import com.expensetracker.sms.UiState
import com.expensetracker.sms.fmt
import com.expensetracker.sms.toLocalDate
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Merchant keys of recurring payments, so rows can show #Recurring. */
val LocalRecurringKeys = compositionLocalOf { emptySet<String>() }

private val shortMonth = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)
private val longMonth = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
fun monthName(m: YearMonth): String = m.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
private fun ym(t: Txn) = YearMonth.from(toLocalDate(t.timestamp))

// ------------------------------------------------------------------ small building blocks

/** Pill-shaped tab row: Transactions | Categories | Merchants. */
@Composable
fun SegmentedTabs(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(3.dp),
    ) {
        tabs.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(if (on) MaterialTheme.colorScheme.surfaceContainerLowest else Color.Transparent)
                    .selectable(selected = on, role = androidx.compose.ui.semantics.Role.Tab) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

fun accountLabel(a: Account) = a.nickname ?: "${a.bank} ••${a.last4}"

/** "All accounts ▾" filter. */
@Composable
fun AccountFilter(accounts: List<Account>, selected: String?, onSelect: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = selected?.let { k -> accounts.firstOrNull { it.key == k }?.let(::accountLabel) } ?: "All accounts"
    Box {
        Surface(
            shape = CircleShape,
            color = if (selected != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
            modifier = Modifier.minimumInteractiveComponentSize().clip(CircleShape).clickable { open = true },
        ) {
            Row(Modifier.padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AccountBalance, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                Icon(Icons.Rounded.ArrowDropDown, null, Modifier.size(20.dp))
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("All accounts") }, onClick = { open = false; onSelect(null) })
            accounts.forEach { a ->
                DropdownMenuItem(text = { Text(accountLabel(a)) }, onClick = { open = false; onSelect(a.key) })
            }
        }
    }
}

/**
 * Month bars with the value on top. Tap a bar to select it.
 * [line] (optional) is drawn as a line over the bars on the same scale (income).
 */
@Composable
fun MonthBars(
    months: List<YearMonth>,
    values: List<Double>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    line: List<Double>? = null,
    slot: Dp = 46.dp,
    barArea: Dp = 120.dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val lineColor = AppColors.income
    // a little headroom when a line is drawn, so it never sits on the value labels
    val max = maxOf(values.maxOrNull() ?: 0.0, line?.maxOrNull() ?: 0.0).coerceAtLeast(1.0) * (if (line != null) 1.15 else 1.0)
    val muted = color.copy(alpha = 0.35f)
    Box(modifier) {
        Row {
            months.forEachIndexed { i, m ->
                val on = i == selected
                Column(
                    Modifier.width(slot).clip(RoundedCornerShape(10.dp)).clickable { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        if (values[i] > 0) compact(values[i]) else "",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                        color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.height(16.dp),
                    )
                    Box(Modifier.height(barArea).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        val frac = (values[i] / max).toFloat().coerceIn(0f, 1f)
                        Box(
                            Modifier
                                .width(slot * 0.55f)
                                .fillMaxHeight(frac.coerceAtLeast(0.015f))
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(if (on) color else muted),
                        )
                    }
                    Text(
                        m.format(shortMonth),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                        color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                    )
                }
            }
        }
        if (line != null && line.any { it > 0 }) {
            Canvas(Modifier.offset(y = 16.dp).width(slot * months.size).height(barArea)) {
                val step = size.width / months.size
                val pts = line.mapIndexed { i, v -> Offset(step * i + step / 2, size.height * (1f - (v / max).toFloat())) }
                pts.zipWithNext().forEach { (a, b) -> drawLine(lineColor, a, b, strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round) }
                pts.forEach { p ->
                    drawCircle(Color.White, 4.dp.toPx(), p)
                    drawCircle(lineColor, 4.dp.toPx(), p, style = Stroke(2.dp.toPx()))
                }
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
    }
}

/** "↑ 12% vs Oct" */
@Composable
private fun ChangeText(now: Double, before: Double, prevLabel: String) {
    if (before <= 0) return
    val pct = Math.round((now - before) / before * 100)
    if (pct == 0L) {
        Text("About the same as $prevLabel", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val up = pct > 0
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(if (up) Icons.Rounded.TrendingUp else Icons.Rounded.TrendingDown, null, Modifier.size(16.dp), tint = if (up) AppColors.danger else AppColors.income)
        Spacer(Modifier.width(4.dp))
        Text(
            "${if (up) "+" else ""}$pct% vs $prevLabel",
            style = MaterialTheme.typography.labelMedium,
            color = if (up) AppColors.danger else AppColors.income,
        )
    }
}

@Composable
fun TxnGroupCard(items: List<Txn>, onTxn: (Txn) -> Unit) {
    AppCard(padding = PaddingValues(vertical = 2.dp)) {
        items.forEachIndexed { i, t ->
            TxnRow(t) { onTxn(t) }
            if (i < items.lastIndex) RowDivider()
        }
    }
}

// ------------------------------------------------------------------ Categories tab

@Composable
fun CategoriesTab(
    s: UiState,
    txns: List<Txn>,
    onOpen: (String) -> Unit,
    onSetBudget: (String) -> Unit,
    onCategorise: () -> Unit,
    othersCount: Int,
) {
    val active = txns.filter { !it.ignored && it.category != Categories.SELF }
    val spend = active.filter { it.isDebit }.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }
        .entries.sortedByDescending { it.value }
    val counts = active.groupingBy { it.category }.eachCount()
    val income = active.filter { !it.isDebit }.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }
        .entries.sortedByDescending { it.value }
    val total = spend.sumOf { it.value }.coerceAtLeast(1.0)
    val hidden = LocalIncomeHidden.current
    val reveal = LocalRevealIncome.current

    LazyColumn(
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (othersCount > 0) {
            item {
                AppCard(onClick = onCategorise, padding = PaddingValues(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge("category", AiPurple, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Categorise $othersCount payment${if (othersCount == 1) "" else "s"}", style = MaterialTheme.typography.titleSmall)
                            Text("Sitting in Others · sort them merchant by merchant", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Rounded.AutoAwesome, null, tint = AiPurple)
                    }
                }
            }
        }
        if (spend.isEmpty()) {
            item { AppCard { EmptyState(Icons.Rounded.SearchOff, "No spending yet", "Categories for this month show up here.") } }
        } else {
            item {
                AppCard(padding = PaddingValues(vertical = 4.dp)) {
                    spend.forEachIndexed { i, (cat, amt) ->
                        val budget = s.budgets[cat]
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpen(cat) }.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CategoryBadge(cat, 38.dp)
                            Spacer(Modifier.width(12.dp))
                            if (budget != null) {
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(cat, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(fmt(amt), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    ThinProgress(amt / budget, budgetColor(amt / budget), height = 5.dp)
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        if (amt > budget) "Over budget by ${fmt(amt - budget)}" else "${fmt(budget - amt)} left of ${fmt(budget)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (amt > budget) AppColors.danger else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            } else {
                                Column(Modifier.weight(1f)) {
                                    Text(cat, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "${counts[cat] ?: 0} payment${if ((counts[cat] ?: 0) == 1) "" else "s"} · ${Math.round(amt / total * 100)}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                // Amount + "Set budget" share one 48dp tap area, so the row stays compact.
                                Column(
                                    Modifier.minimumInteractiveComponentSize().clip(RoundedCornerShape(10.dp))
                                        .clickable(onClickLabel = "Set budget for $cat") { onSetBudget(cat) }
                                        .padding(start = 8.dp, top = 2.dp, bottom = 2.dp),
                                    horizontalAlignment = Alignment.End,
                                ) {
                                    Text(fmt(amt), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text("Set budget", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                        if (i < spend.lastIndex) RowDivider()
                    }
                }
            }
        }
        if (income.isNotEmpty()) {
            item {
                Column {
                    SectionHeader(if (hidden) "Income · locked" else "Income", if (hidden) "Show" else null) { reveal {} }
                    AppCard(padding = PaddingValues(vertical = 4.dp)) {
                        income.forEachIndexed { i, (cat, amt) ->
                            Row(
                                Modifier.fillMaxWidth().clickable { if (hidden) reveal { onOpen(cat) } else onOpen(cat) }.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CategoryBadge(cat, 38.dp)
                                Spacer(Modifier.width(12.dp))
                                Text(cat, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text("+" + incomeAmount(amt), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = AppColors.income)
                            }
                            if (i < income.lastIndex) RowDivider()
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Merchants tab

@Composable
fun MerchantsTab(txns: List<Txn>, onOpen: (MerchantTotal) -> Unit) {
    val list = remember(txns) { Insights.merchants(txns) }
    if (list.isEmpty()) {
        EmptyState(Icons.Rounded.Storefront, "No merchants yet", "Where your money goes, shop by shop, shows up here.")
        return
    }
    val top = list.first().total.coerceAtLeast(1.0)
    LazyColumn(
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp),
    ) {
        item {
            Text(
                "${list.size} merchants · tap one to see every payment",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            )
        }
        item {
            AppCard(padding = PaddingValues(vertical = 4.dp)) {
                list.forEachIndexed { i, m ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpen(m) }.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CategoryBadge(m.category, 38.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row {
                                Text(m.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text(fmt(m.total), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(Modifier.height(4.dp))
                            ThinProgress(m.total / top, Categories.color(m.category), height = 4.dp)
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "${m.count} payment${if (m.count == 1) "" else "s"} · ${m.category}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (i < list.lastIndex) RowDivider()
                }
            }
        }
    }
}

// ------------------------------------------------------------------ drill-downs

/** Category page: last 8 months as bars, then that month's payments. */
@Composable
fun CategoryDetailScreen(s: UiState, category: String, onTxn: (Txn) -> Unit, onSetBudget: (String) -> Unit) {
    val all = remember(s.all, s.accountFilter) { Insights.filterAccount(s.all, s.accountFilter) }
    val isIncome = all.any { it.category == category && !it.isDebit } && all.none { it.category == category && it.isDebit }
    val history = remember(all, category, s.month) {
        if (isIncome) {
            (7 downTo 0).map { s.month.minusMonths(it.toLong()) }.map { m ->
                m to all.filter { !it.ignored && !it.isDebit && it.category == category && ym(it) == m }.sumOf { it.amount }
            }
        } else Insights.categoryHistory(all.filter { it.isDebit }, category, s.month, 8)
    }
    DrillDown(
        history = history,
        txnsFor = { m -> all.filter { it.category == category && ym(it) == m }.sortedByDescending { it.timestamp } },
        onTxn = onTxn,
        budget = if (isIncome) null else s.budgets[category],
        onSetBudget = if (isIncome) null else ({ onSetBudget(category) }),
        header = { CategoryBadge(category, 44.dp) },
        title = category,
        income = isIncome,
    )
}

@Composable
fun MerchantDetailScreen(s: UiState, merchant: MerchantTotal, onTxn: (Txn) -> Unit) {
    val all = remember(s.all, s.accountFilter) { Insights.filterAccount(s.all, s.accountFilter) }
    val history = remember(all, merchant.key, s.month) { Insights.merchantHistory(all, merchant.key, s.month, 8) }
    DrillDown(
        history = history,
        txnsFor = { m -> all.filter { Insights.merchantKey(it.merchant) == merchant.key && ym(it) == m }.sortedByDescending { it.timestamp } },
        onTxn = onTxn,
        budget = null,
        onSetBudget = null,
        header = { CategoryBadge(merchant.category, 44.dp) },
        title = merchant.name,
        income = false,
    )
}

@Composable
private fun DrillDown(
    history: List<Pair<YearMonth, Double>>,
    txnsFor: (YearMonth) -> List<Txn>,
    onTxn: (Txn) -> Unit,
    budget: Double?,
    onSetBudget: (() -> Unit)?,
    header: @Composable () -> Unit,
    title: String,
    income: Boolean,
) {
    var sel by remember(history.size) { mutableIntStateOf(history.lastIndex) }
    val month = history[sel].first
    val total = history[sel].second
    val list = txnsFor(month)
    val nonZero = history.map { it.second }.filter { it > 0 }
    val avg = if (nonZero.isEmpty()) 0.0 else nonZero.average()
    val hidden = income && LocalIncomeHidden.current
    LazyColumn(
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    header()
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(monthName(month) + " " + month.year, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(if (income) incomeAmount(total) else fmt(total), style = MoneyStyle, fontSize = 22.sp)
                }
                Spacer(Modifier.height(12.dp))
                if (!hidden) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        MonthBars(
                            months = history.map { it.first },
                            values = history.map { it.second },
                            selected = sel,
                            onSelect = { sel = it },
                            slot = 38.dp,
                            barArea = 96.dp,
                            color = if (income) AppColors.income else MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Row {
                    StatCell("Monthly average", if (income) incomeAmount(avg) else fmt(Math.round(avg).toDouble()), Modifier.weight(1f))
                    StatCell("Payments", "${list.size}", Modifier.weight(1f))
                    if (onSetBudget != null) {
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable(onClick = onSetBudget)) {
                            Text("Budget", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(budget?.let { fmt(it) } ?: "Set budget", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                if (budget != null && !income) {
                    Spacer(Modifier.height(10.dp))
                    ThinProgress(total / budget, budgetColor(total / budget))
                }
            }
        }
        if (list.isEmpty()) {
            item { AppCard { EmptyState(Icons.Rounded.SearchOff, "Nothing in ${monthName(month)}", "Tap another month above.") } }
        } else {
            item { SectionHeader("${list.size} in ${monthName(month)}") }
            item { TxnGroupCard(list, onTxn) }
        }
    }
}

// ------------------------------------------------------------------ bulk categorise

@Composable
fun CategoriseSheet(
    groups: List<Pair<MerchantTotal, List<Txn>>>,
    suggestionsFor: (String) -> List<Pair<String, Double>>,
    onAssign: (List<Txn>, String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        CategoriseContent(groups, suggestionsFor, onAssign, onDone = onDismiss)
    }
}

@Composable
fun CategoriseContent(
    groups: List<Pair<MerchantTotal, List<Txn>>>,
    suggestionsFor: (String) -> List<Pair<String, Double>>,
    onAssign: (List<Txn>, String) -> Unit,
    onDone: () -> Unit = {},
) {
    val done = remember { mutableStateListOf<String>() }
    var expanded by remember { mutableStateOf<String?>(null) }
    val left = groups.filter { it.first.key !in done }
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Categorise", style = MaterialTheme.typography.titleLarge)
                Text(
                    if (left.isEmpty()) "All sorted" else "${left.sumOf { it.second.size }} payments in Others · ${left.size} merchants",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onDone) { Text("Done") }
        }
        Text(
            "Pick once and every past and future payment to that merchant follows. Suggestions come from the on-phone AI.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )
        LazyColumn(
            Modifier.fillMaxWidth().height(520.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(left, key = { it.first.key }) { (m, txns) ->
                val suggestions = remember(m.key) { suggestionsFor(m.name).filter { it.first != Categories.OTHERS }.take(3) }
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.animateItem()) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(m.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${m.count} payment${if (m.count == 1) "" else "s"} · ${fmt(m.total)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            suggestions.forEach { (cat, p) ->
                                SuggestChip(cat, "${Math.round(p * 100)}%", Categories.color(cat)) {
                                    onAssign(txns, cat); done += m.key
                                }
                            }
                            SuggestChip(if (expanded == m.key) "Less" else "More…", null, MaterialTheme.colorScheme.onSurfaceVariant) {
                                expanded = if (expanded == m.key) null else m.key
                            }
                        }
                        if (expanded == m.key) {
                            Spacer(Modifier.height(10.dp))
                            CategoryGrid(null, Categories.expense.filter { it != Categories.OTHERS }) { cat ->
                                onAssign(txns, cat); done += m.key; expanded = null
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestChip(label: String, extra: String?, color: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = color)
        if (extra != null) {
            Spacer(Modifier.width(6.dp))
            Text(extra, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.7f))
        }
    }
}

// ------------------------------------------------------------------ Trends

@Composable
fun TrendsScreen(s: UiState, onAccountFilter: (String?) -> Unit, onReview: (YearMonth) -> Unit, onCategory: (String) -> Unit) {
    val all = remember(s.all, s.accountFilter) { Insights.filterAccount(s.all, s.accountFilter) }
    val end = YearMonth.now()
    val totals = remember(all) { Insights.monthlyTotals(all, end, 12) }
    var sel by rememberSaveable { mutableIntStateOf(totals.lastIndex) }
    val hidden = LocalIncomeHidden.current
    val reveal = LocalRevealIncome.current
    val scroll = rememberScrollState()
    LaunchedEffect(scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
    val cur = totals[sel]
    val prev = totals.getOrNull(sel - 1)
    val withSpend = totals.filter { it.spent > 0 }
    val avg = if (withSpend.isEmpty()) 0.0 else withSpend.sumOf { it.spent } / withSpend.size
    val monthTxns = all.filter { ym(it) == cur.month && it.isDebit && !it.ignored && it.category != Categories.SELF }
    val topCats = monthTxns.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }.entries.sortedByDescending { it.value }.take(4)

    LazyColumn(
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AccountFilter(s.accounts, s.accountFilter, onAccountFilter)
                Spacer(Modifier.weight(1f))
                LegendDot(MaterialTheme.colorScheme.primary, "Spent")
                Spacer(Modifier.width(10.dp))
                LegendDot(AppColors.income, if (hidden) "Income · locked" else "Income", Modifier.clickable { if (hidden) reveal {} })
            }
        }
        item {
            AppCard(padding = PaddingValues(vertical = 14.dp, horizontal = 8.dp)) {
                Row(Modifier.fillMaxWidth().horizontalScroll(scroll)) {
                    MonthBars(
                        months = totals.map { it.month },
                        values = totals.map { it.spent },
                        selected = sel,
                        onSelect = { sel = it },
                        line = if (hidden) null else totals.map { it.income },
                        slot = 50.dp,
                        barArea = 150.dp,
                    )
                }
            }
        }
        item {
            AppCard {
                Text(cur.month.format(longMonth), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                prev?.let { ChangeText(cur.spent, it.spent, it.month.format(shortMonth)) }
                Spacer(Modifier.height(12.dp))
                Row {
                    StatCell("Spent", fmt(cur.spent), Modifier.weight(1f))
                    StatCell(if (hidden) "Income · locked" else "Income", incomeAmount(cur.income), Modifier.weight(1f).clickable { if (hidden) reveal {} }, AppColors.income)
                    StatCell(
                        "Saved",
                        if (hidden) MASK else fmt(cur.income - cur.spent),
                        Modifier.weight(1f),
                        if (cur.income - cur.spent >= 0) MaterialTheme.colorScheme.onSurface else AppColors.danger,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row {
                    StatCell("Monthly average", fmt(Math.round(avg).toDouble()), Modifier.weight(1f))
                    StatCell("Payments", "${monthTxns.size}", Modifier.weight(1f))
                    StatCell("Biggest", monthTxns.maxByOrNull { it.amount }?.let { compact(it.amount) } ?: "—", Modifier.weight(1f))
                }
                if (topCats.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    topCats.forEach { (cat, amt) ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onCategory(cat) }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CategoryBadge(cat, 28.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Row {
                                    Text(cat, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                    Text(fmt(amt), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                }
                                Spacer(Modifier.height(3.dp))
                                ThinProgress(amt / cur.spent.coerceAtLeast(1.0), Categories.color(cat), height = 4.dp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth().clip(CircleShape).clickable { onReview(cur.month) },
                ) {
                    Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.EmojiEvents, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.width(6.dp))
                        Text("Review ${monthName(cur.month)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ------------------------------------------------------------------ Monthly recap

@Composable
fun RecapScreen(s: UiState, month: YearMonth, onTxn: (Txn) -> Unit, onCategory: (String) -> Unit) {
    val all = remember(s.all, s.accountFilter) { Insights.filterAccount(s.all, s.accountFilter) }
    val r = remember(all, month) { Insights.recap(all, month, s.budgets[Categories.TOTAL]) }
    val hidden = LocalIncomeHidden.current
    val reveal = LocalRevealIncome.current
    val prevLabel = month.minusMonths(1).format(shortMonth)
    LazyColumn(
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(AppColors.heroStart, AppColors.heroEnd)))
                    .padding(18.dp),
            ) {
                Text("${monthName(month)} in review", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                Text("You spent", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text(fmt(r.spent), color = Color.White, style = MoneyStyle, fontSize = 34.sp)
                if (r.prevSpent > 0) {
                    val diff = r.spent - r.prevSpent
                    Text(
                        "${fmt(kotlin.math.abs(diff))} ${if (diff > 0) "more" else "less"} than $prevLabel",
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                r.budget?.let { b ->
                    Spacer(Modifier.height(10.dp))
                    ThinProgress(r.spent / b, if (r.spent > b) Color(0xFFFCA5A5) else Color.White, track = Color.White.copy(alpha = 0.2f))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (r.spent <= b) "Within budget — ${fmt(b - r.spent)} to spare" else "Over budget by ${fmt(r.spent - b)}",
                        color = Color.White, style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        item {
            AppCard {
                Row {
                    StatCell("Payments", "${r.count}", Modifier.weight(1f))
                    StatCell("Daily average", fmt(Math.round(r.dailyAvg).toDouble()), Modifier.weight(1f))
                    StatCell("No-spend days", "${r.noSpendDays}", Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row {
                    StatCell(if (hidden) "Income · locked" else "Income", incomeAmount(r.income), Modifier.weight(1f).clickable { if (hidden) reveal {} }, AppColors.income)
                    StatCell(
                        "Saved",
                        if (hidden) MASK else fmt(r.income - r.spent),
                        Modifier.weight(1f),
                    )
                    StatCell(
                        "Savings rate",
                        if (hidden) MASK else if (r.income > 0) "${Math.round((r.income - r.spent) / r.income * 100)}%" else "—",
                        Modifier.weight(1f),
                    )
                }
            }
        }
        if (r.topCategories.isNotEmpty()) {
            item {
                AppCard {
                    Text("Where it went", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    r.topCategories.forEachIndexed { i, (cat, amt) ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onCategory(cat) }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${i + 1}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(22.dp))
                            CategoryBadge(cat, 32.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Row {
                                    Text(cat, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    Text(fmt(amt), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                }
                                Spacer(Modifier.height(3.dp))
                                ThinProgress(amt / r.spent.coerceAtLeast(1.0), Categories.color(cat), height = 4.dp)
                            }
                        }
                    }
                }
            }
        }
        r.biggest?.let { b ->
            item {
                Column {
                    SectionHeader("Biggest payment")
                    TxnGroupCard(listOf(b), onTxn)
                }
            }
        }
        r.topMerchant?.takeIf { it.count >= 2 }?.let { m ->
            item {
                AppCard(padding = PaddingValues(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CategoryBadge(m.category, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Most visited", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(m.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${m.count}×", style = MaterialTheme.typography.titleSmall)
                            Text(fmt(m.total), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/** Home card shown in the first week of a month. */
@Composable
fun RecapPromptCard(month: YearMonth, spent: Double, onOpen: () -> Unit) {
    AppCard(onClick = onOpen, padding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge("star", MaterialTheme.colorScheme.primary, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Your ${monthName(month)} recap is ready", style = MaterialTheme.typography.titleSmall)
                Text("You spent ${fmt(spent)} · see where it went", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Rounded.EmojiEvents, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

// ------------------------------------------------------------------ Search

@Composable
fun SearchScreen(query: String, onQuery: (String) -> Unit, results: List<Txn>, onTxn: (Txn) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .height(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("Search all months: merchant, note, #tag or amount", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }, modifier = Modifier.size(36.dp)) { Icon(Icons.Rounded.Close, "Clear", Modifier.size(18.dp)) }
        }
        if (query.isBlank()) {
            FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("#UPI", "#Card", "#Recurring", "#Autopay", "Swiggy", "Amazon").forEach { q ->
                    SuggestChip(q, null, MaterialTheme.colorScheme.primary) { onQuery(q) }
                }
            }
            return
        }
        if (results.isEmpty()) {
            EmptyState(Icons.Rounded.SearchOff, "No matches", "Nothing in any month matches “$query”.")
            return
        }
        val spent = results.filter { it.isDebit && !it.ignored }.sumOf { it.amount }
        LazyColumn(
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
                    Text("${results.size} result${if (results.size == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    if (spent > 0) Text("−${fmt(spent)}", style = MaterialTheme.typography.labelLarge)
                }
            }
            results.groupBy { ym(it) }.forEach { (m, items) ->
                item(key = "h-$m") {
                    Text(m.format(longMonth), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp, top = 8.dp, bottom = 4.dp))
                }
                item(key = "g-$m") { TxnGroupCard(items, onTxn) }
            }
        }
    }
}

// ------------------------------------------------------------------ Tags

/** Small "#UPI #Card" line. */
@Composable
fun TagLine(tags: List<String>, modifier: Modifier = Modifier) {
    if (tags.isEmpty()) return
    Text(
        tags.joinToString(" ") { "#$it" },
        style = MaterialTheme.typography.labelSmall,
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.End,
        modifier = modifier,
    )
}

/** Auto tags (fixed) + own tags (removable) + a field to add more. */
@Composable
fun TagsEditor(auto: List<String>, tags: List<String>, onChange: (List<String>) -> Unit) {
    var input by remember { mutableStateOf("") }
    val add = {
        val t = input.trim().removePrefix("#").replace(Regex("\\s+"), "-").take(24)
        if (t.isNotEmpty() && tags.none { it.equals(t, true) } && auto.none { it.equals(t, true) }) onChange(tags + t)
        input = ""
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        auto.forEach { t ->
            Text(
                "#$t",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        tags.forEach { t ->
            Row(
                Modifier.minimumInteractiveComponentSize().clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).clickable(onClickLabel = "Remove tag $t") { onChange(tags - t) }
                    .padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("#$t", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.width(2.dp))
                Icon(Icons.Rounded.Close, "Remove", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Row(
            Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(96.dp)) {
                if (input.isEmpty()) Text("+ Add tag", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                BasicTextField(
                    value = input,
                    onValueChange = { v -> if (v.endsWith(" ") || v.endsWith(",")) { input = v.dropLast(1); add() } else input = v },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { add() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (input.isNotBlank()) {
                Text("Add", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { add() })
            }
        }
    }
}

// ------------------------------------------------------------------ Home bits

@Composable
fun Greeting(name: String?, month: YearMonth) {
    val hour = java.time.LocalTime.now().hour
    val hello = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
    Column(Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp)) {
        Text(if (name != null) "Hi $name" else hello, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            if (name != null) "$hello · here's your ${monthName(month)}" else "Here's your ${monthName(month)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Top-bar title on Home: "Hi Rahul" or "Good evening". */
fun greetingTitle(name: String?): String {
    val hour = java.time.LocalTime.now().hour
    val hello = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
    return if (name != null) "Hi $name" else hello
}

/** Slim row of shortcuts: icon + label in one line. */
@Composable
fun ShortcutRow(items: List<Triple<String, androidx.compose.ui.graphics.vector.ImageVector, () -> Unit>>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (label, icon, onClick) ->
            Row(
                Modifier.weight(1f).height(48.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest).clickable(onClick = onClick),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}

// ------------------------------------------------------------------ Bills calendar

@Composable
fun BillsCalendar(bills: List<com.expensetracker.sms.Bill>, month: YearMonth, today: LocalDate, onBill: (com.expensetracker.sms.Bill) -> Unit) {
    var selDay by remember(month) { mutableStateOf(if (YearMonth.from(today) == month) today.dayOfMonth else 1) }
    val byDay = bills.filter { YearMonth.from(it.dueDate) == month }.groupBy { it.dueDate.dayOfMonth }
    val firstDow = month.atDay(1).dayOfWeek.value % 7 // Sunday = 0
    val cells = (0 until firstDow).map { 0 } + (1..month.lengthOfMonth()).toList()
    AppCard(padding = PaddingValues(12.dp)) {
        Row {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(6.dp))
        cells.chunked(7).forEach { week ->
            Row {
                (0 until 7).forEach { i ->
                    val d = week.getOrNull(i) ?: 0
                    Box(Modifier.weight(1f).height(46.dp).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (d > 0) {
                            val dayBills = byDay[d].orEmpty()
                            val isToday = month.atDay(d) == today
                            val on = d == selDay
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        when {
                                            on -> MaterialTheme.colorScheme.primary
                                            isToday -> MaterialTheme.colorScheme.primaryContainer
                                            else -> Color.Transparent
                                        }
                                    )
                                    .clickable { selDay = d }
                                    .semantics {
                                        contentDescription = "$d ${monthName(month)}" + when (dayBills.size) {
                                            0 -> ""
                                            1 -> ", 1 bill"
                                            else -> ", ${dayBills.size} bills"
                                        }
                                    },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    "$d",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.height(6.dp)) {
                                    dayBills.take(3).forEach { b ->
                                        val c = billStatusText(b, today).second
                                        Box(Modifier.size(5.dp).clip(CircleShape).background(if (on) Color.White else c))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    // Colour is never the only signal: a legend names each dot colour, and the list below spells out the status.
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
        LegendDot(AppColors.income, "Paid")
        Spacer(Modifier.width(14.dp))
        LegendDot(AppColors.warning, "Due soon")
        Spacer(Modifier.width(14.dp))
        LegendDot(MaterialTheme.colorScheme.onSurfaceVariant, "Later")
        Spacer(Modifier.width(14.dp))
        LegendDot(AppColors.danger, "Overdue / today")
    }
    val dayBills = byDay[selDay].orEmpty()
    Spacer(Modifier.height(10.dp))
    Text(
        month.atDay(selDay).format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
    )
    if (dayBills.isEmpty()) {
        Text("No bills due this day.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
    } else {
        AppCard(padding = PaddingValues(vertical = 4.dp)) {
            dayBills.forEachIndexed { i, b ->
                val (status, color) = billStatusText(b, today)
                Row(
                    Modifier.fillMaxWidth().clickable { onBill(b) }.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BillBadge(b.kind, 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(b.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(status, style = MaterialTheme.typography.labelSmall, color = color)
                    }
                    Text(fmt(b.amount), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                if (i < dayBills.lastIndex) RowDivider()
            }
        }
    }
}

/** "Looks recurring — set a reminder?" */
@Composable
fun RecurringCard(r: com.expensetracker.sms.RecurringSuggestion, onYes: () -> Unit, onNo: () -> Unit) {
    AppCard(padding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryBadge(r.category, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(r.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${fmt(r.amount)} · paid ${r.months} months in a row" + if (r.autopay) " · autopay" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Next around ${r.nextDue.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Looks recurring. Set a reminder?", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onNo) { Text("No") }
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clip(CircleShape).clickable(onClick = onYes)) {
                Text("Yes", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
            }
        }
    }
}

// ------------------------------------------------------------------ Settings → Export

@Composable
fun ExportSheet(onDismiss: () -> Unit, onExport: (com.expensetracker.sms.ExportRange, Boolean, Boolean) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) { ExportContent(onExport) }
}

@Composable
fun ExportContent(onExport: (com.expensetracker.sms.ExportRange, Boolean, Boolean) -> Unit) {
    var range by remember { mutableStateOf(com.expensetracker.sms.ExportRange.LAST_12) }
    var sms by remember { mutableStateOf(true) }
    var skipped by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
        Text("Export data", style = MaterialTheme.typography.titleLarge)
        Text(
            "A CSV file for Excel, Google Sheets or your own analysis. It's made on this phone and goes only where you share it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text("Period", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            com.expensetracker.sms.ExportRange.entries.forEach { r ->
                val on = r == range
                Text(
                    r.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer)
                        .clickable { range = r }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        if (range != com.expensetracker.sms.ExportRange.THIS_MONTH) {
            Text(
                "Older SMS still in your inbox are read first, so nothing is missed. This can take a few seconds.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        ExportSwitch("Include original SMS text", "Adds an SMS column. Handy for training or checking the parser.", sms) { sms = it }
        ExportSwitch("Include skipped bank messages", "Reminders, offers, failed payments… with the reason they weren't counted. OTPs and personal messages are never saved, so they're never exported.", skipped) { skipped = it }
        Spacer(Modifier.height(16.dp))
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().clip(CircleShape).clickable { onExport(range, sms, skipped) },
        ) {
            Text(
                "Export ${range.label.lowercase()}",
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 14.dp),
            )
        }
        Text(
            "Includes income, so your screen lock is asked first.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

@Composable
private fun ExportSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = onChange)
    }
}
