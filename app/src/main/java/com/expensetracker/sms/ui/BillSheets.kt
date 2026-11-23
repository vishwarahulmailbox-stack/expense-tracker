@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.expensetracker.sms.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.expensetracker.sms.Bill
import com.expensetracker.sms.BillKind
import com.expensetracker.sms.Recurrence
import com.expensetracker.sms.Upi
import com.expensetracker.sms.fmt
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
private fun BillSheetFrame(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) { Column(Modifier.imePadding()) { content() } }
}

@Composable
private fun FieldLabel(text: String) = Text(
    text,
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
)

private fun Double.plain() = if (this == Math.floor(this)) toLong().toString() else toString()

// ------------------------------------------------------------- add / edit

@Composable
fun BillEditorSheet(bill: Bill?, onDismiss: () -> Unit, onSave: (Bill) -> Unit, onDelete: (Bill) -> Unit, onTogglePaid: (Bill) -> Unit) {
    BillSheetFrame(onDismiss) { BillEditorContent(bill, onSave, onDelete, onTogglePaid) }
}

@Composable
fun BillEditorContent(bill: Bill?, onSave: (Bill) -> Unit, onDelete: (Bill) -> Unit = {}, onTogglePaid: (Bill) -> Unit = {}) {
    var name by remember { mutableStateOf(bill?.name ?: "") }
    var kind by remember { mutableStateOf(bill?.kind ?: BillKind.ELECTRICITY) }
    var amount by remember { mutableStateOf(bill?.amount?.plain() ?: "") }
    var minDue by remember { mutableStateOf(bill?.minDue?.plain() ?: "") }
    var due by remember { mutableStateOf(bill?.dueDate ?: LocalDate.now().plusDays(7)) }
    var recurrence by remember { mutableStateOf(bill?.recurrence ?: Recurrence.MONTHLY) }
    var autopay by remember { mutableStateOf(bill?.autopay ?: false) }
    var vpa by remember { mutableStateOf(bill?.payeeVpa ?: "") }
    var last4 by remember { mutableStateOf(bill?.last4 ?: "") }
    var note by remember { mutableStateOf(bill?.note ?: "") }
    var pickDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val amt = amount.toDoubleOrNull()
    val vpaError = Upi.validate(vpa)
    val valid = name.isNotBlank() && amt != null && amt > 0 && vpaError == null

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BillBadge(kind, 44.dp)
            Spacer(Modifier.width(12.dp))
            Text(if (bill == null) "Add bill" else "Edit bill", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = name, onValueChange = { name = it.take(40) }, label = { Text("Bill name") },
            placeholder = { Text("e.g. BESCOM, LIC policy, Airtel postpaid") }, singleLine = true,
            shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
        )

        FieldLabel("Type")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BillKind.entries.forEach { k ->
                FilterChip(
                    selected = kind == k,
                    onClick = { kind = k; recurrence = if (bill == null) k.defaultRecurrence else recurrence },
                    label = { Text(k.label) },
                    leadingIcon = { Icon(iconFor(k.icon), null, Modifier.size(16.dp), tint = kindColor(k)) },
                )
            }
        }

        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = amount, onValueChange = { v -> amount = v.filter { it.isDigit() || it == '.' } },
                label = { Text(if (kind == BillKind.CREDIT_CARD) "Total due" else "Amount") }, prefix = { Text("₹ ") },
                singleLine = true, shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
            )
            if (kind == BillKind.CREDIT_CARD) {
                OutlinedTextField(
                    value = minDue, onValueChange = { v -> minDue = v.filter { it.isDigit() || it == '.' } },
                    label = { Text("Minimum due") }, prefix = { Text("₹ ") }, singleLine = true, shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
                )
            }
        }

        FieldLabel("Due date & repeat")
        AssistChip(
            onClick = { pickDate = true },
            label = { Text(due.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH))) },
            leadingIcon = { Icon(Icons.Rounded.CalendarToday, null, Modifier.size(18.dp)) },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Recurrence.entries.forEach { r -> FilterChip(selected = recurrence == r, onClick = { recurrence = r }, label = { Text(r.label) }) }
        }
        Row(Modifier.fillMaxWidth().clickable { autopay = !autopay }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Autopay / NACH", style = MaterialTheme.typography.bodyLarge)
                Text("Paid automatically by mandate. You'll get a heads-up, no Pay button.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = autopay, onCheckedChange = { autopay = it })
        }

        if (!autopay) {
            FieldLabel("Payee UPI ID (for Pay now)")
            OutlinedTextField(
                value = vpa, onValueChange = { vpa = it.trim().take(80) }, placeholder = { Text("biller@bank") }, singleLine = true,
                isError = vpaError != null,
                supportingText = { Text(vpaError ?: "Optional. Without it, Pay now opens your UPI app instead.") },
                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
        }
        OutlinedTextField(
            value = last4, onValueChange = { v -> last4 = v.filter { it.isDigit() }.take(4) },
            label = { Text("Last 4 digits (card / policy / account)") },
            supportingText = { Text("Optional. Only the last 4 digits are kept, to match the payment SMS.") },
            singleLine = true, shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
        OutlinedTextField(
            value = note, onValueChange = { note = it.take(100) }, placeholder = { Text("Note (optional)") },
            shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )

        Spacer(Modifier.height(16.dp))
        if (bill != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                OutlinedButton(
                    onClick = { if (confirmDelete) onDelete(bill) else confirmDelete = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f),
                ) { Text(if (confirmDelete) "Confirm delete" else "Delete") }
                OutlinedButton(onClick = { onTogglePaid(bill) }, modifier = Modifier.weight(1f)) { Text(if (bill.paid) "Mark unpaid" else "Mark paid") }
            }
        }
        Button(
            enabled = valid,
            onClick = {
                val base = bill ?: Bill(series = "", name = "", kind = kind, amount = 0.0, dueDate = due, recurrence = recurrence)
                onSave(
                    base.copy(
                        name = name.trim(), kind = kind, amount = amt ?: 0.0,
                        minDue = if (kind == BillKind.CREDIT_CARD) minDue.toDoubleOrNull() else null,
                        dueDate = due, recurrence = recurrence, autopay = autopay,
                        payeeVpa = if (autopay) base.payeeVpa else vpa.ifBlank { null },
                        last4 = last4.ifBlank { null }, note = note.trim(),
                        hint = base.hint ?: name.trim().lowercase().substringBefore(' ').takeIf { it.length >= 4 },
                        notified = if (bill != null && bill.dueDate != due) 0 else base.notified,
                    )
                )
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(if (bill == null) "Add bill" else "Save") }
    }

    if (pickDate) {
        val st = rememberDatePickerState(initialSelectedDateMillis = due.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let { due = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancel") } },
        ) { DatePicker(st) }
    }
}

// ------------------------------------------------------------------- pay

/** Shown when "Pay now" can't go straight to UPI: add the payee's UPI ID, or pay inside a UPI app. */
@Composable
fun PaySheet(bill: Bill, onDismiss: () -> Unit, onPayUpi: (Bill, Double) -> Unit, onSaveVpa: (Bill, String) -> Unit) {
    BillSheetFrame(onDismiss) { PayContent(bill, onPayUpi, onSaveVpa, onDismiss) }
}

data class UpiApp(val label: String, val pkg: String, val icon: android.graphics.drawable.Drawable?)

@Composable
fun PayContent(bill: Bill, onPayUpi: (Bill, Double) -> Unit, onSaveVpa: (Bill, String) -> Unit, onDone: () -> Unit = {}) {
    val ctx = LocalContext.current
    var vpa by remember { mutableStateOf(bill.payeeVpa ?: "") }
    var amountText by remember { mutableStateOf(bill.amount.plain()) }
    val vpaError = Upi.validate(vpa)
    val amount = amountText.toDoubleOrNull()
    val apps = remember {
        runCatching {
            val pm = ctx.packageManager
            pm.queryIntentActivities(Intent(Intent.ACTION_VIEW, Uri.parse("upi://pay")), PackageManager.MATCH_DEFAULT_ONLY)
                .map { UpiApp(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.loadIcon(pm)) }
                .distinctBy { it.pkg }
        }.getOrDefault(emptyList())
    }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BillBadge(bill.kind, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Pay ${bill.name}", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Due ${bill.dueDate.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = amountText, onValueChange = { v -> amountText = v.filter { it.isDigit() || it == '.' } }, label = { Text("Amount") },
            prefix = { Text("₹ ") }, singleLine = true, shape = RoundedCornerShape(14.dp), textStyle = MaterialTheme.typography.titleLarge,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
        )
        if (bill.minDue != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                FilterChip(selected = amount == bill.amount, onClick = { amountText = bill.amount.plain() }, label = { Text("Total ${fmt(bill.amount)}") })
                FilterChip(selected = amount == bill.minDue, onClick = { amountText = bill.minDue.plain() }, label = { Text("Minimum ${fmt(bill.minDue)}") })
            }
        }
        FieldLabel("Payee UPI ID")
        OutlinedTextField(
            value = vpa, onValueChange = { vpa = it.trim().take(80) }, placeholder = { Text("biller@bank") }, singleLine = true,
            isError = vpaError != null, supportingText = { Text(vpaError ?: "Saved for next time, so Pay now is one tap.") },
            shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        Button(
            enabled = vpa.isNotBlank() && vpaError == null && amount != null && amount > 0,
            onClick = {
                if (vpa != bill.payeeVpa) onSaveVpa(bill, vpa)
                onPayUpi(bill.copy(payeeVpa = vpa), amount ?: bill.amount)
            },
            modifier = Modifier.fillMaxWidth().height(52.dp).padding(top = 4.dp),
        ) {
            Icon(Icons.Rounded.Bolt, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Pay ${amount?.let { fmt(it) } ?: ""} via UPI")
        }

        if (apps.isNotEmpty()) {
            FieldLabel("Or pay inside a UPI app (Bill payments / BBPS)")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                apps.forEach { app ->
                    Column(
                        Modifier.width(76.dp).clip(RoundedCornerShape(14.dp))
                            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(14.dp))
                            .clickable {
                                ctx.packageManager.getLaunchIntentForPackage(app.pkg)?.let { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                onDone()
                            }
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        app.icon?.let { Image(it.toBitmap(96, 96).asImageBitmap(), null, Modifier.size(32.dp)) }
                        Spacer(Modifier.height(4.dp))
                        Text(app.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    }
                }
            }
            Text(
                "After you pay, the bill is marked Paid automatically when your bank's debit SMS arrives.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 14.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Lock, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Text(
                "You enter your UPI PIN only in your UPI app. This app never asks for or stores PINs, OTPs or card numbers.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
