@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.expensetracker.sms.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.expensetracker.sms.Categories
import com.expensetracker.sms.Txn
import com.expensetracker.sms.fmt
import com.expensetracker.sms.shortTime

@Composable
private fun CategoryPicker(selected: String, options: List<String>, onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { c ->
            FilterChip(
                selected = c == selected,
                onClick = { onPick(c) },
                label = { Text("${Categories.emoji(c)} $c") },
            )
        }
    }
}

@Composable
fun EditTxnDialog(
    txn: Txn,
    onDismiss: () -> Unit,
    onSave: (Txn, Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    var merchant by remember { mutableStateOf(txn.merchant) }
    var category by remember { mutableStateOf(txn.category) }
    var note by remember { mutableStateOf(txn.note) }
    var ignored by remember { mutableStateOf(txn.ignored) }
    var applyAll by remember { mutableStateOf(txn.smsKey != null) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text((if (txn.isDebit) "−" else "+") + fmt(txn.amount), fontWeight = FontWeight.Bold)
                Text(
                    shortTime(txn.timestamp) + (txn.account?.let { " · A/c ••$it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text("Paid to / from") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text("Category", style = MaterialTheme.typography.labelLarge)
                CategoryPicker(category, Categories.all) { category = it }
                Row(Modifier.fillMaxWidth().clickable { applyAll = !applyAll }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = applyAll, onCheckedChange = { applyAll = it })
                    Text("Always use this category for \"${merchant.trim()}\"", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Ignore this transaction", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Not counted in totals (e.g. self-transfer)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = ignored, onCheckedChange = { ignored = it })
                }
                if (txn.smsKey != null) {
                    Spacer(Modifier.height(12.dp))
                    Text("Original SMS", style = MaterialTheme.typography.labelLarge)
                    Text(txn.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { if (confirmDelete) onDelete() else confirmDelete = true }) {
                    Text(
                        if (confirmDelete) "Tap again to delete" else "Delete transaction",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val m = merchant.trim().ifBlank { txn.merchant }
                onSave(txn.copy(merchant = m, category = category, note = note.trim(), ignored = ignored), applyAll)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun AddTxnDialog(onDismiss: () -> Unit, onSave: (Txn) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var merchant by remember { mutableStateOf("") }
    var isDebit by remember { mutableStateOf(true) }
    var category by remember { mutableStateOf("Others") }
    val value = amount.replace(",", "").toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add transaction") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = isDebit, onClick = {
                        isDebit = true
                        if (category == Categories.INCOME) category = "Others"
                    }, label = { Text("Spent") })
                    FilterChip(selected = !isDebit, onClick = {
                        isDebit = false
                        category = Categories.INCOME
                    }, label = { Text("Received") })
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount (₹)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text(if (isDebit) "Paid to (e.g. Chai stall)" else "Received from") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text("Category", style = MaterialTheme.typography.labelLarge)
                CategoryPicker(category, if (isDebit) Categories.expense else listOf(Categories.INCOME)) { category = it }
            }
        },
        confirmButton = {
            TextButton(
                enabled = value != null && value > 0,
                onClick = {
                    onSave(
                        Txn(
                            smsKey = null,
                            amount = value ?: 0.0,
                            isDebit = isDebit,
                            merchant = merchant.trim().ifBlank { if (isDebit) "Cash expense" else "Cash received" },
                            account = null,
                            category = category,
                            timestamp = System.currentTimeMillis(),
                            body = "Added manually",
                        )
                    )
                },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun AmountDialog(title: String, initial: Double?, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var text by remember { mutableStateOf(initial?.let { if (it == Math.floor(it)) it.toLong().toString() else it.toString() } ?: "") }
    val value = text.replace(",", "").toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Amount per month (₹)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = value != null && value > 0, onClick = { onSave(value ?: 0.0) }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (initial != null) TextButton(onClick = { onSave(0.0) }) { Text("Remove") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
