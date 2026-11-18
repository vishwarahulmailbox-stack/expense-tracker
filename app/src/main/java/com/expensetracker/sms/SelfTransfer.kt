package com.expensetracker.sms

import android.content.Context
import kotlin.math.abs

/**
 * Finds money moved between the user's own accounts (savings → another bank,
 * card bill payments, sweeps, FDs). These are marked "Self transfer" and
 * excluded from spent/income totals so they don't count twice.
 */
object SelfTransfer {
    private val IC = setOf(RegexOption.IGNORE_CASE)

    private val keywordRx = Regex(
        """\b(self[\s-]?transfer|self[\s-]?trf|to self|by self|from self|own a/?c|own account|own acct|""" +
            """sweep[\s-]?(?:in|out)|auto[\s-]?sweep|transfer(?:red)? to (?:your )?fd|fd (?:booked|opened|created))\b""",
        IC,
    )
    private val maskedRx = Regex("""[xX*]+\s?(\d{3,6})\b""")

    /** Time window for matching a debit with a credit of the same amount. */
    private const val PAIR_WINDOW_MS = 6 * 60 * 60 * 1000L

    private const val PREFS = "prefs"
    private const val KEY_NAMES = "my_names"

    fun myNames(ctx: Context): List<String> =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_NAMES, "").orEmpty()
            .split(',').map { normalize(it) }.filter { it.length >= 4 }

    fun namesText(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_NAMES, "").orEmpty()

    fun setNames(ctx: Context, text: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_NAMES, text.trim()).apply()
    }

    private fun normalize(s: String) = s.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    /** Detects a self transfer from the message itself. Pure function, unit-tested. */
    fun byContent(merchant: String, body: String, account: String?, myNames: List<String>, knownAccounts: Set<String>): Boolean {
        if (keywordRx.containsMatchIn(body)) return true

        val m = normalize(merchant)
        if (m.isNotEmpty() && myNames.any { name -> m.contains(name) || (m.length >= 6 && name.contains(m)) }) return true

        // Another of *your* accounts / cards is mentioned (e.g. "to A/c XX5678" or "towards card XX4321").
        val own = account?.takeLast(4)
        val mentioned = maskedRx.findAll(body).map { it.groupValues[1].takeLast(4) }.filter { it != own }.toSet()
        return mentioned.any { it in knownAccounts }
    }

    fun mark(t: Txn) = t.copy(category = Categories.SELF, ignored = true)

    /** A credit for a debit (or the other way round) of the same amount in a different account, close in time. */
    fun findPair(db: Db, t: Txn): Txn? {
        if (t.smsKey == null || t.account == null) return null
        return db.txnsBetween(t.timestamp - PAIR_WINDOW_MS, t.timestamp + PAIR_WINDOW_MS)
            .filter { o ->
                o.id != t.id && o.isDebit != t.isDebit && o.smsKey != null &&
                    o.account != null && o.account != t.account &&
                    abs(o.amount - t.amount) < 0.01 && o.category != Categories.SELF
            }
            .minByOrNull { abs(it.timestamp - t.timestamp) }
    }

    /**
     * Re-checks every transaction that isn't already excluded.
     * Returns how many were newly marked as self transfers.
     */
    fun scanAll(ctx: Context): Int {
        val db = Db.get(ctx)
        val names = myNames(ctx)
        val known = db.knownAccounts()
        var count = 0
        val all = db.allTxns().filter { !it.ignored && it.category != Categories.SELF && it.smsKey != null }
        val marked = HashSet<Long>()
        for (t in all) {
            if (t.id in marked) continue
            if (byContent(t.merchant, t.body, t.account, names, known)) {
                db.update(mark(t)); marked += t.id; count++
                continue
            }
            val pair = findPair(db, t)
            if (pair != null && pair.id !in marked && !pair.ignored) {
                db.update(mark(t)); db.update(mark(pair))
                marked += t.id; marked += pair.id
                count += 2
            }
        }
        return count
    }
}
