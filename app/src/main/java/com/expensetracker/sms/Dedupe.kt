package com.expensetracker.sms

import android.content.Context
import kotlin.math.abs

/**
 * Payment apps (PhonePe, Paytm, Amazon Pay…) often send their own "You paid ₹349 to Swiggy"
 * SMS for a payment the bank also reports. The bank SMS is kept as the real transaction;
 * the app SMS is only used to improve its merchant name and category, then dropped.
 */
object Dedupe {
    private const val WINDOW_MS = 30 * 60 * 1000L
    private val bankCodeRx = Regex("""^[A-Z0-9]{4,8}$""")
    private val vagueCategories = setOf(Categories.OTHERS, "Transfers", Categories.OTHER_INCOME)

    private fun same(a: Txn, b: Txn) =
        a.isDebit == b.isDebit && abs(a.amount - b.amount) < 0.01 && abs(a.timestamp - b.timestamp) <= WINDOW_MS

    /** The bank's record of the payment an app SMS describes, if it is already saved. */
    fun bankMatch(db: Db, app: Txn): Txn? =
        db.txnsBetween(app.timestamp - WINDOW_MS, app.timestamp + WINDOW_MS)
            .filter { it.source != "app" && it.smsKey != null && it.id != app.id && same(it, app) }
            .minByOrNull { abs(it.timestamp - app.timestamp) }

    /** App copies of a bank transaction that were saved before the bank SMS arrived. */
    fun appCopies(db: Db, bank: Txn): List<Txn> =
        db.txnsBetween(bank.timestamp - WINDOW_MS, bank.timestamp + WINDOW_MS)
            .filter { it.source == "app" && it.id != bank.id && same(it, bank) }

    /** Bank SMS often only say "HDFCBK" or a VPA; take the app's clearer merchant/category. */
    fun merge(db: Db, bank: Txn, app: Txn): Txn {
        val bankVague = bankCodeRx.matches(bank.merchant) || bank.merchant.all { it.isDigit() } ||
            bank.merchant.equals(bank.bank, true)
        var merged = bank
        if (bankVague && !bankCodeRx.matches(app.merchant)) merged = merged.copy(merchant = app.merchant)
        if (bank.category in vagueCategories && app.category !in vagueCategories && db.ruleFor(bank.merchant) == null) {
            merged = merged.copy(category = app.category)
        }
        if (merged != bank) db.update(merged)
        return merged
    }

    /** One-time clean-up of duplicates already saved. Returns how many app copies were removed. */
    fun scanAll(ctx: Context): Int {
        val db = Db.get(ctx)
        var removed = 0
        db.allTxns().filter { it.source == "app" }.forEach { app ->
            val bank = bankMatch(db, app) ?: return@forEach
            merge(db, bank, app)
            db.deleteTxn(app.id)
            removed++
        }
        return removed
    }
}
