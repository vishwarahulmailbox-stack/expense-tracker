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
import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.material3.TextButton
import android.content.ActivityNotFoundException
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.rounded.EventNote
import com.expensetracker.sms.Bill
import com.expensetracker.sms.LaunchRequest
import com.expensetracker.sms.Upi
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.IconButton
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.expensetracker.sms.AppViewModel
import com.expensetracker.sms.Categories
import com.expensetracker.sms.Txn

private fun permissions(): Array<String> {
    val list = mutableListOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)
    if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.POST_NOTIFICATIONS)
    return list.toTypedArray()
}

private fun hasSms(ctx: Context) =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

private fun openAppSettings(ctx: Context) {
    ctx.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

@Composable
fun App(vm: AppViewModel = viewModel(), launch: LaunchRequest? = null, onLaunchHandled: () -> Unit = {}) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(hasSms(ctx)) }
    var denied by rememberSaveable { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasSms(ctx)
        denied = !granted
    }
    LifecycleResumeEffect(Unit) {
        granted = hasSms(ctx)
        onPauseOrDispose { }
    }

    if (!granted) {
        OnboardingScreen(
            permissionDenied = denied,
            onContinue = { launcher.launch(permissions()) },
            onOpenSettings = { openAppSettings(ctx) },
            onPrivacy = { showPrivacy = true },
        )
    } else {
        LaunchedEffect(Unit) { vm.importIfFirstRun() }
        val activity = ctx.findActivity()
        var noLock by remember { mutableStateOf(false) }
        // Lock income again whenever the app leaves the screen.
        LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.lockIncome() }
        val reveal: (() -> Unit) -> Unit = { then ->
            if (!vm.incomeLocked) then()
            else if (activity == null) noLock = true
            else unlockWithDeviceLock(activity, onSuccess = { vm.unlockIncome(); then() }, onNoScreenLock = { noLock = true })
        }
        CompositionLocalProvider(LocalIncomeHidden provides vm.incomeLocked, LocalRevealIncome provides reveal) {
            MainScaffold(vm, onPrivacy = { showPrivacy = true }, launch = launch, onLaunchHandled = onLaunchHandled)
        }
        if (noLock) {
            AlertDialog(
                onDismissRequest = { noLock = false },
                title = { Text("Set a screen lock") },
                text = { Text("Income is protected by your phone's screen lock. Set a PIN, pattern or fingerprint in phone Settings to view it.") },
                confirmButton = {
                    TextButton(onClick = {
                        noLock = false
                        runCatching { ctx.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }) { Text("Open Settings") }
                },
                dismissButton = { TextButton(onClick = { noLock = false }) { Text("Not now") } },
            )
        }
    }
    if (showPrivacy) PrivacyDialog { showPrivacy = false }
}

enum class Dest(val label: String, val icon: ImageVector, val selected: ImageVector, val inBar: Boolean = true) {
    Home("Home", Icons.Outlined.Home, Icons.Rounded.Home),
    Txns("Transactions", Icons.Outlined.Receipt, Icons.Rounded.Receipt),
    Bills("Bills", Icons.Outlined.EventNote, Icons.Rounded.EventNote),
    Accounts("Accounts", Icons.Outlined.CreditCard, Icons.Rounded.CreditCard),
    Budget("Budgets", Icons.Outlined.Savings, Icons.Rounded.Savings),
    Settings("Settings", Icons.Outlined.Settings, Icons.Rounded.Settings, inBar = false),
}

/** Pages opened on top of a tab (Back closes the top one). */
sealed interface Sub {
    data object Trends : Sub
    data object Search : Sub
    data class Recap(val month: java.time.YearMonth) : Sub
    data class CategoryDetail(val category: String) : Sub
    data class MerchantDetail(val merchant: com.expensetracker.sms.MerchantTotal) : Sub
}

private sealed interface BudgetEdit {
    data class Of(val category: String?) : BudgetEdit
}

