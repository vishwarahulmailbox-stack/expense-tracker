package com.expensetracker.sms

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs

data class MonthTotal(val month: YearMonth, val spent: Double, val income: Double)

data class MerchantTotal(val key: String, val name: String, val count: Int, val total: Double, val category: String)

/** Same merchant, about the same amount, about the same day, every month → probably a bill/subscription. */
data class RecurringSuggestion(
    val key: String,
    val name: String,
    val amount: Double,
    val category: String,
    val nextDue: LocalDate,
    val months: Int,
    val autopay: Boolean,
)

data class MonthRecap(
    val month: YearMonth,
    val spent: Double,
    val prevSpent: Double,
    val income: Double,
    val count: Int,
    val topCategories: List<Pair<String, Double>>,
    val biggest: Txn?,
    val topMerchant: MerchantTotal?,
    val dailyAvg: Double,
    val noSpendDays: Int,
    val budget: Double?,
)

/** Pure calculations over transactions (unit-tested). */
object Insights {
    private val autopayRx = Regex("""\b(nach|ecs|e-?mandate|mandate|autopay|auto[\s-]?debit|standing instruction|umrn)\b""", RegexOption.IGNORE_CASE)

    fun merchantKey(name: String) = name.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun ym(t: Txn) = YearMonth.from(toLocalDate(t.timestamp))
    private fun counted(t: Txn) = !t.ignored && t.category != Categories.SELF

    fun filterAccount(txns: List<Txn>, accountKey: String?) =
        if (accountKey == null) txns else txns.filter { it.accountKey == accountKey }

    /** Spent and income for each of the last [n] months ending at [end] (oldest first). */
    fun monthlyTotals(txns: List<Txn>, end: YearMonth, n: Int): List<MonthTotal> {
        val months = (n - 1 downTo 0).map { end.minusMonths(it.toLong()) }
        val by = txns.filter(::counted).groupBy(::ym)
        return months.map { m ->
            val list = by[m].orEmpty()
            MonthTotal(m, list.filter { it.isDebit }.sumOf { it.amount }, list.filter { !it.isDebit }.sumOf { it.amount })
        }
    }

    /** One category's spending for each of the last [n] months (oldest first). */
    fun categoryHistory(txns: List<Txn>, category: String, end: YearMonth, n: Int): List<Pair<YearMonth, Double>> {
        val months = (n - 1 downTo 0).map { end.minusMonths(it.toLong()) }
        val by = txns.filter { counted(it) && it.category == category }.groupBy(::ym)
        return months.map { m -> m to by[m].orEmpty().sumOf { it.amount } }
    }

    /** One merchant's spending for each of the last [n] months (oldest first). */
    fun merchantHistory(txns: List<Txn>, key: String, end: YearMonth, n: Int): List<Pair<YearMonth, Double>> {
        val months = (n - 1 downTo 0).map { end.minusMonths(it.toLong()) }
        val by = txns.filter { counted(it) && it.isDebit && merchantKey(it.merchant) == key }.groupBy(::ym)
        return months.map { m -> m to by[m].orEmpty().sumOf { it.amount } }
    }

    /** "Others" payments grouped by merchant (most payments first) for the bulk categorise flow. */
    fun uncategorised(txns: List<Txn>): List<Pair<MerchantTotal, List<Txn>>> {
        val others = txns.filter { it.isDebit && !it.ignored && it.category == Categories.OTHERS }
        val groups = others.groupBy { merchantKey(it.merchant) }.filterKeys { it.isNotEmpty() }
        return merchants(others).map { m -> m to groups[m.key].orEmpty() }.sortedByDescending { it.first.count }
    }

    /** Auto tags plus the user's own tags. */
    fun allTags(t: Txn, recurringKeys: Set<String> = emptySet()): List<String> = (autoTags(t, recurringKeys) + t.tags).distinct()

    /** Spending per merchant, biggest first. */
    fun merchants(txns: List<Txn>): List<MerchantTotal> =
        txns.filter { it.isDebit && counted(it) }
            .groupBy { merchantKey(it.merchant) }
            .filterKeys { it.isNotEmpty() }
            .map { (key, list) ->
                MerchantTotal(
                    key = key,
                    name = list.groupingBy { it.merchant }.eachCount().maxBy { it.value }.key,
                    count = list.size,
                    total = list.sumOf { it.amount },
                    category = list.groupingBy { it.category }.eachCount().maxBy { it.value }.key,
                )
            }
            .sortedByDescending { it.total }

