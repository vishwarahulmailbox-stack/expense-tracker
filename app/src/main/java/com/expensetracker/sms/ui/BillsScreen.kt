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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.expensetracker.sms.Bill
import com.expensetracker.sms.BillKind
import com.expensetracker.sms.BillStatus
import com.expensetracker.sms.Recurrence
import com.expensetracker.sms.fmt
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dueFmt = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

fun kindColor(k: BillKind): Color = Color(
    when (k) {
        BillKind.CREDIT_CARD -> 0xFF6366F1
        BillKind.ELECTRICITY -> 0xFFEAB308
        BillKind.LIC, BillKind.INSURANCE -> 0xFF0D9488
        BillKind.PHONE -> 0xFFEF4444
        BillKind.INTERNET -> 0xFF0EA5E9
        BillKind.GAS -> 0xFFF97316
        BillKind.WATER -> 0xFF3B82F6
        BillKind.LOAN_EMI -> 0xFF64748B
        BillKind.RENT -> 0xFF8B5CF6
        BillKind.SUBSCRIPTION -> 0xFFEC4899
        BillKind.OTHER -> 0xFF78716C
    }
)

@Composable
fun BillBadge(kind: BillKind, size: androidx.compose.ui.unit.Dp = 40.dp) = IconBadge(kind.icon, kindColor(kind), size)

/** "Due in 3 days" / "Due today" / "Overdue by 2 days" / "Paid 20 Nov" with a colour. */
@Composable
fun billStatusText(b: Bill, today: LocalDate = LocalDate.now()): Pair<String, Color> {
    val d = b.daysLeft(today)
    return when (b.status(today)) {
        BillStatus.PAID -> "Paid${b.paidTs?.let { " " + Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(dueFmt) } ?: ""}" to AppColors.income
        BillStatus.OVERDUE -> "Overdue ${-d}d" to AppColors.danger
        BillStatus.PENDING -> when {
            d == 0L -> "Due today" to AppColors.danger
            d == 1L -> "Due tomorrow" to AppColors.warning
            d <= 3 -> "Due in $d days" to AppColors.warning
            else -> "Due in $d days" to MaterialTheme.colorScheme.onSurfaceVariant
        }
    }
}

@Composable
fun BillsScreen(
    bills: List<Bill>,
    month: YearMonth,
    onPay: (Bill) -> Unit,
    onEdit: (Bill) -> Unit,
    onMarkPaid: (Bill, Boolean) -> Unit,
    recurring: List<com.expensetracker.sms.RecurringSuggestion> = emptyList(),
    onAcceptRecurring: (com.expensetracker.sms.RecurringSuggestion) -> Unit = {},
    onDismissRecurring: (com.expensetracker.sms.RecurringSuggestion) -> Unit = {},
    today: LocalDate = LocalDate.now(),
) {
    var filter by rememberSaveable { mutableStateOf("upcoming") }
    var calendar by rememberSaveable { mutableStateOf(false) }
    val monthBills = bills.filter { YearMonth.from(it.dueDate) == month }
    val upcoming = bills.filter { it.status(today) == BillStatus.PENDING }.sortedBy { it.dueDate }
    val overdue = bills.filter { it.status(today) == BillStatus.OVERDUE }.sortedBy { it.dueDate }
    val paid = monthBills.filter { it.paid }.sortedByDescending { it.paidTs ?: 0 }
    val shown = when (filter) {
        "overdue" -> overdue
        "paid" -> paid
        "all" -> monthBills.sortedBy { it.dueDate }
        else -> upcoming
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { BillsSummary(monthBills, month, today) }
        if (recurring.isNotEmpty()) {
            item { SectionHeader("Spotted in your payments") }
            items(recurring.take(3), key = { "r-" + it.key }) { r ->
                RecurringCard(r, onYes = { onAcceptRecurring(r) }, onNo = { onDismissRecurring(r) })
            }
        }
        item {
            SegmentedTabs(listOf("List", "Calendar"), if (calendar) 1 else 0, { calendar = it == 1 })
        }
        if (calendar) {
            item { Column { BillsCalendar(bills, month, today, onEdit) } }
            return@LazyColumn
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val chips = listOf(
                    "upcoming" to "Upcoming · ${upcoming.size}",
                    "overdue" to "Overdue · ${overdue.size}",
                    "paid" to "Paid · ${paid.size}",
                    "all" to "This month · ${monthBills.size}",
                )
                items(chips, key = { it.first }) { (k, label) ->
                    FilterChip(
                        selected = filter == k,
                        onClick = { filter = k },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                            selectedContainerColor = if (k == "overdue") AppColors.danger else MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White,
                        ),
                    )
                }
            }
        }
        if (shown.isEmpty()) {
            item {
                AppCard {
                    EmptyState(
                        Icons.Rounded.ReceiptLong,
                        when (filter) {
                            "overdue" -> "Nothing overdue"
                            "paid" -> "No bills paid this month"
                            else -> "No upcoming bills"
                        },
                        if (bills.isEmpty()) "Credit card statements, LIC premiums, electricity and other bills from your SMS appear here automatically. Add any other bill with the + button."
                        else "You're all caught up.",
                    )
                }
            }
        }
        items(shown, key = { it.id }) { b -> BillCard(b, today, { onPay(b) }, { onEdit(b) }, { onMarkPaid(b, !b.paid) }) }
    }
}

