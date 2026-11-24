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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableIntStateOf
import com.expensetracker.sms.Categories
import com.expensetracker.sms.Insights
import com.expensetracker.sms.dayLabel
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
            Text("Quick check: $count payee${if (count == 1) "" else "s"}", style = MaterialTheme.typography.titleSmall)
            Text(
                "One tap each. The on-device AI learns from every answer.",
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
    // One question per merchant: answering once covers all its payments.
    val groups = remember(items) {
        items.groupBy { Insights.merchantKey(it.merchant).ifEmpty { it.merchant } }
            .values.map { g -> g.sortedByDescending { it.timestamp } }
            .sortedByDescending { g -> g.size }
    }
    var index by remember { mutableIntStateOf(0) }
    val total = groups.size
    val answer: (List<Txn>, String) -> Unit = { g, cat -> g.forEach { onAnswer(it, cat) }; index++ }

    Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = AiPurple)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Quick check", style = MaterialTheme.typography.titleLarge)
                Text(
                    if (index < total) "${index + 1} of $total · each answer teaches the AI" else "All done",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onDone) { Text(if (index < total) "Later" else "Done") }
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { if (total == 0) 1f else index.toFloat() / total },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
            color = AiPurple,
            trackColor = AiPurple.copy(alpha = 0.12f),
        )
        Spacer(Modifier.height(14.dp))
        if (index >= total) {
            EmptyState(Icons.Rounded.Check, "All checked 🎉", "Thanks! The AI learned from your answers, on this phone only.")
            return@Column
        }
        val g = groups[index]
        val t = g.first()
        val top = remember(t.id) { suggestionsFor(t).filter { it.first != Categories.OTHERS && it.first != t.category }.take(3) }

        // The payment
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable { onOpenTxn(t) }.padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryBadge(t.category, 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.merchant, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        (if (g.size > 1) "${g.size} payments · ${fmt(g.sumOf { it.amount })}" else fmt(t.amount)) +
                            " · " + dayLabel(t.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Now", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(t.category, style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                t.body.replace(Regex("\\s+"), " "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest).padding(10.dp),
            )
        }
        Spacer(Modifier.height(14.dp))

        if (top.isNotEmpty()) {
            Text("AI thinks it's", style = MaterialTheme.typography.labelLarge, color = AiPurple)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                top.forEachIndexed { i, (cat, p) ->
                    val first = i == 0
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                            .background(if (first) AiPurple else AiPurple.copy(alpha = 0.08f))
                            .clickable { answer(g, cat) }
                            .padding(vertical = 10.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(categoryIcon(cat), null, Modifier.size(20.dp), tint = if (first) Color.White else AiPurple)
                        Spacer(Modifier.height(4.dp))
                        Text(cat, style = MaterialTheme.typography.labelMedium, color = if (first) Color.White else MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${(p * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = if (first) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        Text(if (top.isEmpty()) "Pick a category" else "Or pick another", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Categories.expense.filter { it != t.category && it !in top.map { x -> x.first } }.forEach { cat ->
                Row(
                    Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable { answer(g, cat) }.padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(categoryIcon(cat), null, Modifier.size(16.dp), tint = Categories.color(cat))
                    Spacer(Modifier.width(6.dp))
                    Text(cat, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { index++ }) { Text("Skip") }
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = { answer(g, t.category) }) {
                Icon(Icons.Rounded.Check, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Keep ${t.category}")
            }
        }
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Lock, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text(
                if (g.size > 1) "Applies to all ${g.size} payments to this merchant. AI runs on this phone." else "AI runs on this phone. Your messages are never uploaded.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
