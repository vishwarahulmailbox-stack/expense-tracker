@file:OptIn(ExperimentalMaterial3Api::class)

package com.expensetracker.sms.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.rounded.Add
import com.expensetracker.sms.CatType
import com.expensetracker.sms.Categories
import com.expensetracker.sms.CategoryDef
import com.expensetracker.sms.Txn
import com.expensetracker.sms.fmt
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
private fun Sheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(Modifier.imePadding()) { content() }
    }
}

/** 4-column grid of category tiles. */
@Composable
fun CategoryGrid(selected: String?, options: List<String>, onAddNew: (() -> Unit)? = null, onPick: (String) -> Unit) {
    val items = if (onAddNew != null) options + NEW_TILE else options
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { c ->
                    if (c == NEW_TILE) {
                        NewTile(Modifier.weight(1f)) { onAddNew?.invoke() }
                        return@forEach
                    }
                    val isSel = c == selected
                    val color = Categories.color(c)
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSel) color.copy(alpha = 0.12f) else Color.Transparent)
                            .border(
                                BorderStroke(if (isSel) 1.5.dp else 1.dp, if (isSel) color else MaterialTheme.colorScheme.outlineVariant),
                                RoundedCornerShape(14.dp),
                            )
                            .clickable { onPick(c) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CategoryBadge(c, 34.dp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            c,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = if (c.length > 11) 11.sp else MaterialTheme.typography.labelSmall.fontSize,
                            letterSpacing = if (c.length > 11) 0.sp else MaterialTheme.typography.labelSmall.letterSpacing,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private const val NEW_TILE = "\u0000new"

@Composable
private fun NewTile(modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.height(6.dp))
        Text("New", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

private val fullFmt = DateTimeFormatter.ofPattern("EEE, d MMM yyyy · h:mm a", Locale.ENGLISH)

// --------------------------------------------------------------- Edit

@Composable
fun EditTxnSheet(
    txn: Txn,
    onDismiss: () -> Unit,
    /** Saves the current edits right away (debounced while typing). */
    onPersist: (Txn) -> Unit,
    /** Called once when the sheet closes: final state + "always use" choice (for rules & AI learning). */
    onDone: (Txn, Boolean) -> Unit,
    onDelete: () -> Unit,
    onCreateCategory: (CategoryDef) -> Unit,
    aiTop: List<Pair<String, Double>> = emptyList(),
) {
    var latest by remember { mutableStateOf(txn) }
    var rule by remember { mutableStateOf(txn.smsKey != null) }
    LaunchedEffect(latest) {
        if (latest != txn) {
            delay(400)
            onPersist(latest)
        }
    }
    Sheet(onDismiss = { onDone(latest, rule); onDismiss() }) {
        TxnEditorContent(txn, onChange = { t, r -> latest = t; rule = r }, onDelete, onCreateCategory, aiTop)
    }
}

@Composable
fun TxnEditorContent(
    txn: Txn,
    onChange: (Txn, Boolean) -> Unit,
    onDelete: () -> Unit,
    onCreateCategory: (CategoryDef) -> Unit = {},
    aiTop: List<Pair<String, Double>> = emptyList(),
) {
    var creating by remember { mutableStateOf(false) }
    var merchant by remember { mutableStateOf(txn.merchant) }
    var category by remember { mutableStateOf(txn.category) }
    var note by remember { mutableStateOf(txn.note) }
    var ignored by remember { mutableStateOf(txn.ignored) }
    var tags by remember { mutableStateOf(txn.tags) }
    var applyRule by remember { mutableStateOf(txn.smsKey != null) }
    var showSms by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val initialRule = remember { applyRule }

    // No Save button: every change is reported straight away.
    val current = txn.copy(merchant = merchant.trim().ifBlank { txn.merchant }, category = category, note = note.trim(), ignored = ignored, tags = tags)
    LaunchedEffect(current, applyRule) {
        if (current != txn || applyRule != initialRule) onChange(current, applyRule)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
    ) {
        // Header
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            CategoryBadge(category, 56.dp)
            Spacer(Modifier.height(10.dp))
            Text(
                if (txn.isDebit) "−" + fmt(txn.amount) else "+" + incomeAmount(txn.amount),
                style = MoneyStyle,
                fontSize = 34.sp,
                color = if (txn.isDebit) MaterialTheme.colorScheme.onSurface else AppColors.income,
            )
            Text(
                Instant.ofEpochMilli(txn.timestamp).atZone(ZoneId.systemDefault()).format(fullFmt) +
                    (txn.account?.let { "  ·  A/c ••$it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = merchant,
            onValueChange = { merchant = it },
            label = { Text(if (txn.isDebit) "Paid to" else "Received from") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )

        Label("Category")
        if (txn.isDebit && aiTop.isNotEmpty()) {
            AiSuggestions(aiTop.take(3), category, txn.aiConf != null && !txn.aiReviewed) { category = it }
            Spacer(Modifier.height(10.dp))
        }
        CategoryGrid(category, Categories.forDirection(txn.isDebit), onAddNew = { creating = true }) { category = it }
        if (category == Categories.SELF) {
            Text(
                "Money moved between your own accounts. Never counted as spending or income.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        SwitchRow(
            "Always use for “${merchant.trim().ifBlank { txn.merchant }}”",
            "Apply this category to past and future payments",
            applyRule,
        ) { applyRule = it }

        Label("Note")
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            placeholder = { Text("Add a note (optional)") },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Label("Tags")
        val autoTags = remember(txn.id) { com.expensetracker.sms.Insights.autoTags(txn) }
        TagsEditor(autoTags, tags) { tags = it }
        Spacer(Modifier.height(8.dp))
        if (category != Categories.SELF) {
            SwitchRow("Exclude from totals", "For refunds, duplicates or reimbursements", ignored) { ignored = it }
        }

        if (txn.smsKey != null) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { showSms = !showSms }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Original SMS", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Icon(if (showSms) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
            }
            AnimatedVisibility(showSms) {
                Text(
                    txn.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(12.dp),
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CloudDone, null, Modifier.size(16.dp), tint = AppColors.income)
            Spacer(Modifier.width(6.dp))
            Text(
                "Changes are saved automatically",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = { if (confirmDelete) onDelete() else confirmDelete = true },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(Icons.Rounded.DeleteOutline, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (confirmDelete) "Tap again to delete" else "Delete")
            }
        }
    }
    if (creating) {
        CategoryEditorSheet(
            existing = null,
            presetType = if (txn.isDebit) CatType.EXPENSE else CatType.INCOME,
            onDismiss = { creating = false },
            onSave = { def, _ -> onCreateCategory(def); category = def.name; creating = false },
        )
    }
}

// --------------------------------------------------------------- Add

@Composable
fun AddTxnSheet(onDismiss: () -> Unit, onSave: (Txn) -> Unit, onCreateCategory: (CategoryDef) -> Unit) {
    Sheet(onDismiss) { AddTxnContent(onSave, onCreateCategory) }
}

@Composable
fun AddTxnContent(onSave: (Txn) -> Unit, onCreateCategory: (CategoryDef) -> Unit = {}) {
    var creating by remember { mutableStateOf(false) }
    var isDebit by remember { mutableStateOf(true) }
    var amount by remember { mutableStateOf("") }
    var merchant by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Food") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var pickDate by remember { mutableStateOf(false) }
    val value = amount.replace(",", "").toDoubleOrNull()

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
    ) {
        Text("Add transaction", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = isDebit,
                onClick = { isDebit = true; category = "Food" },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) { Text("Expense") }
            SegmentedButton(
                selected = !isDebit,
                onClick = { isDebit = false; category = "Salary" },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
            ) { Text("Income") }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = amount,
            onValueChange = { v -> amount = v.filter { it.isDigit() || it == '.' } },
            prefix = { Text("₹ ", style = MaterialTheme.typography.headlineSmall) },
            placeholder = { Text("0", style = MaterialTheme.typography.headlineSmall) },
            textStyle = MaterialTheme.typography.headlineSmall,
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = merchant,
            onValueChange = { merchant = it },
            label = { Text(if (isDebit) "Paid to" else "Received from") },
            placeholder = { Text(if (isDebit) "e.g. Chai stall" else "e.g. Salary") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        AssistChip(
            onClick = { pickDate = true },
            label = {
                Text(if (date == LocalDate.now()) "Today" else date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)))
            },
            leadingIcon = { Icon(Icons.Rounded.CalendarToday, null, Modifier.size(18.dp)) },
        )
        Label("Category")
        CategoryGrid(category, if (isDebit) Categories.expense else Categories.income, onAddNew = { creating = true }) { category = it }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            placeholder = { Text("Note (optional)") },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))
        Button(
            enabled = value != null && value > 0,
            onClick = {
                val ts = if (date == LocalDate.now()) System.currentTimeMillis()
                else date.atTime(LocalTime.NOON).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                onSave(
                    Txn(
                        smsKey = null,
                        amount = value ?: 0.0,
                        isDebit = isDebit,
                        merchant = merchant.trim().ifBlank { if (isDebit) "Cash expense" else "Cash received" },
                        account = null,
                        category = category,
                        timestamp = ts,
                        body = "Added manually",
                        note = note.trim(),
                    )
                )
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(if (isDebit) "Add expense" else "Add income") }
    }

    if (creating) {
        CategoryEditorSheet(
            existing = null,
            presetType = if (isDebit) CatType.EXPENSE else CatType.INCOME,
            onDismiss = { creating = false },
            onSave = { def, _ -> onCreateCategory(def); category = def.name; creating = false },
        )
    }

    if (pickDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        val d = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        date = if (d.isAfter(LocalDate.now())) LocalDate.now() else d
                    }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}

// --------------------------------------------------------------- Budget

/** [category] null = let the user pick a category first. */
@Composable
fun BudgetSheet(
    category: String?,
    current: Double?,
    spent: Map<String, Double>,
    existing: Set<String>,
    onDismiss: () -> Unit,
    onSave: (String, Double) -> Unit,
) {
    Sheet(onDismiss) { BudgetEditorContent(category, current, spent, existing, onSave) }
}

@Composable
fun BudgetEditorContent(
    category: String?,
    current: Double?,
    spent: Map<String, Double>,
    existing: Set<String>,
    onSave: (String, Double) -> Unit,
) {
    var picked by remember { mutableStateOf(category) }
    var text by remember {
        mutableStateOf(current?.let { if (it == Math.floor(it)) it.toLong().toString() else it.toString() } ?: "")
    }
    val value = text.toDoubleOrNull()
    val isTotal = picked == Categories.TOTAL

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
    ) {
        Text(
            when {
                isTotal -> "Monthly budget"
                current != null -> "Edit $picked budget"
                else -> "New category budget"
            },
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            if (isTotal) "One limit for all your spending each month." else "You'll be notified at 80% and when you go over.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (category == null) {
            Label("Category")
            CategoryGrid(picked, Categories.expense.filter { it !in existing }) { picked = it }
        }
        Label("Monthly limit")
        OutlinedTextField(
            value = text,
            onValueChange = { v -> text = v.filter { it.isDigit() || it == '.' } },
            prefix = { Text("₹ ", style = MaterialTheme.typography.headlineSmall) },
            placeholder = { Text("0", style = MaterialTheme.typography.headlineSmall) },
            textStyle = MaterialTheme.typography.headlineSmall,
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            supportingText = {
                val sp = if (isTotal) spent.values.sum() else picked?.let { spent[it] } ?: 0.0
                if (picked != null) Text("Spent this month: ${fmt(sp)}")
            },
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (current != null && picked != null) {
                OutlinedButton(
                    onClick = { onSave(picked!!, 0.0) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.height(52.dp),
                ) { Text("Remove") }
            }
            Button(
                enabled = picked != null && value != null && value > 0,
                onClick = { onSave(picked!!, value ?: 0.0) },
                modifier = Modifier.weight(1f).height(52.dp),
            ) { Text("Save budget") }
        }
    }
}
