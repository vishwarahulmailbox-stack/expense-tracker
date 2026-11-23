package com.expensetracker.sms

import android.content.Context
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max

/**
 * Glue between SMS, bills and transactions:
 *  - bill / statement SMS  → pending bill (updates the open bill of the same series)
 *  - payment SMS           → matching bill marked Paid and linked to the expense
 *  - NACH / autopay debit  → tracked as a paid autopay bill (+ next month's)
 *  - paid recurring bill   → next occurrence created
 */
object BillEngine {
    private val IC = setOf(RegexOption.IGNORE_CASE)
    private val autopayDebitRx = Regex("""\b(nach|ecs|e-?mandate|mandate|autopay|auto[\s-]?debit|standing instruction|si\s+debit|umrn)\b""", IC)
    private val cardPaymentRx = Regex("""\b(credit card|card payment|cc payment|cred|bbps|card bill|towards (?:your )?card)\b""", IC)

    // ----------------------------------------------------------- matching

    /**
     * How well a debit transaction fits a bill (0 = no match). A match needs the right amount
     * (or, for credit cards, anything between the minimum and total due) within the due window,
     * plus at least one identifying clue.
     */
    fun score(bill: Bill, t: Txn): Int {
        if (!t.isDebit) return 0
        val day = java.time.Instant.ofEpochMilli(t.timestamp).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        val diff = ChronoUnit.DAYS.between(bill.dueDate, day)
        if (diff < -25 || diff > 20) return 0

        val exact = abs(t.amount - bill.amount) <= max(1.0, bill.amount * 0.005)
        val ccRange = bill.kind == BillKind.CREDIT_CARD && bill.minDue != null &&
            t.amount >= bill.minDue - 1 && t.amount <= bill.amount + 1
        if (!exact && !ccRange) return 0

        val text = (t.merchant + " " + t.body).lowercase()
        var s = 3
        bill.hint?.split(',')?.map { it.trim() }?.filter { it.length >= 3 }?.let { hints ->
            if (hints.any { Regex("(?<![a-z0-9])${Regex.escape(it)}(?![a-z0-9])").containsMatchIn(text) }) s += 3
        }
        bill.payeeVpa?.substringBefore('@')?.lowercase()?.takeIf { it.length >= 3 }?.let { if (text.contains(it)) s += 3 }
        // Biller's name in the payment SMS (not for cards/loans, whose name is just the bank's).
        if (bill.kind != BillKind.CREDIT_CARD && bill.kind != BillKind.LOAN_EMI) {
            val word = bill.name.lowercase().substringBefore(' ')
            if (word.length >= 4 && Regex("(?<![a-z0-9])${Regex.escape(word)}").containsMatchIn(text)) s += 2
        }
        bill.last4?.let { if (text.contains(it)) s += 3 }
        if (bill.autopay && autopayDebitRx.containsMatchIn(text)) s += 2
        if (bill.kind == BillKind.CREDIT_CARD && cardPaymentRx.containsMatchIn(text)) s += 2
        if (t.category == bill.kind.txnCategory) s += 1
        if (exact) s += 1
        return s
    }

    private const val MIN_SCORE = 6

    /** Bills a payment could belong to: unpaid, or paid via "Pay now" but not yet linked to an SMS. */
    private fun candidates(db: Db): List<Bill> {
        val recent = System.currentTimeMillis() - 5 * 86_400_000L
        return db.bills().filter { !it.paid || (it.paidTxnId == null && (it.paidTs ?: 0) > recent) }
    }

    /** Called for every new debit transaction. Returns the bill it paid, if any. */
    fun onNewTxn(ctx: Context, t: Txn): Bill? {
        if (!t.isDebit || t.id == 0L) return null
        val db = Db.get(ctx)
        val best = candidates(db).map { it to score(it, t) }.filter { it.second >= MIN_SCORE }.maxByOrNull { it.second }?.first
        if (best != null) return markPaid(ctx, best, t)
        if (autopayDebitRx.containsMatchIn(t.body) && t.category != Categories.SELF) trackAutopay(ctx, t)
        return null
    }

    /** Called when a bill is created or edited: maybe it was already paid. */
    fun matchExisting(ctx: Context, bill: Bill): Bill {
        if (bill.paid) return bill
        val db = Db.get(ctx)
        val from = bill.dueDate.minusDays(25).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val to = bill.dueDate.plusDays(21).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val linked = db.bills().mapNotNull { it.paidTxnId }.toSet()
        val t = db.txnsBetween(from, to).filter { it.id !in linked }
            .map { it to score(bill, it) }.filter { it.second >= MIN_SCORE }.maxByOrNull { it.second }?.first
            ?: return bill
        return markPaid(ctx, bill, t)
    }

