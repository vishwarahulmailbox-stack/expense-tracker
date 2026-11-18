package com.expensetracker.sms.ui

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { OverallBudgetCard(s) { onEdit(Categories.TOTAL) } }

        item { SectionHeader("Category budgets", "Add", { onEdit(null) }) }
        if (budgeted.isEmpty()) {
            item {
                AppCard {
                    Text("No category limits yet", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Set limits for categories like Food or Shopping. You'll get a notification at 80% and when you go over.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(onClick = { onEdit(null) }) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add category budget")
                    }
                }
            }
        }
        items(budgeted, key = { "b-$it" }) { c ->
            CategoryBudgetRow(c, spentBy[c] ?: 0.0, s.budgets.getValue(c)) { onEdit(c) }
        }

        if (unbudgeted.isNotEmpty()) {
            item { SectionHeader("Spending without a budget") }
            items(unbudgeted, key = { "u-${it.key}" }) { (c, amt) ->
                AppCard(onClick = { onEdit(c) }, padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CategoryBadge(c, 38.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text("${fmt(amt)} spent", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("Set limit", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun OverallBudgetCard(s: UiState, onEdit: () -> Unit) {
    val limit = s.budgets[Categories.TOTAL]
    AppCard(padding = PaddingValues(20.dp)) {
        if (limit == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryBadge("Investment", 48.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Monthly budget", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Set one limit for all your spending this month.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Savings, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Set monthly budget")
            }
            return@AppCard
        }

        val ratio = s.spent / limit
        val color = budgetColor(ratio)
        val daysLeft = if (s.month == YearMonth.now()) s.month.lengthOfMonth() - LocalDate.now().dayOfMonth + 1 else 0
        Row(verticalAlignment = Alignment.CenterVertically) {
            DonutChart(
                listOf(color to minOf(s.spent, limit)),
                Modifier.size(112.dp),
                thickness = 12.dp,
                total = limit,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${Math.round(ratio * 100)}%", style = MaterialTheme.typography.titleLarge, color = color)
                    Text("used", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text("Monthly budget", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(fmt(limit), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (s.spent > limit) "Over by ${fmt(s.spent - limit)}" else "${fmt(limit - s.spent)} left",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (s.spent > limit) AppColors.danger else MaterialTheme.colorScheme.onSurface,
                )
                if (daysLeft > 0 && s.spent < limit) {
                    Text(
                        "≈ ${fmt(Math.floor((limit - s.spent) / daysLeft))}/day for $daysLeft days",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text("Edit budget") }
    }
}

@Composable
private fun CategoryBudgetRow(c: String, spent: Double, limit: Double, onClick: () -> Unit) {
    val ratio = spent / limit
    val color = budgetColor(ratio)
    AppCard(onClick = onClick, padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryBadge(c, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(c, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    "${fmt(spent)} of ${fmt(limit)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                ratio >= 1.0 -> Pill("Over by ${fmt(spent - limit)}", AppColors.danger)
                ratio >= 0.8 -> Pill("${Math.round(ratio * 100)}% used", AppColors.warning)
                else -> Text("${fmt(limit - spent)} left", style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(Modifier.height(10.dp))
        ThinProgress(ratio, color, Modifier.padding(start = 52.dp))
    }
}
