package com.expensetracker.sms

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
)

object Categories {
    const val INCOME = "Income"
    const val TOTAL = "__TOTAL__"

    val expense = listOf(
        "Food", "Groceries", "Shopping", "Travel", "Fuel", "Bills", "Rent",
        "Entertainment", "Health", "Education", "EMI/Loan", "Investment",
        "Transfers", "ATM/Cash", "Others",
    )
    val all = expense + INCOME

    fun emoji(c: String): String = when (c) {
        "Food" -> "🍔"
        "Groceries" -> "🛒"
        "Shopping" -> "🛍️"
        "Travel" -> "🚕"
        "Fuel" -> "⛽"
        "Bills" -> "💡"
        "Rent" -> "🏠"
        "Entertainment" -> "🎬"
        "Health" -> "💊"
        "Education" -> "📚"
        "EMI/Loan" -> "🏦"
        "Investment" -> "📈"
        "Transfers" -> "🔁"
        "ATM/Cash" -> "💵"
        INCOME -> "💰"
        else -> "📦"
    }

    private val palette = listOf(
        0xFFEF6C00, 0xFF43A047, 0xFFD81B60, 0xFF1E88E5, 0xFF6D4C41,
        0xFFFDD835, 0xFF8E24AA, 0xFFE53935, 0xFF00ACC1, 0xFF3949AB,
        0xFF5E35B1, 0xFF00897B, 0xFF7CB342, 0xFF546E7A, 0xFF757575, 0xFF2E7D32,
    )

    fun color(c: String): Color = Color(palette[all.indexOf(c).coerceAtLeast(0) % palette.size])
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