@Composable
private fun MainScaffold(vm: AppViewModel, onPrivacy: () -> Unit, launch: LaunchRequest?, onLaunchHandled: () -> Unit) {
    val ctx = LocalContext.current
    var dest by rememberSaveable { mutableStateOf(Dest.Home) }
    var editing by remember { mutableStateOf<Txn?>(null) }
    var adding by remember { mutableStateOf(false) }
    var budgetEdit by remember { mutableStateOf<BudgetEdit.Of?>(null) }
    var showCategories by rememberSaveable { mutableStateOf(false) }
    var selectedAccount by rememberSaveable { mutableStateOf<String?>(null) }
    var showSkipped by rememberSaveable { mutableStateOf(false) }
    val stack = remember { mutableStateListOf<Sub>() }
    val sub = stack.lastOrNull()
    var txTab by rememberSaveable { mutableIntStateOf(0) }
    var categorising by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val open: (Sub) -> Unit = { stack.add(it) }
    val inSubScreen = sub != null || (dest == Dest.Settings && (showCategories || showSkipped)) || (dest == Dest.Accounts && selectedAccount != null)
    val closeAll = { stack.clear(); showCategories = false; showSkipped = false; selectedAccount = null }
    val closeSubScreen: () -> Unit = { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) else closeAll() }
    BackHandler(enabled = inSubScreen) { closeSubScreen() }
    val snack = remember { SnackbarHostState() }
    val hidden = LocalIncomeHidden.current
    val reveal = LocalRevealIncome.current
    /** Credits (income) open only after the screen lock when income is hidden. */
    val openTxn: (Txn) -> Unit = { t -> if (!t.isDebit && hidden) reveal { editing = t } else editing = t }

    // ---- Bills: edit / add / pay
    var editingBill by remember { mutableStateOf<Bill?>(null) }
    var addingBill by remember { mutableStateOf(false) }
    var payBill by remember { mutableStateOf<Bill?>(null) }      // PaySheet open
    var reviewing by remember { mutableStateOf(false) }          // AI quick-check sheet
    var awaitingUpi by remember { mutableStateOf<Bill?>(null) }  // waiting for the UPI app's result
    val upiLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val b = awaitingUpi ?: return@rememberLauncherForActivityResult
        awaitingUpi = null
        vm.onUpiResult(b, res.data?.getStringExtra("response") ?: res.data?.getStringExtra("Status")?.let { "Status=$it" })
    }
    /** Opens the UPI payment screen; [pkg] = a specific app, null = let the user choose. */
    val launchUpiIn: (Bill, Double, String?) -> Unit = { b, amount, pkg ->
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(Upi.payUri(b.payeeVpa!!, b.name, amount, "Bill ${b.name}")))
        awaitingUpi = b
        try {
            upiLauncher.launch(if (pkg != null) intent.setPackage(pkg) else Intent.createChooser(intent, "Pay with"))
        } catch (e: ActivityNotFoundException) {
            awaitingUpi = null
            payBill = b
        }
    }
    val launchUpi: (Bill, Double) -> Unit = { b, amount -> launchUpiIn(b, amount, null) }
    // One tap: straight to the UPI app if we know the payee, otherwise the pay sheet.
    val pay: (Bill) -> Unit = { b ->
        val vpa = b.payeeVpa
        if (vpa != null && Upi.validate(vpa) == null && b.minDue == null) launchUpi(b, b.amount) else payBill = b
    }
    // Opened from a bill notification
    LaunchedEffect(launch, vm.state.bills) {
        val l = launch ?: return@LaunchedEffect
        dest = Dest.Bills
        if (l.payBillId == null) {
            onLaunchHandled()
            return@LaunchedEffect
        }
        val b = vm.state.bills.firstOrNull { it.id == l.payBillId } ?: return@LaunchedEffect
        onLaunchHandled()
        if (!b.paid) pay(b)
    }
    val s = vm.state
    val share: (Uri?, String, String) -> Unit = { uri, mime, title ->
        if (uri != null) {
            val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            ctx.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
    val onExport: (ExportKind) -> Unit = { kind ->
        when (kind) {
            ExportKind.CSV -> reveal { vm.exportCsv(s.month, s.accountFilter) { share(it, "text/csv", "Export expenses") } }
            ExportKind.PDF -> reveal { vm.exportPdf(s.month, s.accountFilter, true) { share(it, "application/pdf", "Share report") } }
            ExportKind.PDF_SPENDS -> vm.exportPdf(s.month, s.accountFilter, false) { share(it, "application/pdf", "Share report") }
        }
    }
    val recurringKeys = remember(s.recurring, s.bills) {
        (s.recurring.map { it.key } + s.bills.mapNotNull { b -> b.hint?.let { com.expensetracker.sms.Insights.merchantKey(it) } }).toSet()
    }

    LaunchedEffect(s.message) {
        s.message?.let {
            snack.showSnackbar(it)
            vm.clearMessage()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (inSubScreen) {
                        IconButton(onClick = closeSubScreen) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                    }
                },
                title = {
                    Text(
                        when {
                            sub is Sub.Trends -> "Trends"
                            sub is Sub.Search -> "Search"
                            sub is Sub.Recap -> "${monthName(sub.month)} recap"
                            sub is Sub.CategoryDetail -> sub.category
                            sub is Sub.MerchantDetail -> "Merchant"
                            dest == Dest.Settings && showCategories -> "Categories"
                            dest == Dest.Settings && showSkipped -> "Skipped messages"
                            dest == Dest.Accounts && selectedAccount != null ->
                                s.accounts.firstOrNull { it.key == selectedAccount }?.let { it.nickname ?: it.bank } ?: "Account"
                            dest == Dest.Home -> greetingTitle(vm.firstName)
                            else -> dest.label
                        },
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    if (sub == null && dest != Dest.Settings) {
                        IconButton(onClick = { searchQuery = ""; vm.search(""); open(Sub.Search) }) {
                            Icon(Icons.Rounded.Search, "Search")
                        }
                    }
                    if (dest != Dest.Settings && (sub == null || sub is Sub.CategoryDetail || sub is Sub.MerchantDetail)) {
                        MonthPill(s.month, vm::prevMonth, vm::nextMonth)
                    }
                    if (sub == null && dest != Dest.Settings) {
                        IconButton(onClick = { dest = Dest.Settings; closeAll() }) {
                            Icon(Icons.Outlined.Settings, "Settings")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Dest.entries.filter { it.inBar }.forEach { d ->
                    NavigationBarItem(
                        selected = dest == d,
                        onClick = { dest = d; closeAll() },
                        alwaysShowLabel = true,
                        icon = { Icon(if (dest == d) d.selected else d.icon, contentDescription = d.label) },
                        label = { Text(d.label, maxLines = 1, style = MaterialTheme.typography.labelSmall) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }
        },
        floatingActionButton = {
            if (sub != null) {
                // no FAB on detail pages
            } else if (dest == Dest.Bills) {
                ExtendedFloatingActionButton(
                    onClick = { addingBill = true },
                    icon = { Icon(Icons.Rounded.Add, null) },
                    text = { Text("Add bill") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            } else if (dest == Dest.Home || dest == Dest.Txns) {
                ExtendedFloatingActionButton(
                    onClick = { adding = true },
                    icon = { Icon(Icons.Rounded.Add, null) },
                    text = { Text("Add") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (s.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
          CompositionLocalProvider(LocalRecurringKeys provides recurringKeys) {
            if (sub != null) when (sub) {
                Sub.Trends -> TrendsScreen(s, vm::setAccountFilter, onReview = { open(Sub.Recap(it)) }, onCategory = { open(Sub.CategoryDetail(it)) })
                Sub.Search -> SearchScreen(searchQuery, { searchQuery = it; vm.search(it) }, vm.searchResults, openTxn)
                is Sub.Recap -> RecapScreen(s, sub.month, openTxn, onCategory = { open(Sub.CategoryDetail(it)) })
                is Sub.CategoryDetail -> CategoryDetailScreen(s, sub.category, openTxn, onSetBudget = { budgetEdit = BudgetEdit.Of(it) })
                is Sub.MerchantDetail -> MerchantDetailScreen(s, sub.merchant, openTxn)
            } else when (dest) {
                Dest.Home -> HomeScreen(
                    s,
                    onTxn = openTxn,
                    onSeeAll = { dest = Dest.Txns },
                    onOpenBudgets = { dest = Dest.Budget },
                    onOpenBills = { dest = Dest.Bills },
                    onPayBill = pay,
                    reviewCount = vm.reviewQueue.distinctBy { com.expensetracker.sms.Insights.merchantKey(it.merchant).ifEmpty { it.merchant } }.size,
                    onReview = { reviewing = true },
                    name = vm.firstName,
                    onTrends = { open(Sub.Trends) },
                    onCategories = { txTab = 1; dest = Dest.Txns },
                    onSearch = { searchQuery = ""; vm.search(""); open(Sub.Search) },
                    onRecap = { open(Sub.Recap(it)) },
                )
                Dest.Txns -> TransactionsScreen(
                    s,
                    onTxn = openTxn,
                    tab = txTab,
                    onTab = { txTab = it },
                    onCategory = { open(Sub.CategoryDetail(it)) },
                    onMerchant = { open(Sub.MerchantDetail(it)) },
                    onCategorise = { categorising = true },
                    onSetBudget = { budgetEdit = BudgetEdit.Of(it) },
                    onAccountFilter = vm::setAccountFilter,
                    onExport = onExport,
                )
                Dest.Bills -> BillsScreen(
                    s.bills,
                    s.month,
                    onPay = pay,
                    onEdit = { editingBill = it },
                    onMarkPaid = { b, paid -> vm.setBillPaid(b, paid) },
                    recurring = s.recurring,
                    onAcceptRecurring = vm::acceptRecurring,
                    onDismissRecurring = vm::dismissRecurring,
                )
                Dest.Accounts -> AccountsScreen(
                    s,
                    selectedKey = selectedAccount,
                    onSelect = { selectedAccount = it },
                    onTxn = openTxn,
                    onRename = vm::renameAccount,
                )
                Dest.Budget -> BudgetScreen(s, onEdit = { budgetEdit = BudgetEdit.Of(it) })
                Dest.Settings -> if (showCategories) {
                    CategoriesScreen(onSave = vm::saveCategory, onDelete = vm::deleteCategory)
                } else if (showSkipped) {
                    LaunchedEffect(Unit) { vm.loadSkipped() }
                    SkippedScreen(vm.skipped, onAdd = vm::addSkippedAsTxn, onDismiss = vm::dismissSkipped, onClearAll = vm::clearSkipped)
                } else SettingsScreen(
                    onRescan = { vm.importSms(firstTime = false) },
                    onExport = { exporting = true },
                    onClearAll = vm::clearAll,
                    onPrivacy = onPrivacy,
                    onOpenCategories = { showCategories = true },
                    myNames = vm.myNames,
                    onSaveNames = vm::saveMyNames,
                    onDetectSelf = vm::detectSelfTransfers,
                    onOpenSkipped = { showSkipped = true },
                    aiCorrections = vm.aiCorrections.also { LaunchedEffect(Unit) { vm.refreshAiStats() } },
                    onResetAi = vm::resetAi,
                    hideIncome = vm.hideIncome,
                    onHideIncome = { on -> if (on) vm.setHideIncomePref(true) else reveal { vm.setHideIncomePref(false) } },
                )
            }
          }
        }
    }

    if (exporting) {
        ExportSheet(
            onDismiss = { exporting = false },
            onExport = { range, sms, skipped ->
                exporting = false
                reveal { vm.exportData(range, sms, skipped) { share(it, "text/csv", "Export expenses") } }
            },
        )
    }
    if (categorising) {
        val groups = remember { com.expensetracker.sms.Insights.uncategorised(s.all) }
        CategoriseSheet(
            groups = groups,
            suggestionsFor = { vm.aiTop(it) },
            onAssign = { txns, cat -> vm.categoriseMerchant(txns, cat) },
            onDismiss = { categorising = false },
        )
    }

    editingBill?.let { b ->
        BillEditorSheet(
            bill = b,
            onDismiss = { editingBill = null },
            onSave = { vm.saveBill(it); editingBill = null },
            onDelete = { vm.deleteBill(it); editingBill = null },
            onTogglePaid = { vm.setBillPaid(it, !it.paid); editingBill = null },
        )
    }
    if (addingBill) {
        BillEditorSheet(
            bill = null,
            onDismiss = { addingBill = false },
            onSave = { vm.saveBill(it); addingBill = false },
            onDelete = {},
            onTogglePaid = {},
        )
    }
    payBill?.let { b ->
        PaySheet(
            bill = b,
            onDismiss = { payBill = null },
            onPayUpi = { bill, amount -> payBill = null; launchUpi(bill, amount) },
            onSaveVpa = { bill, vpa -> vm.saveBill(bill.copy(payeeVpa = vpa)) },
            onPayInApp = { bill, amount, pkg -> payBill = null; launchUpiIn(bill, amount, pkg) },
        )
    }

    editing?.let { t ->
        EditTxnSheet(
            txn = t,
            onDismiss = { editing = null },
            onPersist = { vm.liveUpdate(t, it) },
            onDone = { final, rule -> vm.finishEdit(t, final, rule) },
            onDelete = { vm.deleteTxn(t); editing = null },
            onCreateCategory = { vm.saveCategory(it, null) },
            aiTop = remember(t.id) { if (t.isDebit) vm.aiTop(t.merchant) else emptyList() },
        )
    }
    if (reviewing) {
        val queue = remember { vm.reviewQueue }
        AiReviewSheet(
            items = queue,
            suggestionsFor = { vm.aiTopFor(it) },
            onDismiss = { reviewing = false },
            onAnswer = { t, cat -> vm.reviewTxn(t, cat) },
            onOpenTxn = { reviewing = false; editing = it },
        )
    }
    if (adding) {
        AddTxnSheet(
            onDismiss = { adding = false },
            onSave = { vm.addManual(it); adding = false },
            onCreateCategory = { vm.saveCategory(it, null) },
        )
    }
    budgetEdit?.let { b ->
        BudgetSheet(
            category = b.category,
            current = b.category?.let { s.budgets[it] },
            spent = s.byCategory,
            existing = s.budgets.keys - Categories.TOTAL,
            onDismiss = { budgetEdit = null },
            onSave = { c, amt -> vm.setBudget(c, amt); budgetEdit = null },
        )
    }
}
