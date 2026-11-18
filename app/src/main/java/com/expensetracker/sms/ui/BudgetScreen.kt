package com.expensetracker.sms.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.expensetracker.sms.Categories
import com.expensetracker.sms.UiState
import com.expensetracker.sms.fmt
import java.time.LocalDate
import java.time.YearMonth

/** [onEdit] is called with a category (or Categories.TOTAL) to edit, or null to pick a new category. */
@Composable
fun BudgetScreen(s: UiState, onEdit: (String?) -> Unit) {
    val spentBy = s.byCategory
    val budgeted = Categories.expense.filter { s.budgets.containsKey(it) }
        .sortedByDescending { (spentBy[it] ?: 0.0) / (s.budgets[it] ?: 1.0) }
    val unbudgeted = spentBy.entries.filter { !s.budgets.containsKey(it.key) }.sortedByDescending { it.value }

    LazyColumn(
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { OverallBudgetCard(s) { onEdit(Categories.TOTAL) } }

        item {
            Column {
                SectionHeader("Category limits", "+ Add", { onEdit(null) })
                if (budgeted.isEmpty()) {
                    AppCard(padding = PaddingValues(14.dp)) {
                        Text(
                            "Set limits for categories like Food or Shopping. You'll be notified at 80% and when you go over.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        FilledTonalButton(onClick = { onEdit(null) }) {
                            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add category limit")
                        }
                    }
                } else {
                    AppCard(padding = PaddingValues(vertical = 2.dp)) {
                        budgeted.forEachIndexed { i, c ->
                            CategoryBudgetRow(c, spentBy[c] ?: 0.0, s.budgets.getValue(c)) { onEdit(c) }
                            if (i < budgeted.lastIndex) RowDivider()
                        }
                    }
                }
            }
        }

        if (unbudgeted.isNotEmpty()) {
            item {
                Column {
                    SectionHeader("No limit set")
                    AppCard(padding = PaddingValues(vertical = 2.dp)) {
                        unbudgeted.forEachIndexed { i, (c, amt) ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onEdit(c) }.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CategoryBadge(c, 34.dp)
                                Spacer(Modifier.width(12.dp))
                                Text(c, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                Text(fmt(amt), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(12.dp))
                                Text("Set", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            }
                            if (i < unbudgeted.lastIndex) RowDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverallBudgetCard(s: UiState, onEdit: () -> Unit) {
    val limit = s.budgets[Categories.TOTAL]
    AppCard(padding = PaddingValues(14.dp)) {
        if (limit == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryBadge("Investment", 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Monthly budget", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "One limit for all your spending",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(onClick = onEdit) {
                    Icon(Icons.Rounded.Savings, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Set")
                }
            }
            return@AppCard
        }

        val ratio = s.spent / limit
        val color = budgetColor(ratio)
        val daysLeft = if (s.month == YearMonth.now()) s.month.lengthOfMonth() - LocalDate.now().dayOfMonth + 1 else 0
        Row(verticalAlignment = Alignment.CenterVertically) {
            DonutChart(listOf(color to minOf(s.spent, limit)), Modifier.size(84.dp), thickness = 10.dp, total = limit) {
                Text("${Math.round(ratio * 100)}%", style = MaterialTheme.typography.titleMedium, color = color)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Monthly budget", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    TextButton(onClick = onEdit, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.height(32.dp)) { Text("Edit") }
                }
                Text("${fmt(s.spent)} of ${fmt(limit)}", style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        s.spent > limit -> "Over by ${fmt(s.spent - limit)}"
                        daysLeft > 0 -> "${fmt(limit - s.spent)} left · ${fmt(Math.floor((limit - s.spent) / daysLeft))}/day for $daysLeft days"
                        else -> "${fmt(limit - s.spent)} left"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (s.spent > limit) AppColors.danger else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CategoryBudgetRow(c: String, spent: Double, limit: Double, onClick: () -> Unit) {
    val ratio = spent / limit
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryBadge(c, 34.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    "${fmt(spent)} / ${fmt(limit)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            ThinProgress(ratio, budgetColor(ratio), height = 5.dp)
            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    ratio >= 1.0 -> "Over by ${fmt(spent - limit)}"
                    else -> "${fmt(limit - spent)} left · ${Math.round(ratio * 100)}% used"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (ratio >= 0.8) budgetColor(ratio) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
