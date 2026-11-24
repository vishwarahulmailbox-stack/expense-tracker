package com.expensetracker.sms.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.runtime.remember
import com.expensetracker.sms.Categories
import com.expensetracker.sms.Insights
import com.expensetracker.sms.MerchantTotal
import com.expensetracker.sms.Txn
import com.expensetracker.sms.UiState
import com.expensetracker.sms.dayLabel
import com.expensetracker.sms.fmt

@Composable
fun TransactionsScreen(
    s: UiState,
    onTxn: (Txn) -> Unit,
    tab: Int = 0,
    onTab: (Int) -> Unit = {},
    onCategory: (String) -> Unit = {},
    onMerchant: (MerchantTotal) -> Unit = {},
    onCategorise: () -> Unit = {},
    onSetBudget: (String) -> Unit = {},
    onAccountFilter: (String?) -> Unit = {},
    onExport: (ExportKind) -> Unit = {},
) {
    val txns = remember(s.txns, s.accountFilter) { Insights.filterAccount(s.txns, s.accountFilter) }
    val othersCount = remember(s.all) { s.all.count { it.isDebit && !it.ignored && it.category == Categories.OTHERS } }
    Column {
        SegmentedTabs(listOf("Transactions", "Categories", "Merchants"), tab, onTab, Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            AccountFilter(s.accounts, s.accountFilter, onAccountFilter)
            Spacer(Modifier.weight(1f))
            ExportMenu(onExport)
        }
        androidx.compose.animation.Crossfade(targetState = tab, animationSpec = androidx.compose.animation.core.tween(220), label = "tabs") { t ->
            when (t) {
                1 -> CategoriesTab(s, txns, onCategory, onSetBudget, onCategorise, othersCount)
                2 -> MerchantsTab(txns, onMerchant)
                else -> TxnListTab(s.copy(txns = txns), onTxn)
            }
        }
    }
}

enum class ExportKind { CSV, PDF, PDF_SPENDS }

@Composable
private fun ExportMenu(onExport: (ExportKind) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Icon(Icons.Rounded.IosShare, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Export")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("PDF report") },
                leadingIcon = { Icon(Icons.Rounded.PictureAsPdf, null) },
                onClick = { open = false; onExport(ExportKind.PDF) },
            )
            DropdownMenuItem(
                text = { Text("PDF report · spends only") },
                leadingIcon = { Icon(Icons.Rounded.PictureAsPdf, null) },
                onClick = { open = false; onExport(ExportKind.PDF_SPENDS) },
            )
            DropdownMenuItem(
                text = { Text("CSV (spreadsheet)") },
                leadingIcon = { Icon(Icons.Rounded.TableChart, null) },
                onClick = { open = false; onExport(ExportKind.CSV) },
            )
        }
    }
}

@Composable
private fun TxnListTab(s: UiState, onTxn: (Txn) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableIntStateOf(0) } // 0 all, 1 expenses, 2 income
    var category by rememberSaveable { mutableStateOf<String?>(null) }

    val usedCategories = s.txns.map { it.category }.distinct().sorted()
    val list = s.txns.filter {
        (type == 0 || (type == 1) == it.isDebit) &&
            (category == null || it.category == category) &&
            (query.isBlank() || it.merchant.contains(query, true) || it.category.contains(query, true) ||
                it.note.contains(query, true) || it.amount.toString().startsWith(query) ||
                it.tags.any { tag -> tag.contains(query.removePrefix("#"), true) })
    }
    val spent = list.filter { it.isDebit && !it.ignored }.sumOf { it.amount }
    val received = list.filter { !it.isDebit && !it.ignored }.sumOf { it.amount }

    Column {
        // Compact search field (44dp instead of the 56dp Material default)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .height(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text("Search merchant, category, note", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = { query = "" }, modifier = Modifier.size(36.dp)) { Icon(Icons.Rounded.Close, "Clear", Modifier.size(18.dp)) }
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val types = listOf("All", "Expenses", "Income")
            items(types.size) { i ->
                FilterChip(
                    selected = type == i,
                    onClick = { type = i },
                    label = { Text(types[i]) },
                    colors = chipColors(),
                )
            }
            items(usedCategories, key = { it }) { c ->
                FilterChip(
                    selected = category == c,
                    onClick = { category = if (category == c) null else c },
                    label = { Text(c) },
                    leadingIcon = { Icon(categoryIcon(c), null, Modifier.size(16.dp)) },
                    colors = chipColors(),
                )
            }
        }

        if (list.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)) {
                Text(
                    "${list.size} transaction${if (list.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (received > 0) Text("+${incomeAmount(received)}  ", style = MaterialTheme.typography.labelLarge, color = AppColors.income)
                if (spent > 0) Text("−${fmt(spent)}", style = MaterialTheme.typography.labelLarge)
            }
        }

        if (list.isEmpty()) {
            EmptyState(
                Icons.Rounded.SearchOff,
                if (s.txns.isEmpty()) "Nothing here yet" else "No matches",
                if (s.txns.isEmpty()) "Transactions for this month will appear here." else "Try a different search or filter.",
            )
            return
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            list.groupBy { dayLabel(it.timestamp) }.forEach { (day, items) ->
                item(key = "h-$day") {
                    val daySpent = items.filter { it.isDebit && !it.ignored }.sumOf { it.amount }
                    Row(
                        Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(day, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        if (daySpent > 0) {
                            Text(fmt(daySpent), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item(key = "g-$day") {
                    AppCard(padding = PaddingValues(vertical = 2.dp)) {
                        items.forEachIndexed { i, t ->
                            TxnRow(t) { onTxn(t) }
                            if (i < items.lastIndex) RowDivider()
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
)