    /** Mark paid, link the expense (category from the bill) and schedule the next occurrence. */
    fun markPaid(ctx: Context, bill: Bill, t: Txn?): Bill {
        val db = Db.get(ctx)
        val paid = bill.copy(paid = true, paidTs = bill.paidTs ?: t?.timestamp ?: System.currentTimeMillis(), paidTxnId = t?.id ?: bill.paidTxnId)
        db.saveBill(paid)
        if (t != null) {
            val cat = bill.kind.txnCategory
            val updated = if (cat == Categories.SELF) {
                // Card bill payments move money to your card; the card spends are already counted.
                t.copy(category = Categories.SELF, ignored = true, note = t.note.ifBlank { "Bill: ${bill.name}" })
            } else {
                t.copy(
                    category = if (t.category in setOf(Categories.OTHERS, "Transfers", "Bills")) cat else t.category,
                    note = t.note.ifBlank { "Bill: ${bill.name}" },
                )
            }
            if (updated != t) db.update(updated)
        }
        createNext(db, paid)
        return paid
    }

    fun markUnpaid(ctx: Context, bill: Bill) {
        Db.get(ctx).saveBill(bill.copy(paid = false, paidTs = null, paidTxnId = null))
    }

    /** Next month's / year's occurrence of a recurring bill (unless one exists already). */
    fun createNext(db: Db, bill: Bill) {
        if (bill.recurrence == Recurrence.NONE) return
        val next = bill.dueDate.plusMonths(bill.recurrence.months.toLong())
        val exists = db.billsInSeries(bill.series).any { it.id != bill.id && !it.dueDate.isBefore(next.minusDays(10)) }
        if (exists) return
        db.saveBill(
            bill.copy(
                id = 0, dueDate = next, paid = false, paidTs = null, paidTxnId = null, notified = 0, smsKey = null,
                minDue = null,
            )
        )
    }

    // ------------------------------------------------------------ from SMS

    /** Bill / statement / upcoming auto-debit SMS → create or refresh the pending bill. Returns true if handled. */
    fun onBillSms(ctx: Context, sender: String, body: String, received: LocalDate, smsKey: String): Boolean {
        val d = BillParser.parse(sender, body, received) ?: return false
        if (d.dueDate.isBefore(LocalDate.now().minusDays(45))) return true // old reminder: nothing to do
        val db = Db.get(ctx)
        val series = db.billsInSeries(d.series)
        // Same bill again (reminder, or statement replacing our auto-created next occurrence)?
        val open = series.firstOrNull { abs(ChronoUnit.DAYS.between(it.dueDate, d.dueDate)) <= 12 }
        val bill = if (open != null) {
            if (open.paid) return true
            open.copy(
                amount = d.amount, minDue = d.minDue ?: open.minDue, dueDate = d.dueDate,
                autopay = open.autopay || d.autopay, smsKey = smsKey,
                notified = if (open.dueDate == d.dueDate) open.notified else 0,
            )
        } else {
            val template = series.lastOrNull()
            Bill(
                series = d.series, name = template?.name ?: d.name, kind = d.kind, amount = d.amount, minDue = d.minDue,
                dueDate = d.dueDate, recurrence = template?.recurrence ?: d.recurrence, autopay = d.autopay || template?.autopay == true,
                payeeVpa = template?.payeeVpa, last4 = d.last4, hint = template?.hint ?: d.hint, source = "sms", smsKey = smsKey,
            )
        }
        val id = db.saveBill(bill)
        val saved = if (bill.id == 0L) (if (id > 0) bill.copy(id = id) else return true) else bill
        matchExisting(ctx, saved)
        return true
    }

    /** A NACH / autopay debit with no bill yet: track it as a paid autopay bill and expect it next month. */
    private fun trackAutopay(ctx: Context, t: Txn) {
        val db = Db.get(ctx)
        val kind = when (t.category) {
            "EMI/Loan" -> BillKind.LOAN_EMI
            "Insurance" -> BillKind.INSURANCE
            "Entertainment" -> BillKind.SUBSCRIPTION
            "Investment" -> return // SIPs are investments, not bills
            "Bills" -> BillKind.OTHER
            "Rent" -> BillKind.RENT
            else -> BillKind.OTHER
        }
        val day = java.time.Instant.ofEpochMilli(t.timestamp).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        val series = "auto:${t.merchant.lowercase()}:${t.account ?: ""}"
        val bill = Bill(
            series = series, name = t.merchant, kind = kind, amount = t.amount, dueDate = day,
            recurrence = Recurrence.MONTHLY, paid = true, paidTs = t.timestamp, paidTxnId = t.id, autopay = true,
            hint = t.merchant.lowercase().takeIf { it.length >= 4 }, source = "autopay",
        )
        val id = db.saveBill(bill)
        if (id > 0) createNext(db, bill.copy(id = id))
    }

    fun newSeries() = "manual:" + UUID.randomUUID().toString()
}