    /**
     * Payments that look monthly: same merchant, amounts within 10%, at most one per month,
     * in at least 3 of the last 6 months — or 2 months in a row with the same amount (≥ ₹100).
     */
    fun detectRecurring(
        txns: List<Txn>,
        today: LocalDate,
        known: (String) -> Boolean = { false },
        dismissed: Set<String> = emptySet(),
    ): List<RecurringSuggestion> {
        val since = today.minusMonths(6).withDayOfMonth(1)
        val skip = setOf("ATM/Cash", "Groceries", "Food", "Fuel", "Shopping", "Health")
        val out = mutableListOf<RecurringSuggestion>()
        txns.filter { it.isDebit && counted(it) && it.category !in skip && !toLocalDate(it.timestamp).isBefore(since) }
            .groupBy { merchantKey(it.merchant) }
            .forEach { (key, list) ->
                if (key.length < 3 || key in dismissed || known(key)) return@forEach
                val sorted = list.sortedBy { it.timestamp }
                val ref = sorted.last().amount
                val similar = sorted.filter { abs(it.amount - ref) <= ref * 0.10 }
                // keep one per month (the one closest to the latest amount)
                val perMonth = similar.groupBy(::ym).mapValues { (_, v) -> v.minBy { abs(it.amount - ref) } }
                if (perMonth.size < 2) return@forEach
                val months = perMonth.keys.sorted()
                val days = perMonth.values.map { toLocalDate(it.timestamp).dayOfMonth }
                if ((days.max() - days.min()) > 7) return@forEach
                val consecutive = months.zipWithNext().all { (a, b) -> ChronoUnit.MONTHS.between(a, b) == 1L }
                val exact = perMonth.values.all { abs(it.amount - ref) < 1.0 }
                val ok = perMonth.size >= 3 || (perMonth.size == 2 && consecutive && exact && ref >= 100)
                if (!ok) return@forEach
                // Stale if the last one is more than ~45 days ago
                val last = toLocalDate(perMonth.values.maxBy { it.timestamp }.timestamp)
                if (ChronoUnit.DAYS.between(last, today) > 45) return@forEach
                var next = last.plusMonths(1)
                while (next.isBefore(today.minusDays(3))) next = next.plusMonths(1)
                out += RecurringSuggestion(
                    key = key,
                    name = sorted.last().merchant,
                    amount = ref,
                    category = sorted.last().category,
                    nextDue = next,
                    months = perMonth.size,
                    autopay = perMonth.values.any { autopayRx.containsMatchIn(it.body) },
                )
            }
        return out.sortedBy { it.nextDue }
    }

    private val onlineRx = Regex(
        """\b(ecom|e-com|online|internet|www\.|\.com|\.in\b|amazon|flipkart|myntra|ajio|meesho|nykaa|swiggy|zomato|netflix|spotify|google|apple|uber|ola|rapido|makemytrip|irctc|blinkit|zepto|bigbasket)""",
        RegexOption.IGNORE_CASE,
    )
    private val upiRx = Regex("""\b(upi|vpa)\b|@[a-z]{2,}""", RegexOption.IGNORE_CASE)
    private val cardRx = Regex("""\b(card|pos)\b""", RegexOption.IGNORE_CASE)

    /** Tags worked out from the SMS: #UPI, #Card, #Online, #Cash, #Autopay, #Recurring. */
    fun autoTags(t: Txn, recurringKeys: Set<String> = emptySet()): List<String> {
        val tags = mutableListOf<String>()
        val text = t.merchant + " " + t.body
        if (t.category == "ATM/Cash" || (t.smsKey == null && t.body == "Added manually")) tags += "Cash"
        if (upiRx.containsMatchIn(t.body)) tags += "UPI"
        if (t.acctType == AcctType.CREDIT_CARD || t.acctType == AcctType.DEBIT_CARD || cardRx.containsMatchIn(t.body)) tags += "Card"
        if (onlineRx.containsMatchIn(text) && "Cash" !in tags) tags += "Online"
        if (autopayRx.containsMatchIn(t.body)) tags += "Autopay"
        if (merchantKey(t.merchant) in recurringKeys || t.note.startsWith("Bill:")) tags += "Recurring"
        return tags
    }

    /** End-of-month story. */
    fun recap(txns: List<Txn>, month: YearMonth, budget: Double?): MonthRecap {
        val inMonth = txns.filter { counted(it) && ym(it) == month }
        val debits = inMonth.filter { it.isDebit }
        val prev = txns.filter { counted(it) && it.isDebit && ym(it) == month.minusMonths(1) }.sumOf { it.amount }
        val spent = debits.sumOf { it.amount }
        val days = if (month == YearMonth.now()) LocalDate.now().dayOfMonth else month.lengthOfMonth()
        val spendDays = debits.map { toLocalDate(it.timestamp).dayOfMonth }.toSet().size
        return MonthRecap(
            month = month,
            spent = spent,
            prevSpent = prev,
            income = inMonth.filter { !it.isDebit }.sumOf { it.amount },
            count = debits.size,
            topCategories = debits.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }
                .entries.sortedByDescending { it.value }.take(3).map { it.key to it.value },
            biggest = debits.maxByOrNull { it.amount },
            topMerchant = merchants(debits).maxByOrNull { it.count },
            dailyAvg = if (days > 0) spent / days else 0.0,
            noSpendDays = (days - spendDays).coerceAtLeast(0),
            budget = budget,
        )
    }
}
