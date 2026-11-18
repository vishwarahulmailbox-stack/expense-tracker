@file:OptIn(ExperimentalMaterial3Api::class)

package com.expensetracker.sms.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.expensetracker.sms.AppViewModel
import com.expensetracker.sms.Txn

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme(primary = Color(0xFF81C784))
        else -> lightColorScheme(primary = Color(0xFF2E7D32))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

private fun permissions(): Array<String> {
    val list = mutableListOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)
    if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.POST_NOTIFICATIONS)
    return list.toTypedArray()
}

private fun hasSms(ctx: Context) =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

@Composable
fun App(vm: AppViewModel = viewModel()) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(hasSms(ctx)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasSms(ctx)
    }
    LifecycleResumeEffect(Unit) {
        granted = hasSms(ctx)
        onPauseOrDispose { }
    }

    if (!granted) {
        PermissionScreen(
            onAllow = { launcher.launch(permissions()) },
            onOpenSettings = {
                ctx.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            },
        )
        return
    }

    LaunchedEffect(Unit) { vm.importIfFirstRun() }
    MainScaffold(vm)
}

@Composable
private fun PermissionScreen(onAllow: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("💸", fontSize = 64.sp)
        Spacer(Modifier.height(16.dp))
        Text("SMS Expense Tracker", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(
            "Your expenses are tracked automatically from bank, UPI and card SMS.\n\n" +
                "• Only transaction messages are read — OTPs and personal chats are skipped\n" +
                "• Everything stays on your phone. No internet, no account.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAllow, modifier = Modifier.fillMaxWidth()) { Text("Allow SMS access") }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onOpenSettings) { Text("Not working? Open app settings") }
        Text(
            "If Android says the setting is restricted: App info → ⋮ (top right) → Allow restricted settings, then try again.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

enum class Tab(val label: String, val emoji: String) {
    Home("Overview", "🏠"),
    Txns("Transactions", "📋"),
    Budget("Budget", "🎯"),
}

@Composable
private fun MainScaffold(vm: AppViewModel) {
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var editing by remember { mutableStateOf<Txn?>(null) }
    var adding by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }
    val s = vm.state

    LaunchedEffect(s.message) {
        s.message?.let {
            snack.showSnackbar(it)
            vm.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tab.label, fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = { vm.importSms(firstTime = false) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Rescan SMS")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Text(t.emoji, fontSize = 20.sp) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab != Tab.Budget) {
                FloatingActionButton(onClick = { adding = true }) { Icon(Icons.Default.Add, contentDescription = "Add") }
            }
        },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            MonthSelector(s.month, vm::prevMonth, vm::nextMonth)
            if (s.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            when (tab) {
                Tab.Home -> HomeScreen(s, onTxn = { editing = it }, onSeeAll = { tab = Tab.Txns })
                Tab.Txns -> TxnsScreen(s, onTxn = { editing = it })
                Tab.Budget -> BudgetScreen(s, vm::setBudget)
            }
        }
    }

    editing?.let { t ->
        EditTxnDialog(
            txn = t,
            onDismiss = { editing = null },
            onSave = { nt, applyAll -> vm.updateTxn(nt, applyAll); editing = null },
            onDelete = { vm.deleteTxn(t); editing = null },
        )
    }
    if (adding) {
        AddTxnDialog(onDismiss = { adding = false }, onSave = { vm.addManual(it); adding = false })
    }
}
