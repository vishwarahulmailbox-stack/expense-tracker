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
    val busy: Boolean = false,
    val message: String? = null,
) {
    val active: List<Txn> get() = txns.filter { !it.ignored }
    val spent: Double get() = active.filter { it.isDebit }.sumOf { it.amount }
    val income: Double get() = active.filter { !it.isDebit }.sumOf { it.amount }
    val byCategory: Map<String, Double>
        get() = active.filter { it.isDebit }.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val db = Db.get(app)
    private val prefs = app.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    var state by mutableStateOf(UiState())
        private set

    init {
        viewModelScope.launch { DataBus.version.collect { reload() } }
    }

    private fun reload() {
        val m = state.month
        viewModelScope.launch {
            val (t, b) = withContext(Dispatchers.IO) { db.txnsInMonth(m) to db.budgets() }
            if (state.month == m) state = state.copy(txns = t, budgets = b)
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

    fun updateTxn(t: Txn, applyToMerchant: Boolean) = io {
        db.update(t)
        if (applyToMerchant) db.setRule(t.merchant, t.category)
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
