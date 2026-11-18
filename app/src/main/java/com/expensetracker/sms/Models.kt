package com.expensetracker.sms

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class Txn(
    val id: Long = 0,
    val smsKey: String?,
    val amount: Double,
    val isDebit: Boolean,
    val merchant: String,
    val account: String?,
    val category: String,
    val timestamp: Long,
    val body: String,
    val note: String = "",
    val ignored: Boolean = false,
    val bank: String? = null,
    val acctType: AcctType? = null,
    val balance: Double? = null,
) {
    /** Groups transactions of the same account / card. */
    val accountKey: String? get() = account?.let { "${bank ?: "Account"}|$it" }
}

/** One bank account or card, as shown on the Accounts tab. */
data class Account(
    val key: String,
    val bank: String,
    val last4: String,
    val type: AcctType,
    val nickname: String?,
    val balance: Double?,
    val balanceTs: Long?,
    val monthSpent: Double,
    val monthReceived: Double,
    val monthCount: Int,
    val totalCount: Int,
    val lastTs: Long,
)

enum class CatType { EXPENSE, INCOME, NEUTRAL }

data class CategoryDef(
    val name: String,
    val type: CatType,
    val icon: String,
    val color: Long,
    val builtin: Boolean,
    val sort: Int,
)

object Categories {
    const val TOTAL = "__TOTAL__"
    const val SELF = "Self transfer"
    const val OTHERS = "Others"
    const val OTHER_INCOME = "Other income"
    const val LEGACY_INCOME = "Income"

    private fun e(name: String, icon: String, color: Long, sort: Int) = CategoryDef(name, CatType.EXPENSE, icon, color, true, sort)
    private fun i(name: String, icon: String, color: Long, sort: Int) = CategoryDef(name, CatType.INCOME, icon, color, true, sort)

    val builtins: List<CategoryDef> = listOf(
        e("Food", "restaurant", 0xFFF97316, 10),
        e("Groceries", "grocery", 0xFF22C55E, 20),
        e("Shopping", "shopping", 0xFFEC4899, 30),
        e("Travel", "car", 0xFF3B82F6, 40),
        e("Fuel", "fuel", 0xFFB45309, 50),
        e("Bills", "receipt", 0xFFEAB308, 60),
        e("Rent", "home", 0xFF8B5CF6, 70),
        e("Entertainment", "movie", 0xFFEF4444, 80),
        e("Health", "hospital", 0xFF06B6D4, 90),
        e("Education", "school", 0xFF6366F1, 100),
        e("EMI/Loan", "bank", 0xFF64748B, 110),
        e("Investment", "trending", 0xFF10B981, 120),
        e("Transfers", "swap", 0xFF0EA5E9, 130),
        e("ATM/Cash", "atm", 0xFF84CC16, 140),
        e("Insurance", "shield", 0xFF0D9488, 150),
        e("Personal care", "spa", 0xFFD946EF, 160),
        e("Charges & fees", "fees", 0xFF78716C, 170),
        e("Gifts & donations", "gift", 0xFFF43F5E, 180),
        e(OTHERS, "category", 0xFF94A3B8, 999),
        i("Salary", "work", 0xFF16A34A, 10),
        i("Rent received", "home_work", 0xFF7C3AED, 20),
        i("Dividend", "pie", 0xFF0891B2, 30),
        i("Interest", "savings", 0xFFCA8A04, 40),
        i("Refund & cashback", "replay", 0xFFDB2777, 50),
        i(OTHER_INCOME, "payments", 0xFF059669, 999),
        CategoryDef(SELF, CatType.NEUTRAL, "sync", 0xFF64748B, true, 1000),
    )

    /** Live list (built-in + user-created). Compose state, so screens update when it changes. */
    private var defs by mutableStateOf(builtins.associateBy { it.name })

    fun set(list: List<CategoryDef>) {
        if (list.isNotEmpty()) defs = list.associateBy { it.name }
    }

    fun def(name: String): CategoryDef? = defs[name]
    fun allDefs(): List<CategoryDef> = defs.values.sortedWith(compareBy({ it.type.ordinal }, { it.sort }, { it.name }))

    val expense: List<String> get() = allDefs().filter { it.type == CatType.EXPENSE }.map { it.name }
    val income: List<String> get() = allDefs().filter { it.type == CatType.INCOME }.map { it.name }

    /** Categories that fit a debit or a credit, plus Self transfer. */
    fun forDirection(isDebit: Boolean): List<String> = (if (isDebit) expense else income) + SELF

    fun color(c: String): Color = Color(defs[c]?.color ?: 0xFF94A3B8)
    fun iconKey(c: String): String = defs[c]?.icon ?: if (c == LEGACY_INCOME) "payments" else "category"
    fun fallbackFor(isDebit: Boolean) = if (isDebit) OTHERS else OTHER_INCOME
}

fun fmt(v: Double): String {
    val f = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    val whole = v == Math.floor(v)
    f.maximumFractionDigits = if (whole) 0 else 2
    f.minimumFractionDigits = if (whole) 0 else 2
    return f.format(v)
}

private val dayFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
private val shortFmt = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH)

fun toLocalDate(ts: Long): LocalDate = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate()

fun dayLabel(ts: Long): String {
    val d = toLocalDate(ts)
    val today = LocalDate.now()
    return when (d) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> d.format(dayFmt)
    }
}

fun shortTime(ts: Long): String = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).format(shortFmt)
