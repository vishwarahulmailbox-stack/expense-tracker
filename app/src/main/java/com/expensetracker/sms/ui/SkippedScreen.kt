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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.expensetracker.sms.SkipReason
import com.expensetracker.sms.SkippedSms
import com.expensetracker.sms.SmsParser
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val whenFmt = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH)

/**
 * Money-related SMS that were not counted (reminders, offers, failed payments…), with the reason.
 * OTPs and personal messages are never stored, so they never appear here.
 */
@Composable
fun SkippedScreen(items: List<SkippedSms>, onAdd: (SkippedSms) -> Unit, onDismiss: (SkippedSms) -> Unit, onClearAll: () -> Unit) {
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    val reasons = items.map { it.reason }.distinct()
    val shown = items.filter { filter == null || it.reason.name == filter }

    if (items.isEmpty()) {
        EmptyState(
            Icons.Rounded.MarkEmailRead,
            "Nothing skipped",
            "Messages about money that weren't counted — reminders, offers, failed payments — show up here so you can add them if needed. OTPs are never read or stored.",
        )
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                "These SMS mention money but weren't counted. If one is a real payment, tap “Add”. OTPs are never read or stored.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item { FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("All · ${items.size}") }) }
                items(reasons, key = { it.name }) { r ->
                    FilterChip(
                        selected = filter == r.name,
                        onClick = { filter = if (filter == r.name) null else r.name },
                        label = { Text("${r.label} · ${items.count { it.reason == r }}") },
                    )
                }
            }
        }
        items(shown, key = { it.key }) { s -> SkippedRow(s, { onAdd(s) }, { onDismiss(s) }) }
        item {
            TextButton(onClick = onClearAll, modifier = Modifier.fillMaxWidth()) { Text("Clear list") }
        }
    }
}

@Composable
private fun SkippedRow(s: SkippedSms, onAdd: () -> Unit, onDismiss: () -> Unit) {
    var expanded by rememberSaveable(s.key) { mutableStateOf(false) }
    AppCard(padding = PaddingValues(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                SmsParser.senderName(s.sender),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            Pill(s.reason.label, reasonColor(s.reason))
            Spacer(Modifier.width(4.dp))
            Text(
                Instant.ofEpochMilli(s.timestamp).atZone(ZoneId.systemDefault()).format(whenFmt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            s.body,
            style = MaterialTheme.typography.bodySmall,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = 8.dp).clickable { expanded = !expanded },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Ignore")
            }
            FilledTonalButton(onClick = onAdd, contentPadding = PaddingValues(horizontal = 14.dp)) {
                Icon(Icons.Rounded.Add, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add")
            }
        }
    }
}

@Composable
private fun reasonColor(r: SkipReason) = when (r) {
    SkipReason.FAILED -> AppColors.danger
    SkipReason.REMINDER, SkipReason.MANDATE_SETUP -> AppColors.warning
    SkipReason.PROMO, SkipReason.REQUEST -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
