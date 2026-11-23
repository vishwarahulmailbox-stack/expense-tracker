package com.expensetracker.sms

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

data class UiState(
    val month: YearMonth = YearMonth.now(),
    val txns: List<Txn> = emptyList(),
    val budgets: Map<String, Double> = emptyMap(),
    val accounts: List<Account> = emptyList(),
    val bills: List<Bill> = emptyList(),
    /** Every transaction (all months), for trends, drill-downs and recurring detection. */
    val all: List<Txn> = emptyList(),
    val recurring: List<RecurringSuggestion> = emptyList(),
    /** null = all accounts. */
    val accountFilter: String? = null,
    val busy: Boolean = false,
    val message: String? = null,
) {
    val active: List<Txn> get() = txns.filter { !it.ignored }
    val spent: Double get() = active.filter { it.isDebit }.sumOf { it.amount }
    val income: Double get() = active.filter { !it.isDebit }.sumOf { it.amount }
    val byCategory: Map<String, Double>
        get() = active.filter { it.isDebit }.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }
    val incomeByCategory: Map<String, Double>
        get() = active.filter { !it.isDebit }.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }
    val selfTransfers: List<Txn> get() = txns.filter { it.category == Categories.SELF }
}

enum class ExportRange(val label: String, val fileTag: String) {
    THIS_MONTH("This month", "month"),
    LAST_3("Last 3 months", "3m"),
    LAST_12("Last 12 months", "12m"),
    THIS_YEAR("This year", "year"),
    ALL("Everything", "all");

    /** First day included; null = everything on the phone. */
    fun start(today: LocalDate): LocalDate? = when (this) {
        THIS_MONTH -> today.withDayOfMonth(1)
        LAST_3 -> today.minusMonths(3).plusDays(1)
        LAST_12 -> today.minusMonths(12).plusDays(1)
        THIS_YEAR -> today.withDayOfYear(1)
        ALL -> null
    }
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val db = Db.get(app)
    private val prefs = app.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    var state by mutableStateOf(UiState())
        private set

    var myNames by mutableStateOf(SelfTransfer.namesText(app))
        private set

    // ---- Income privacy: hidden until the phone's screen lock is used; locks again when the app leaves the screen.
    /** First name for the greeting, from "Your names" in Settings. */
    val firstName: String?
        get() = myNames.split(',').firstOrNull()?.trim()?.split(' ')?.firstOrNull()
            ?.takeIf { it.length >= 2 }?.lowercase()?.replaceFirstChar { it.uppercase() }

    var hideIncome by mutableStateOf(prefs.getBoolean("hide_income", true))
        private set
    var incomeUnlocked by mutableStateOf(false)
        private set
    val incomeLocked: Boolean get() = hideIncome && !incomeUnlocked

    fun unlockIncome() { incomeUnlocked = true }
    fun lockIncome() { incomeUnlocked = false }
    fun setHideIncomePref(on: Boolean) {
        hideIncome = on
        prefs.edit().putBoolean("hide_income", on).apply()
    }

    var skipped by mutableStateOf<List<SkippedSms>>(emptyList())
        private set

