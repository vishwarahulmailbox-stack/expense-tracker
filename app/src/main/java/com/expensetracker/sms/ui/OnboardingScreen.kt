package com.expensetracker.sms.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun OnboardingScreen(
    permissionDenied: Boolean,
    onContinue: () -> Unit,
    onOpenSettings: () -> Unit,
    onPrivacy: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier.size(80.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.AccountBalanceWallet, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(42.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text("Your spending,\ntracked automatically", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "No manual entry. We turn your bank, card and UPI messages into a clear picture of where your money goes.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
        Feature(Icons.Rounded.Sms, "Auto-detects transactions", "Debits, credits, UPI, cards and ATM withdrawals")
        Feature(Icons.Rounded.PieChart, "Categories & insights", "See where your money goes, month by month")
        Feature(Icons.Rounded.NotificationsActive, "Budget alerts", "Get notified at 80% and when you go over")
        Feature(Icons.Rounded.Lock, "Private by design", "No account, no internet access, no data leaves your phone")

        Spacer(Modifier.height(20.dp))
        // Prominent disclosure (required by Google Play for SMS access)
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
            Row(Modifier.padding(16.dp)) {
                Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    "Expense Tracker needs SMS access to read transaction messages from banks, cards and UPI apps, " +
                        "including new messages as they arrive. For each transaction it saves the amount, merchant, account ending, date and the message itself. " +
                        "Personal messages and OTPs are ignored. Everything is processed on this device and is never uploaded or shared.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (permissionDenied) {
            Spacer(Modifier.height(12.dp))
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "SMS access is blocked",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "1. Open App settings below\n2. Tap ⋮ (top right) → Allow restricted settings\n3. Permissions → SMS → Allow\n4. Come back to this app",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = onOpenSettings) { Text("Open app settings") }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("Agree & continue") }
        TextButton(onClick = onPrivacy, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Privacy policy") }
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

const val PRIVACY_POLICY = """Last updated: 18 November 2026

Expense Tracker ("the app") helps you track spending using the transaction SMS that banks, card issuers and UPI apps send to your phone.

What the app reads
With your permission (READ_SMS and RECEIVE_SMS), the app reads SMS messages on your device to find financial transaction messages. Messages that don't look like transactions, including personal messages and one-time passwords (OTPs), are ignored and not stored.

What the app stores
For each transaction it stores the amount, whether it was a debit or credit, the merchant or sender name, the last digits of the account or card, the date and the original transaction message. Your categories, notes and budgets are stored as well.

Where your data goes
Nowhere. All processing happens on your device. The app has no internet permission, has no servers, uses no analytics or advertising SDKs, and does not share, sell or upload any data. Cloud backup is disabled.

Your control
You can edit or delete any transaction, export your data as a CSV file, or delete everything from Settings → Delete all data. Uninstalling the app removes all of its data. You can revoke SMS access at any time in Android Settings.

Children
The app is not directed at children under 13.

Contact
For questions about this policy, contact the developer through the email address on the app's Google Play listing."""
