package com.expensetracker.sms.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Payment
import androidx.compose.material.icons.rounded.RequestQuote
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.expensetracker.sms.Account
import com.expensetracker.sms.AcctType
import com.expensetracker.sms.Banks
import com.expensetracker.sms.Txn
import com.expensetracker.sms.UiState
import com.expensetracker.sms.fmt
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val asOfFmt = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private fun asOf(ts: Long) = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).format(asOfFmt)

fun typeIcon(t: AcctType): ImageVector = when (t) {
    AcctType.BANK -> Icons.Rounded.AccountBalance
    AcctType.CREDIT_CARD -> Icons.Rounded.CreditCard
    AcctType.DEBIT_CARD -> Icons.Rounded.Payment
    AcctType.WALLET -> Icons.Rounded.AccountBalanceWallet
    AcctType.LOAN -> Icons.Rounded.RequestQuote
}

private fun noun(t: AcctType) = when (t) {
    AcctType.BANK, AcctType.LOAN, AcctType.WALLET -> "account"
    else -> "card"
}

/** List of all accounts / cards as bank-coloured cards. Tap one to see its transactions. */
@Composable
fun AccountsScreen(
    s: UiState,
    selectedKey: String?,
    onSelect: (String?) -> Unit,
    onTxn: (Txn) -> Unit,
    onRename: (String, String?) -> Unit,
    onSetType: (String, AcctType?) -> Unit = { _, _ -> },
    onSetHidden: (String, Boolean) -> Unit = { _, _ -> },
) {
    val selected = s.accounts.firstOrNull { it.key == selectedKey }
    if (selected != null) {
        AccountDetail(selected, s.txns.filter { it.accountKey == selected.key }, onTxn, onRename, onSetType, onSetHidden)
        return
    }
    if (s.accounts.isEmpty()) {
        EmptyState(
            Icons.Rounded.CreditCard,
            "No accounts yet",
            "Bank accounts and cards appear here automatically once their transaction SMS arrive.",
        )
        return
    }

    val shown = s.accounts.filter { !it.hidden }
    val hidden = s.accounts.filter { it.hidden }
    val banks = shown.filter { it.type == AcctType.BANK }
    val cards = shown.filter { it.type == AcctType.CREDIT_CARD }
    val loans = shown.filter { it.type == AcctType.LOAN }
    val others = shown.filter { it.type == AcctType.DEBIT_CARD || it.type == AcctType.WALLET }
    var showHidden by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { AccountsSummary(shown.filter { it.type != AcctType.LOAN }) }
        listOf("Bank accounts" to banks, "Credit cards" to cards, "Debit cards & wallets" to others).forEach { (title, list) ->
            if (list.isNotEmpty()) {
                item(key = "h-$title") { SectionHeader("$title · ${list.size}") }
                items(list, key = { it.key }) { a -> BankCard(a, Modifier.clip(RoundedCornerShape(20.dp)).clickable { onSelect(a.key) }) }
            }
        }
        // Loans aren't money you hold, so they get a slim list instead of a bank card.
        if (loans.isNotEmpty()) {
            item(key = "h-loans") { SectionHeader("Loans · ${loans.size}") }
            item(key = "loans") { AccountList(loans, onSelect) }
        }
        if (hidden.isNotEmpty()) {
            item(key = "h-hidden") {
                SectionHeader("Hidden · ${hidden.size}", if (showHidden) "Collapse" else "Show") { showHidden = !showHidden }
            }
            if (showHidden) item(key = "hidden") { AccountList(hidden, onSelect, unhide = { onSetHidden(it, false) }) }
        }
        item {
            Text(
                "Balances come from the latest SMS that mentioned one. Tap a card to rename it, change its type (e.g. loan account) or hide it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun AccountsSummary(accounts: List<Account>) {
    val bankBal = accounts.filter { it.type == AcctType.BANK }.mapNotNull { it.balance }.sum()
    val cardSpent = accounts.filter { it.type == AcctType.CREDIT_CARD }.sumOf { it.monthSpent }
    val totalSpent = accounts.sumOf { it.monthSpent }
    AppCard(padding = PaddingValues(vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth()) {
            SummaryCell("Accounts", "${accounts.size}", Modifier.weight(1f))
            SummaryCell("Bank balance", if (bankBal > 0) compact(bankBal) else "—", Modifier.weight(1f))
            SummaryCell("Card spends", compact(cardSpent), Modifier.weight(1f))
            SummaryCell("Total spent", compact(totalSpent), Modifier.weight(1f))
        }
    }
}

@Composable
private fun SummaryCell(label: String, value: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** A credit-card-shaped tile in the bank's colours. */
/** Compact rows for loans and hidden accounts. */
@Composable
private fun AccountList(list: List<Account>, onSelect: (String?) -> Unit, unhide: ((String) -> Unit)? = null) {
    AppCard(padding = PaddingValues(vertical = 4.dp)) {
        list.forEachIndexed { i, a ->
            Row(
                Modifier.fillMaxWidth().clickable { onSelect(a.key) }.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val (c1, _) = Banks.colorsFor(a.bank)
                IconBadgeVector(typeIcon(a.type), Color(c1))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.nickname ?: a.bank, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${a.type.label} ••${a.last4} · ${a.totalCount} SMS" + if (a.monthSpent > 0) " · ${fmt(a.monthSpent)} this month" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (unhide != null) TextButton(onClick = { unhide(a.key) }) { Text("Unhide") }
            }
            if (i < list.lastIndex) RowDivider()
        }
    }
}

@Composable
private fun IconBadgeVector(icon: ImageVector, color: Color) {
    Box(
        Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(20.dp), tint = color) }
}

@Composable
fun BankCard(a: Account, modifier: Modifier = Modifier) {
    val (c1, c2) = Banks.colorsFor(a.bank)
    val white = Color.White
    val dim = white.copy(alpha = 0.72f)
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(2.15f)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Color(c1), Color(c2)))),
    ) {
        // Soft decorative circles
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(white.copy(alpha = 0.07f), radius = size.height * 0.9f, center = Offset(size.width * 1.02f, size.height * 0.05f))
            drawCircle(white.copy(alpha = 0.05f), radius = size.height * 0.6f, center = Offset(size.width * 0.82f, size.height * 1.05f))
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(a.nickname ?: a.bank, color = white, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (a.nickname != null) "${a.bank} · ${a.type.label}" else a.type.label,
                        color = dim, style = MaterialTheme.typography.labelMedium, maxLines = 1,
                    )
                }
                Icon(typeIcon(a.type), null, tint = white, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.weight(1f))
            // Chip + number
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (a.type == AcctType.CREDIT_CARD || a.type == AcctType.DEBIT_CARD) {
                    Box(
                        Modifier.size(width = 34.dp, height = 26.dp).clip(RoundedCornerShape(6.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFFF5D98B), Color(0xFFC9A44C)))),
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Text(
                    when (a.type) {
                        AcctType.BANK, AcctType.WALLET -> "A/c  ••••  ${a.last4}"
                        AcctType.LOAN -> "Loan  ••••  ${a.last4}"
                        else -> "••••  ••••  ••••  ${a.last4}"
                    },
                    color = white,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 17.sp,
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (a.type == AcctType.CREDIT_CARD) "Available limit" else "Balance",
                        color = dim, style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        a.balance?.let { fmt(it) } ?: "—",
                        color = white, style = MaterialTheme.typography.titleMedium, maxLines = 1,
                    )
                    if (a.balanceTs != null) Text("as of ${asOf(a.balanceTs)}", color = dim, style = MaterialTheme.typography.labelSmall)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("This month", color = dim, style = MaterialTheme.typography.labelSmall)
                    Text("−${fmt(a.monthSpent)}", color = white, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    if (a.monthReceived > 0) {
                        Text("+${incomeAmount(a.monthReceived)}", color = Color(0xFFB9F6CA), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountDetail(
    a: Account,
    txns: List<Txn>,
    onTxn: (Txn) -> Unit,
    onRename: (String, String?) -> Unit,
    onSetType: (String, AcctType?) -> Unit,
    onSetHidden: (String, Boolean) -> Unit,
) {
    var renaming by remember { mutableStateOf(false) }
    var choosingType by remember { mutableStateOf(false) }
    val byCat = txns.filter { it.isDebit && !it.ignored }.groupBy { it.category }
        .mapValues { e -> e.value.sumOf { it.amount } }.entries.sortedByDescending { it.value }

    LazyColumn(
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { BankCard(a) }
        item {
            AppCard(padding = PaddingValues(vertical = 12.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    SummaryCell("Spent", fmt(a.monthSpent), Modifier.weight(1f))
                    SummaryCell("Received", incomeAmount(a.monthReceived), Modifier.weight(1f))
                    SummaryCell("Transactions", "${a.monthCount}", Modifier.weight(1f))
                }
                HorizontalDivider(Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    Modifier.fillMaxWidth().clickable { renaming = true }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (a.nickname == null) "Give this ${noun(a.type)} a name" else "Rename “${a.nickname}”",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    Modifier.fillMaxWidth().clickable { choosingType = true }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(typeIcon(a.type), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Type: ${a.type.label}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(
                            if (a.typeSet) "Set by you" else "Guessed from SMS · tap if it's wrong (e.g. a loan account)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    Modifier.fillMaxWidth().clickable { onSetHidden(a.key, !a.hidden) }.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.VisibilityOff, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Hide from Accounts", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "Not a real account? Hide it. Its transactions stay in your totals.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = a.hidden, onCheckedChange = { onSetHidden(a.key, it) })
                }
            }
        }
        if (byCat.isNotEmpty()) {
            item {
                AppCard(padding = PaddingValues(14.dp)) {
                    Text("Where it went", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(10.dp))
                    val total = byCat.sumOf { it.value }
                    byCat.take(5).forEach { (cat, amt) ->
                        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            CategoryBadge(cat, 28.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Row {
                                    Text(cat, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                    Text(fmt(amt), style = MaterialTheme.typography.labelLarge)
                                }
                                Spacer(Modifier.height(3.dp))
                                ThinProgress(amt / total, com.expensetracker.sms.Categories.color(cat), height = 4.dp)
                            }
                        }
                    }
                }
            }
        }
        item { SectionHeader("Transactions this month") }
        item {
            if (txns.isEmpty()) {
                AppCard { Text("No transactions this month.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                AppCard(padding = PaddingValues(vertical = 2.dp)) {
                    txns.forEachIndexed { i, t ->
                        TxnRow(t) { onTxn(t) }
                        if (i < txns.lastIndex) RowDivider()
                    }
                }
            }
        }
    }

    if (renaming) {
        var text by remember { mutableStateOf(a.nickname ?: "") }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Name this ${noun(a.type)}") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(24) },
                    placeholder = { Text("e.g. Salary account, Travel card") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = { TextButton(onClick = { renaming = false; onRename(a.key, text) }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } },
        )
    }

    if (choosingType) {
        AlertDialog(
            onDismissRequest = { choosingType = false },
            title = { Text("What is ••${a.last4}?") },
            text = {
                Column {
                    AcctType.entries.forEach { t ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .selectable(selected = a.type == t, role = androidx.compose.ui.semantics.Role.RadioButton) {
                                    choosingType = false; onSetType(a.key, t)
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = a.type == t, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Icon(typeIcon(t), null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(t.label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    if (a.typeSet) {
                        TextButton(onClick = { choosingType = false; onSetType(a.key, null) }) { Text("Let the app guess again") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosingType = false }) { Text("Close") } },
        )
    }
}