@Composable
private fun BillsSummary(monthBills: List<Bill>, month: YearMonth, today: LocalDate) {
    val total = monthBills.sumOf { it.amount }
    val paid = monthBills.filter { it.paid }.sumOf { it.amount }
    val overdue = monthBills.filter { it.status(today) == BillStatus.OVERDUE }
    val pending = monthBills.filter { it.status(today) == BillStatus.PENDING }
    val monthName = month.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH)
    AppCard(padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("Bills in $monthName", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(fmt(total), style = MaterialTheme.typography.headlineSmall)
            }
            Text("${monthBills.count { it.paid }}/${monthBills.size} paid", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        ThinProgress(if (total > 0) paid / total else 0.0, AppColors.income, height = 8.dp)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            SummaryStat("Paid", fmt(paid), AppColors.income, Modifier.weight(1f))
            SummaryStat("Pending · ${pending.size}", fmt(pending.sumOf { it.amount }), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
            SummaryStat("Overdue · ${overdue.size}", fmt(overdue.sumOf { it.amount }), if (overdue.isEmpty()) MaterialTheme.colorScheme.onSurface else AppColors.danger, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleSmall, color = color, maxLines = 1)
    }
}

@Composable
fun BillCard(b: Bill, today: LocalDate, onPay: () -> Unit, onEdit: () -> Unit, onTogglePaid: () -> Unit) {
    val (statusText, statusColor) = billStatusText(b, today)
    AppCard(onClick = onEdit, padding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BillBadge(b.kind, 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(b.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        "Due ${b.dueDate.format(dueFmt)}",
                        b.recurrence.takeIf { it != Recurrence.NONE }?.label,
                        b.last4?.let { "••$it" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(fmt(b.amount), style = MaterialTheme.typography.titleMedium)
                Text(statusText, style = MaterialTheme.typography.labelMedium, color = statusColor, fontWeight = FontWeight.SemiBold)
            }
        }
        if (b.minDue != null && !b.paid) {
            Text(
                "Minimum due ${fmt(b.minDue)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 54.dp, top = 2.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().padding(start = 54.dp), verticalAlignment = Alignment.CenterVertically) {
            when {
                b.paid -> {
                    Icon(Icons.Rounded.CheckCircle, null, Modifier.size(16.dp), tint = AppColors.income)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (b.paidTxnId != null) "Matched with your bank SMS" else "Marked as paid",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onTogglePaid) { Text("Undo") }
                }
                b.autopay -> {
                    Pill("Autopay", MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                    Text("Will be auto-debited", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    TextButton(onClick = onTogglePaid) { Text("Mark paid") }
                }
                else -> {
                    Button(onClick = onPay, contentPadding = PaddingValues(horizontal = 16.dp), modifier = Modifier.height(36.dp)) {
                        Icon(Icons.Rounded.Bolt, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Pay now")
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onTogglePaid) { Text("Mark paid") }
                }
            }
        }
    }
}

/** Compact list for the Home screen. */
@Composable
fun UpcomingBillsCard(bills: List<Bill>, onSeeAll: () -> Unit, onPay: (Bill) -> Unit) {
    val today = LocalDate.now()
    val soon = bills.filter { !it.paid && it.daysLeft(today) <= 10 }.sortedBy { it.dueDate }.take(3)
    if (soon.isEmpty()) return
    Column {
        SectionHeader("Upcoming bills", "All bills", onSeeAll)
        AppCard(padding = PaddingValues(vertical = 2.dp)) {
            soon.forEachIndexed { i, b ->
                val (txt, col) = billStatusText(b, today)
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onSeeAll).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BillBadge(b.kind, 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(b.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${fmt(b.amount)} · $txt", style = MaterialTheme.typography.labelSmall, color = col)
                    }
                    if (b.autopay) {
                        Pill("Autopay", MaterialTheme.colorScheme.primary)
                    } else {
                        Box(
                            Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary)
                                .clickable { onPay(b) }.padding(horizontal = 12.dp, vertical = 6.dp),
                        ) { Text("Pay", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge) }
                    }
                }
                if (i < soon.lastIndex) RowDivider()
            }
        }
    }
}
