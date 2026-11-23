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

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val db = Db.get(app)
    private val prefs = app.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    var state by mutableStateOf(UiState())
        private set

    var myNames by mutableStateOf(SelfTransfer.namesText(app))
        private set

    // ---- Income privacy: hidden until the phone's screen lock is used; locks again when the app leaves the screen.
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
                listOf(db.txnsInMonth(m), db.budgets(), db.categories(), db.accounts(m), db.bills())
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
    fun exportCsv(onReady: (Uri?) -> Unit) {
        viewModelScope.launch {
            val uri = withContext(Dispatchers.IO) {
                runCatching {
                    val app = getApplication<Application>()
                    val dir = File(app.cacheDir, "exports").apply { mkdirs() }
                    val file = File(dir, "expenses-${LocalDate.now()}.csv")
                    file.bufferedWriter().use { w ->
                        w.write("Date,Type,Amount,Merchant,Category,Account,Note,Excluded\n")
                        db.allTxns().forEach { t ->
                            val cells = listOf(
                                toLocalDate(t.timestamp).toString(),
                                if (t.isDebit) "Expense" else "Income",
                                "%.2f".format(Locale.US, t.amount),
                                t.merchant, t.category, t.account ?: "", t.note,
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
