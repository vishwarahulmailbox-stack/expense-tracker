package com.expensetracker.sms.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.MarkEmailUnread
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.expensetracker.sms.BuildConfig

@Composable
fun SettingsScreen(
    onRescan: () -> Unit,
    onExport: () -> Unit,
    onClearAll: () -> Unit,
    onPrivacy: () -> Unit,
    onOpenCategories: () -> Unit = {},
    myNames: String = "",
    onSaveNames: (String) -> Unit = {},
    onDetectSelf: () -> Unit = {},
    onOpenSkipped: () -> Unit = {},
    aiCorrections: Int = 0,
    onResetAi: () -> Unit = {},
) {
    val ctx = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }
    var editNames by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { AiPrivacyCard(aiCorrections, onResetAi) }
        item {
            SettingsGroup("Categories & detection") {
                SettingsRow(Icons.Rounded.Category, "Categories", "Create your own, change icons and colours", onClick = onOpenCategories)
                Divider()
                SettingsRow(
                    Icons.Rounded.Badge, "Your name for self transfers",
                    myNames.ifBlank { "Add the name on your bank accounts" },
                    onClick = { editNames = true },
                )
                Divider()
                SettingsRow(Icons.Rounded.SyncAlt, "Find self transfers", "Mark money moved between your own accounts", onClick = onDetectSelf)
            }
        }
        item {
            SettingsGroup("Data") {
                SettingsRow(Icons.Rounded.Refresh, "Scan SMS again", "Look for missed transactions in the last 90 days", onClick = onRescan)
                Divider()
                SettingsRow(Icons.Rounded.MarkEmailUnread, "Skipped messages", "Money SMS that weren't counted, and why", onClick = onOpenSkipped)
                Divider()
                SettingsRow(Icons.Rounded.FileDownload, "Export to CSV", "Open in Excel or Google Sheets", onClick = onExport)
                Divider()
                SettingsRow(
                    Icons.Rounded.DeleteForever, "Delete all data", "Remove all transactions, budgets and rules",
                    tint = MaterialTheme.colorScheme.error, onClick = { confirmClear = true },
                )
            }
        }
        item {
            SettingsGroup("Notifications") {
                SettingsRow(Icons.Rounded.Notifications, "Notification settings", "New transactions and budget alerts") {
                    ctx.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        }
        item {
            SettingsGroup("About") {
                SettingsRow(Icons.Rounded.Policy, "Privacy policy", null, onClick = onPrivacy)
                Divider()
                SettingsRow(Icons.Rounded.Info, "Version", BuildConfig.VERSION_NAME, showArrow = false, onClick = {})
            }
        }
    }

    if (editNames) {
        var text by remember { mutableStateOf(myNames) }
        AlertDialog(
            onDismissRequest = { editNames = false },
            icon = { Icon(Icons.Rounded.Badge, null) },
            title = { Text("Your name") },
            text = {
                Column {
                    Text(
                        "Transfers to or from this name (e.g. IMPS to your other bank) are marked as self transfers and not counted. " +
                            "Write it as banks show it. Separate multiple names with commas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = { Text("e.g. Rahul Vishwa, R Vishwa") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { editNames = false; onSaveNames(text) }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editNames = false }) { Text("Cancel") } },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            icon = { Icon(Icons.Rounded.DeleteForever, null) },
            title = { Text("Delete all data?") },
            text = { Text("All transactions, budgets and category rules will be removed from this phone. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; onClearAll() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        AppCard(padding = PaddingValues(vertical = 4.dp)) { content() }
    }
}

@Composable
private fun Divider() = HorizontalDivider(Modifier.padding(start = 60.dp), color = MaterialTheme.colorScheme.outlineVariant)

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    showArrow: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (tint == MaterialTheme.colorScheme.error) tint else MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (showArrow) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun PrivacyDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Privacy policy") },
        text = {
            Text(
                PRIVACY_POLICY,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