    init {
        viewModelScope.launch { DataBus.version.collect { reload() } }
        // Load the on-device model in the background so the first suggestion is instant.
        viewModelScope.launch(Dispatchers.IO) { runCatching { CategoryAI.predict(app, "warm up") } }
        // v6: let the on-device AI sort existing "Others" once.
        if (prefs.getBoolean("imported", false) && !prefs.getBoolean("ai_v6", false)) {
            viewModelScope.launch {
                val n = withContext(Dispatchers.IO) { runCatching { CategoryAI.sortOthers(app) }.getOrDefault(0) }
                prefs.edit().putBoolean("ai_v6", true).apply()
                if (n > 0) state = state.copy(message = "AI sorted $n transactions from Others")
                DataBus.bump()
            }
        }
        // v8: SMS-text model + better payee reading ("Fvg: …", "wallet for …"): sort "Others" again.
        if (prefs.getBoolean("imported", false) && !prefs.getBoolean("ai_sms_v1", false)) {
            viewModelScope.launch {
                val n = withContext(Dispatchers.IO) { runCatching { CategoryAI.sortOthers(app, reparse = true) }.getOrDefault(0) }
                prefs.edit().putBoolean("ai_sms_v1", true).apply()
                if (n > 0) state = state.copy(message = "AI sorted $n more transactions from Others")
                DataBus.bump()
            }
        }
        // v4 upgrade: re-read the inbox with the new filters (recovers mandate debits etc.),
        // fill the skipped list and remove payment-app duplicates.
        if (prefs.getBoolean("imported", false) && !prefs.getBoolean("rescan_v4", false)) {
            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    SmsImporter.importInbox(app, 180)
                    Dedupe.scanAll(app)
                }
                prefs.edit().putBoolean("rescan_v4", true).apply()
                DataBus.bump()
            }
        }
        // One-time pass after upgrading: find self transfers in already-saved data.
        if (!prefs.getBoolean("self_scan_v2", false)) {
            viewModelScope.launch {
                withContext(Dispatchers.IO) { SelfTransfer.scanAll(app) }
                prefs.edit().putBoolean("self_scan_v2", true).apply()
                DataBus.bump()
            }
        }
    }

    private fun reload() {
        val m = state.month
        viewModelScope.launch {
            val data = withContext(Dispatchers.IO) {
                val all = db.allTxns()
                val bills = db.bills()
                val dismissed = prefs.getStringSet("recurring_dismissed", emptySet()).orEmpty()
                val billKeys = bills.flatMap { listOfNotNull(Insights.merchantKey(it.name), it.hint?.let(Insights::merchantKey)) }.toSet()
                val recurring = runCatching {
                    Insights.detectRecurring(all, java.time.LocalDate.now(), { k -> billKeys.any { b -> b.isNotEmpty() && (k.contains(b) || b.contains(k)) } }, dismissed)
                }.getOrDefault(emptyList())
                listOf(db.txnsInMonth(m), db.budgets(), db.categories(), db.accounts(m), bills, all, recurring)
            }
            @Suppress("UNCHECKED_CAST")
            Categories.set(data[2] as List<CategoryDef>)
            @Suppress("UNCHECKED_CAST")
            if (state.month == m) {
                state = state.copy(
                    txns = data[0] as List<Txn>,
                    budgets = data[1] as Map<String, Double>,
                    accounts = data[3] as List<Account>,
                    bills = data[4] as List<Bill>,
                    all = data[5] as List<Txn>,
                    recurring = data[6] as List<RecurringSuggestion>,
                )
            }
        }
    }

    fun prevMonth() {
        state = state.copy(month = state.month.minusMonths(1))
        reload()
    }

    fun nextMonth() {
        if (state.month < YearMonth.now()) {
            state = state.copy(month = state.month.plusMonths(1))
            reload()
        }
    }

    fun importIfFirstRun() {
        if (!prefs.getBoolean("imported", false)) importSms(firstTime = true)
    }

    fun importSms(firstTime: Boolean) {
        if (state.busy) return
        state = state.copy(busy = true)
        viewModelScope.launch {
            val n = withContext(Dispatchers.IO) {
                SmsImporter.importInbox(getApplication(), days = if (firstTime) 180 else 90)
            }
            prefs.edit().putBoolean("imported", true).apply()
            state = state.copy(
                busy = false,
                message = if (n > 0) "$n new transactions found" else "No new transactions found",
            )
            DataBus.bump()
        }
    }

    fun clearMessage() {
        state = state.copy(message = null)
    }

    private fun io(block: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { block() }
            DataBus.bump()
        }
    }

    /** Self transfers are never counted; leaving Self transfer counts the transaction again. */
    private fun normalized(original: Txn, e: Txn): Txn = when {
        e.category == Categories.SELF -> e.copy(ignored = true)
        original.category == Categories.SELF && e.ignored == original.ignored -> e.copy(ignored = false)
        else -> e
    }

    /** Auto-save while the edit sheet is open (no learning yet). */
    fun liveUpdate(original: Txn, edited: Txn) = io { db.update(normalized(original, edited)) }

    /** The edit sheet closed: save the final state, apply "always use" and teach the AI once. */
    fun finishEdit(original: Txn, final0: Txn, applyToMerchant: Boolean) {
        if (final0 == original) return
        val changed = original.category != final0.category
        val final = if (changed) final0.copy(aiReviewed = true, aiConf = null) else final0
        val t = normalized(original, final)
        io {
            db.update(t)
            if (changed) {
                CategoryAI.learn(getApplication(), t.merchant, t.category, 1f)
                // Learn only when the category actually changed, so a note edit doesn't lock a merchant.
                if (applyToMerchant) db.setRule(t.merchant, t.category)
            }
        }
    }

    // ------------------------------------------------------------ AI

    var aiCorrections by mutableStateOf(0)
        private set

    fun refreshAiStats() {
        viewModelScope.launch { aiCorrections = withContext(Dispatchers.IO) { CategoryAI.corrections(getApplication()) } }
    }

    /** Transactions the AI isn't sure about, plus leftover "Others", for the review card. */
    val reviewQueue: List<Txn>
        get() = state.txns.filter { !it.ignored && it.isDebit && (it.needsReview || (it.category == Categories.OTHERS && !it.aiReviewed)) }

    /** Review card: keep or change the category; either way the model learns. */
    fun reviewTxn(t: Txn, category: String) {
        val changed = category != t.category
        io {
            CategoryAI.learn(getApplication(), t.merchant, category, if (changed) 1f else 0.5f)
            db.update(t.copy(category = category, aiReviewed = true, aiConf = if (changed) null else t.aiConf))
        }
    }

    fun aiTop(merchant: String): List<Pair<String, Double>> =
        runCatching { CategoryAI.predict(getApplication(), merchant) }.getOrDefault(emptyList())

    fun resetAi() {
        io { CategoryAI.reset(getApplication()) }
        aiCorrections = 0
        state = state.copy(message = "AI learning reset")
    }

    fun saveCategory(def: CategoryDef, oldName: String?) {
        // Update the in-memory list right away so the new category can be picked immediately.
        Categories.set(Categories.allDefs().filter { it.name != oldName && it.name != def.name } + def)
        io { db.saveCategory(def, oldName) }
    }

    fun deleteCategory(def: CategoryDef) = io { db.deleteCategory(def) }

    fun renameAccount(key: String, nickname: String?) = io { db.setNickname(key, nickname) }

    // ------------------------------------------------------------- bills

    fun saveBill(bill: Bill) = io {
        val b = if (bill.id == 0L && bill.series.isBlank()) bill.copy(series = BillEngine.newSeries()) else bill
        val id = db.saveBill(b)
        val saved = if (b.id == 0L) b.copy(id = id) else b
        if (id > 0) BillEngine.matchExisting(getApplication(), saved)
        BillReminders.checkNow(getApplication())
    }

    fun deleteBill(bill: Bill) = io { db.deleteBill(bill.id) }

    fun setBillPaid(bill: Bill, paid: Boolean) {
        io { if (paid) BillEngine.markPaid(getApplication(), bill, null) else BillEngine.markUnpaid(getApplication(), bill) }
        state = state.copy(message = if (paid) "${bill.name} marked as paid" else "${bill.name} marked as unpaid")
    }

    /** UPI app reported success: mark paid now; the bank SMS will be linked when it arrives. */
    fun onUpiResult(bill: Bill, response: String?) {
        when (Upi.isSuccess(response)) {
            true -> setBillPaid(bill, true)
            false -> state = state.copy(message = "Payment didn't go through")
            null -> state = state.copy(message = "We'll mark it paid when the bank SMS arrives")
        }
    }

    // ------------------------------------------------------- insights

    fun setAccountFilter(key: String?) {
        state = state.copy(accountFilter = key)
    }

    fun selectMonth(m: YearMonth) {
        if (m <= YearMonth.now()) {
            state = state.copy(month = m)
            reload()
        }
    }

    /** "Set reminder? Yes" on a detected recurring payment → a bill in the Bills tab. */
    fun acceptRecurring(r: RecurringSuggestion) {
        val kind = when (r.category) {
            "EMI/Loan" -> BillKind.LOAN_EMI
            "Insurance" -> BillKind.INSURANCE
            "Entertainment" -> BillKind.SUBSCRIPTION
            "Rent" -> BillKind.RENT
            else -> BillKind.OTHER
        }
        saveBill(
            Bill(
                series = "recurring:" + r.key, name = r.name, kind = kind, amount = r.amount, dueDate = r.nextDue,
                recurrence = Recurrence.MONTHLY, autopay = r.autopay, hint = r.key, source = "recurring",
            )
        )
        state = state.copy(message = "Reminder set for ${r.name}")
    }

    fun dismissRecurring(r: RecurringSuggestion) {
        val set = prefs.getStringSet("recurring_dismissed", emptySet()).orEmpty() + r.key
        prefs.edit().putStringSet("recurring_dismissed", set).apply()
        state = state.copy(recurring = state.recurring - r)
    }

    /** Categorise every transaction of a merchant at once (and remember it). */
    fun categoriseMerchant(txns: List<Txn>, category: String) {
        if (txns.isEmpty()) return
        io {
            txns.forEach { db.update(normalized(it, it.copy(category = category, aiReviewed = true, aiConf = null))) }
            db.setRule(txns.first().merchant, category)
            CategoryAI.learn(getApplication(), txns.first().merchant, category, 1f)
        }
    }

    var searchResults by mutableStateOf<List<Txn>>(emptyList())
        private set

    fun search(q: String) {
        if (q.isBlank()) { searchResults = emptyList(); return }
        viewModelScope.launch { searchResults = withContext(Dispatchers.IO) { db.search(q) } }
    }

    /** PDF report of a month (optionally one account), shared via the system sheet. */
    fun exportPdf(month: YearMonth, accountKey: String?, includeIncome: Boolean, onReady: (Uri?) -> Unit) {
        viewModelScope.launch {
            val uri = withContext(Dispatchers.IO) {
                runCatching {
                    val app = getApplication<Application>()
                    val txns = Insights.filterAccount(state.all, accountKey).filter { YearMonth.from(toLocalDate(it.timestamp)) == month }
                    val file = ReportPdf.write(app, month, txns, state.budgets[Categories.TOTAL], includeIncome,
                        accountKey?.let { k -> state.accounts.firstOrNull { it.key == k }?.let { a -> a.nickname ?: "${a.bank} ••${a.last4}" } })
                    FileProvider.getUriForFile(app, app.packageName + ".files", file)
                }.getOrNull()
            }
            onReady(uri)
        }
    }

    fun loadSkipped() {
        viewModelScope.launch { skipped = withContext(Dispatchers.IO) { db.skipped() } }
    }

    fun addSkippedAsTxn(item: SkippedSms) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { SmsImporter.addSkipped(getApplication(), item) }
            state = state.copy(message = if (ok) "Added as a transaction" else "Couldn't read an amount — add it with the + button")
            loadSkipped()
            DataBus.bump()
        }
    }

    fun dismissSkipped(item: SkippedSms) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { db.deleteSkipped(item.key) }
            loadSkipped()
        }
    }

    fun clearSkipped() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { db.clearSkipped() }
            loadSkipped()
        }
    }

    fun saveMyNames(text: String) {
        SelfTransfer.setNames(getApplication(), text)
        myNames = text.trim()
        detectSelfTransfers()
    }

    fun detectSelfTransfers() {
        viewModelScope.launch {
            val n = withContext(Dispatchers.IO) { SelfTransfer.scanAll(getApplication()) }
            state = state.copy(message = if (n > 0) "$n self transfers found and excluded" else "No new self transfers found")
            DataBus.bump()
        }
    }

    fun deleteTxn(t: Txn) = io { db.delete(t.id) }

    fun addManual(t: Txn) = io { db.insert(t) }

    fun setBudget(category: String, amount: Double) = io { db.setBudget(category, amount) }

    fun clearAll() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { db.clearAll() }
            prefs.edit().putBoolean("imported", false).apply()
            state = state.copy(message = "All data cleared")
            DataBus.bump()
        }
    }

    /** Writes all transactions to a CSV in the cache folder and returns a shareable Uri. */
    fun exportCsv(onReady: (Uri?) -> Unit) = exportCsv(null, null, onReady)

    /**
     * Settings → Export: any period, optionally with the original SMS text and the skipped
     * (non-transaction) bank messages. Scans older SMS first when the period goes further back
     * than what was imported. OTPs and personal messages are never stored, so never exported.
     */
    fun exportData(range: ExportRange, includeSms: Boolean, includeSkipped: Boolean, onReady: (Uri?) -> Unit) {
        if (state.busy) return
        val today = LocalDate.now()
        val start = range.start(today)
        val days = start?.let { java.time.temporal.ChronoUnit.DAYS.between(it, today).toInt() + 1 } ?: 3650
        state = state.copy(busy = true, message = if (days > 90) "Reading SMS for ${range.label.lowercase()}…" else null)
        viewModelScope.launch {
            val uri = withContext(Dispatchers.IO) {
                runCatching {
                    val app = getApplication<Application>()
                    if (days > 90) SmsImporter.importInbox(app, days)
                    val fromTs = start?.atStartOfDay(java.time.ZoneId.systemDefault())?.toInstant()?.toEpochMilli() ?: 0L
                    val dir = File(app.cacheDir, "exports").apply { mkdirs() }
                    val file = File(dir, "expenses-${range.fileTag}-$today.csv")
                    fun q(v: String) = "\"" + v.replace("\"", "\"\"") + "\""
                    file.bufferedWriter().use { w ->
                        val head = mutableListOf("Date", "Time", "Type", "Amount", "Merchant", "Category", "Picked by", "Bank", "Account", "Tags", "Note", "Excluded")
                        if (includeSms) head += "SMS"
                        w.write("\uFEFF" + head.joinToString(",") + "\n") // BOM so Excel shows ₹ and Hindi text correctly
                        val tf = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
                        db.allTxns().filter { it.timestamp >= fromTs }.sortedBy { it.timestamp }.forEach { t ->
                            val at = java.time.Instant.ofEpochMilli(t.timestamp).atZone(java.time.ZoneId.systemDefault())
                            val cells = mutableListOf(
                                at.toLocalDate().toString(), at.format(tf),
                                if (t.isDebit) "Expense" else "Income",
                                "%.2f".format(Locale.US, t.amount),
                                t.merchant, t.category,
                                when {
                                    t.smsKey == null -> "Manual"
                                    t.aiReviewed -> "You"
                                    t.aiConf != null -> "AI " + Math.round(t.aiConf * 100) + "%"
                                    else -> "Auto"
                                },
                                t.bank ?: "", t.account ?: "",
                                t.tags.joinToString(" ") { "#$it" }, t.note,
                                if (t.ignored) "Yes" else "No",
                            )
                            if (includeSms) cells += t.body.replace('\n', ' ')
                            w.write(cells.joinToString(",") { q(it) } + "\n")
                        }
                        if (includeSkipped) {
                            db.skipped(100_000).filter { it.timestamp >= fromTs }.sortedBy { it.timestamp }.forEach { k ->
                                val at = java.time.Instant.ofEpochMilli(k.timestamp).atZone(java.time.ZoneId.systemDefault())
                                val cells = mutableListOf(
                                    at.toLocalDate().toString(), at.format(tf), "Skipped", "", k.sender, k.reason.label,
                                    "Auto", "", "", "", "", "Yes",
                                )
                                if (includeSms) cells += k.body.replace('\n', ' ')
                                w.write(cells.joinToString(",") { q(it) } + "\n")
                            }
                        }
                    }
                    FileProvider.getUriForFile(app, app.packageName + ".files", file)
                }.getOrNull()
            }
            state = state.copy(busy = false, message = if (uri == null) "Export failed" else null)
            DataBus.bump()
            onReady(uri)
        }
    }

    /** CSV of everything, or of one month / one account. */
    fun exportCsv(month: YearMonth?, accountKey: String?, onReady: (Uri?) -> Unit) {
        viewModelScope.launch {
            val uri = withContext(Dispatchers.IO) {
                runCatching {
                    val app = getApplication<Application>()
                    val dir = File(app.cacheDir, "exports").apply { mkdirs() }
                    val file = File(dir, "expenses-${LocalDate.now()}.csv")
                    file.bufferedWriter().use { w ->
                        w.write("Date,Type,Amount,Merchant,Category,Account,Note,Tags,Excluded\n")
                        Insights.filterAccount(db.allTxns(), accountKey)
                            .filter { month == null || YearMonth.from(toLocalDate(it.timestamp)) == month }
                            .forEach { t ->
                            val cells = listOf(
                                toLocalDate(t.timestamp).toString(),
                                if (t.isDebit) "Expense" else "Income",
                                "%.2f".format(Locale.US, t.amount),
                                t.merchant, t.category, t.account ?: "", t.note, t.tags.joinToString(" ") { "#$it" },
                                if (t.ignored) "Yes" else "No",
                            )
                            w.write(cells.joinToString(",") { "\"" + it.replace("\"", "\"\"") + "\"" } + "\n")
                        }
                    }
                    FileProvider.getUriForFile(app, app.packageName + ".files", file)
                }.getOrNull()
            }
            onReady(uri)
        }
    }
}
