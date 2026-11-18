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

    private val colors = mapOf(
        "Food" to 0xFFF97316, "Groceries" to 0xFF22C55E, "Shopping" to 0xFFEC4899,
        "Travel" to 0xFF3B82F6, "Fuel" to 0xFFB45309, "Bills" to 0xFFEAB308,
        "Rent" to 0xFF8B5CF6, "Entertainment" to 0xFFEF4444, "Health" to 0xFF06B6D4,
        "Education" to 0xFF6366F1, "EMI/Loan" to 0xFF64748B, "Investment" to 0xFF10B981,
        "Transfers" to 0xFF0EA5E9, "ATM/Cash" to 0xFF84CC16, "Others" to 0xFF94A3B8,
        INCOME to 0xFF16A34A,
    )

    fun color(c: String): Color = Color(colors[c] ?: 0xFF94A3B8)
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
