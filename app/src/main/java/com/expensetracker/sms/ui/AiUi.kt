@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.expensetracker.sms.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.expensetracker.sms.Txn
import com.expensetracker.sms.fmt
import kotlin.math.roundToInt

val AiPurple = Color(0xFF7C3AED)

/** "✨ AI suggests: Health 87% · Shopping 8%" chips in the edit sheet. */
@Composable
fun AiSuggestions(top: List<Pair<String, Double>>, selected: String, picked: Boolean, onPick: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(AiPurple.copy(alpha = 0.07f)).padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(16.dp), tint = AiPurple)
            Spacer(Modifier.width(6.dp))
            Text(
                if (picked) "Picked by on-device AI — tap to change" else "On-device AI suggests",
                style = MaterialTheme.typography.labelLarge,
                color = AiPurple,
            )
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            top.forEach { (cat, p) ->
                FilterChip(
                    selected = cat == selected,
                    onClick = { onPick(cat) },
                    label = { Text("$cat ${(p * 100).roundToInt()}%") },
                    leadingIcon = { Icon(categoryIcon(cat), null, Modifier.size(16.dp)) },
                )
            }
        }
    }
}

/** Home card: "✨ 5 transactions to check". */
@Composable
fun AiReviewCard(count: Int, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(AiPurple.copy(alpha = 0.14f), AiPurple.copy(alpha = 0.05f))))
            .clickable(onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(AiPurple), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(20.dp), tint = Color.White)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Check $count categor${if (count == 1) "y" else "ies"}", style = MaterialTheme.typography.titleSmall)
            Text(
                "The on-device AI wasn't sure. Each answer makes it smarter.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun AiReviewSheet(
    items: List<Txn>,
    suggestionsFor: (Txn) -> List<Pair<String, Double>>,
    onDismiss: () -> Unit,
    onAnswer: (Txn, String) -> Unit,
    onOpenTxn: (Txn) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) { AiReviewContent(items, suggestionsFor, onAnswer, onOpenTxn, onDismiss) }
}

@Composable
fun AiReviewContent(
    items: List<Txn>,
    suggestionsFor: (Txn) -> List<Pair<String, Double>>,
    onAnswer: (Txn, String) -> Unit,
    onOpenTxn: (Txn) -> Unit = {},
    onDone: () -> Unit = {},
) {
    // Answered rows disappear locally right away.
    val answered = remember { mutableStateMapOf<Long, String>() }
    val left = items.filter { it.id !in answered }
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = AiPurple)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Quick check", style = MaterialTheme.typography.titleLarge)
                Text("${left.size} left · tap the right category", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (left.isEmpty()) TextButton(onClick = onDone) { Text("Done") }
        }
        Spacer(Modifier.height(8.dp))
        if (left.isEmpty()) {
            EmptyState(Icons.Rounded.Check, "All checked 🎉", "Thanks! The AI learned from your answers — on this phone only.")
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.height(if (left.isEmpty()) 0.dp else 520.dp),
        ) {
            items(left, key = { it.id }) { t ->
                ReviewRow(t, suggestionsFor(t), onPick = { cat -> answered[t.id] = cat; onAnswer(t, cat) }, onMore = { onOpenTxn(t) })
            }
        }
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Lock, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text(
                "AI runs on this phone. Your messages are never uploaded.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReviewRow(t: Txn, top: List<Pair<String, Double>>, onPick: (String) -> Unit, onMore: () -> Unit) {
    val options = (listOf(t.category to (t.aiConf ?: 0.0)) + top).distinctBy { it.first }.take(4)
    AppCard(padding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryBadge(t.category, 36.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(t.merchant, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${fmt(t.amount)} · now: ${t.category}" + (t.aiConf?.let { " (${(it * 100).roundToInt()}% sure)" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEach { (cat, _) ->
                FilterChip(
                    selected = false,
                    onClick = { onPick(cat) },
                    label = { Text(if (cat == t.category) "✓ $cat" else cat) },
                    leadingIcon = { Icon(categoryIcon(cat), null, Modifier.size(16.dp)) },
                )
            }
            TextButton(onClick = onMore) { Text("Other…") }
        }
    }
}

/** Settings card about privacy + AI learning. */
@Composable
fun AiPrivacyCard(corrections: Int, onReset: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    AppCard(padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(AiPurple.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = AiPurple)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("On-device AI", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (corrections > 0) "Learned from $corrections of your answers" else "Learns from your category changes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        listOf(
            "Sorts transactions using a small model inside the app",
            "Your SMS and corrections never leave this phone",
            "The app has no internet permission at all",
        ).forEach {
            Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = AppColors.income)
                Spacer(Modifier.width(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (corrections > 0) {
            TextButton(onClick = { if (confirm) { onReset(); confirm = false } else confirm = true }, contentPadding = PaddingValues(0.dp)) {
                Text(if (confirm) "Tap again to reset AI learning" else "Reset AI learning", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
