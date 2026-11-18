package com.expensetracker.sms.ui

import androidx.compose.foundation.layout.Arrangement
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
import com.expensetracker.sms.Txn
import com.expensetracker.sms.UiState
import com.expensetracker.sms.dayLabel
import com.expensetracker.sms.fmt

@Composable
fun TransactionsScreen(s: UiState, onTxn: (Txn) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableIntStateOf(0) } // 0 all, 1 expenses, 2 income
    var category by rememberSaveable { mutableStateOf<String?>(null) }

    val usedCategories = s.txns.map { it.category }.distinct().sorted()
    val list = s.txns.filter {
        (type == 0 || (type == 1) == it.isDebit) &&
            (category == null || it.category == category) &&
            (query.isBlank() || it.merchant.contains(query, true) || it.category.contains(query, true) ||
                it.note.contains(query, true) || it.amount.toString().startsWith(query))
    }
    val spent = list.filter { it.isDebit && !it.ignored }.sumOf { it.amount }
    val received = list.filter { !it.isDebit && !it.ignored }.sumOf { it.amount }

    Column {
        TextField(
            value = query,
            onValueChange = { query = it },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear") }
            },
            placeholder = { Text("Search merchant, category or note") },
            singleLine = true,
            shape = CircleShape,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                    leadingIcon = { Icon(categoryIcon(c), null, Modifier.padding(0.dp)) },
                    colors = chipColors(),
                )
            }
        }

        if (list.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                Text(
                    "${list.size} transaction${if (list.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (received > 0) Text("+${fmt(received)}  ", style = MaterialTheme.typography.labelLarge, color = AppColors.income)
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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            list.groupBy { dayLabel(it.timestamp) }.forEach { (day, items) ->
                item(key = "h-$day") {
                    val daySpent = items.filter { it.isDebit && !it.ignored }.sumOf { it.amount }
                    Row(
                        Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(day, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        if (daySpent > 0) {
                            Text(fmt(daySpent), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item(key = "g-$day") {
                    AppCard(padding = PaddingValues(vertical = 4.dp)) {
                        items.forEachIndexed { i, t ->
                            TxnRow(t) { onTxn(t) }
                            if (i < items.lastIndex) {
                                HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            }
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
