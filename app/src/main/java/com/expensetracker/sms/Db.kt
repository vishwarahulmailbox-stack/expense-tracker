package com.expensetracker.sms

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.time.YearMonth
import java.time.ZoneId

/** Tells the UI that data changed (new SMS, edits...). */
object DataBus {
    val version = MutableStateFlow(0)
    fun bump() = version.update { it + 1 }
}

class Db private constructor(ctx: Context) : SQLiteOpenHelper(ctx, "expenses.db", null, 3) {

    companion object {
        @Volatile private var inst: Db? = null
        fun get(ctx: Context): Db = inst ?: synchronized(this) {
            inst ?: Db(ctx.applicationContext).also { inst = it }
        }

        fun monthRange(ym: YearMonth): Pair<Long, Long> {
            val z = ZoneId.systemDefault()
            val start = ym.atDay(1).atStartOfDay(z).toInstant().toEpochMilli()
            val end = ym.plusMonths(1).atDay(1).atStartOfDay(z).toInstant().toEpochMilli()
            return start to end
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE txns(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sms_key TEXT UNIQUE,
                amount REAL NOT NULL,
                is_debit INTEGER NOT NULL,
                merchant TEXT NOT NULL,
                account TEXT,
                category TEXT NOT NULL,
                ts INTEGER NOT NULL,
                body TEXT NOT NULL,
                note TEXT NOT NULL DEFAULT '',
                ignored INTEGER NOT NULL DEFAULT 0)"""
        )
        db.execSQL("CREATE INDEX idx_ts ON txns(ts)")
        db.execSQL("CREATE TABLE budgets(category TEXT PRIMARY KEY, amount REAL NOT NULL)")
        db.execSQL("CREATE TABLE rules(merchant TEXT PRIMARY KEY, category TEXT NOT NULL)")
        upgradeToV2(db)
        upgradeToV3(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) upgradeToV2(db)
        if (oldVersion < 3) upgradeToV3(db)
    }

    /** v3: bank / account type / balance per transaction, account nicknames, smarter categories. */
    private fun upgradeToV3(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE txns ADD COLUMN bank TEXT")
        db.execSQL("ALTER TABLE txns ADD COLUMN acct_type TEXT")
        db.execSQL("ALTER TABLE txns ADD COLUMN balance REAL")
        db.execSQL("CREATE TABLE IF NOT EXISTS accounts(key TEXT PRIMARY KEY, nickname TEXT)")
        Categories.builtins.forEach { db.insertWithOnConflict("categories", null, it.toCv(), SQLiteDatabase.CONFLICT_IGNORE) }

        val rules = HashMap<String, String>()
        db.rawQuery("SELECT merchant, category FROM rules", null).use { c -> while (c.moveToNext()) rules[c.getString(0)] = c.getString(1) }
        val redo = setOf(Categories.OTHERS, "Transfers", Categories.OTHER_INCOME)

        db.rawQuery("SELECT id, sms_key, body, account, merchant, category, is_debit FROM txns", null).use { c ->
            while (c.moveToNext()) {
                val key = c.getString(1) ?: continue // manual entries
                val sender = key.substringBefore('|')
                val body = c.getString(2)
                val info = SmsParser.accountInfo(sender, body, c.getString(3))
                val cv = ContentValues().apply {
                    put("bank", info.bank)
                    put("acct_type", info.type?.name)
                    put("balance", info.balance)
                }
                // Give "Others" & co. a second chance with the smarter categoriser.
                val merchant = c.getString(4)
                val category = c.getString(5)
                if (category in redo && rules[merchant.lowercase()] == null) {
                    val better = Categorizer.categorize(merchant, body, c.getInt(6) == 1, null)
                    if (better != category) {
                        cv.put("category", better)
                        if (better == Categories.SELF) cv.put("ignored", 1)
                    }
                }
                db.update("txns", cv, "id=?", arrayOf(c.getLong(0).toString()))
            }
        }
    }

    /** v2: categories table (built-in + custom) and income categories. */
    private fun upgradeToV2(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS categories(
                name TEXT PRIMARY KEY,
                type INTEGER NOT NULL,
                icon TEXT NOT NULL,
                color INTEGER NOT NULL,
                builtin INTEGER NOT NULL,
                sort INTEGER NOT NULL)"""
        )
        Categories.builtins.forEach { db.insertWithOnConflict("categories", null, it.toCv(), SQLiteDatabase.CONFLICT_IGNORE) }
        // Old single "Income" category -> specific income categories
        db.rawQuery("SELECT id, merchant, body FROM txns WHERE category=?", arrayOf(Categories.LEGACY_INCOME)).use { c ->
            while (c.moveToNext()) {
                val cat = Categorizer.incomeCategory(c.getString(1), c.getString(2))
                db.update("txns", ContentValues().apply { put("category", cat) }, "id=?", arrayOf(c.getLong(0).toString()))
            }
        }
        db.update("rules", ContentValues().apply { put("category", Categories.OTHER_INCOME) }, "category=?", arrayOf(Categories.LEGACY_INCOME))
    }

    private fun CategoryDef.toCv() = ContentValues().apply {
        put("name", name)
        put("type", type.ordinal)
        put("icon", icon)
        put("color", color)
        put("builtin", if (builtin) 1 else 0)
        put("sort", sort)
    }

    fun categories(): List<CategoryDef> =
        readableDatabase.rawQuery("SELECT name, type, icon, color, builtin, sort FROM categories", null).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        CategoryDef(
                            name = c.getString(0),
                            type = CatType.entries.getOrElse(c.getInt(1)) { CatType.EXPENSE },
                            icon = c.getString(2),
                            color = c.getLong(3),
                            builtin = c.getInt(4) == 1,
                            sort = c.getInt(5),
                        )
                    )
                }
            }
        }

    /** Insert or update a category. Renaming moves its transactions, budgets and rules along. */
    fun saveCategory(def: CategoryDef, oldName: String?) {
        val w = writableDatabase
        w.beginTransaction()
        try {
            if (oldName != null && oldName != def.name) {
                w.delete("categories", "name=?", arrayOf(oldName))
                val cv = ContentValues().apply { put("category", def.name) }
                w.update("txns", cv, "category=?", arrayOf(oldName))
                w.update("budgets", cv, "category=?", arrayOf(oldName))
                w.update("rules", cv, "category=?", arrayOf(oldName))
            }
            w.insertWithOnConflict("categories", null, def.toCv(), SQLiteDatabase.CONFLICT_REPLACE)
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
    }

    /** Deletes a custom category; its transactions go to Others / Other income. */
    fun deleteCategory(def: CategoryDef) {
        if (def.builtin) return
        val w = writableDatabase
        w.beginTransaction()
        try {
            w.delete("categories", "name=?", arrayOf(def.name))
            w.update("txns", ContentValues().apply { put("category", Categories.OTHERS) }, "category=? AND is_debit=1", arrayOf(def.name))
            w.update("txns", ContentValues().apply { put("category", Categories.OTHER_INCOME) }, "category=? AND is_debit=0", arrayOf(def.name))
            w.delete("budgets", "category=?", arrayOf(def.name))
            w.delete("rules", "category=?", arrayOf(def.name))
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
    }

    fun txnsBetween(start: Long, end: Long): List<Txn> =
        readableDatabase.rawQuery("SELECT * FROM txns WHERE ts>=? AND ts<=?", arrayOf(start.toString(), end.toString())).use { c ->
            buildList { while (c.moveToNext()) add(c.toTxn()) }
        }

    fun nicknames(): Map<String, String> =
        readableDatabase.rawQuery("SELECT key, nickname FROM accounts WHERE nickname IS NOT NULL", null).use { c ->
            buildMap { while (c.moveToNext()) put(c.getString(0), c.getString(1)) }
        }

    fun setNickname(key: String, nickname: String?) {
        val cv = ContentValues().apply { put("key", key); put("nickname", nickname?.trim()?.ifBlank { null }) }
        writableDatabase.insertWithOnConflict("accounts", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** Every account / card seen in SMS, with this month's activity and the latest known balance. */
    fun accounts(ym: YearMonth): List<Account> {
        val (start, end) = monthRange(ym)
        val names = nicknames()
        return allTxns().filter { it.accountKey != null }.groupBy { it.accountKey!! }.map { (key, list) ->
            val latest = list.maxBy { it.timestamp }
            val withBal = list.filter { it.balance != null }.maxByOrNull { it.timestamp }
            val type = list.mapNotNull { it.acctType }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: AcctType.BANK
            val month = list.filter { it.timestamp in start until end && !it.ignored }
            Account(
                key = key,
                bank = latest.bank ?: "Account",
                last4 = latest.account!!,
                type = type,
                nickname = names[key],
                balance = withBal?.balance,
                balanceTs = withBal?.timestamp,
                monthSpent = month.filter { it.isDebit }.sumOf { it.amount },
                monthReceived = month.filter { !it.isDebit }.sumOf { it.amount },
                monthCount = list.count { it.timestamp in start until end },
                totalCount = list.size,
                lastTs = latest.timestamp,
            )
        }.sortedWith(compareBy<Account>({ it.type.ordinal }, { -it.lastTs }))
    }

    /** Last digits of every account / card seen in your SMS. */
    fun knownAccounts(): Set<String> =
        readableDatabase.rawQuery("SELECT DISTINCT account FROM txns WHERE account IS NOT NULL", null).use { c ->
            buildSet { while (c.moveToNext()) add(c.getString(0).takeLast(4)) }
        }

    private fun Txn.toCv() = ContentValues().apply {
        put("sms_key", smsKey)
        put("amount", amount)
        put("is_debit", if (isDebit) 1 else 0)
        put("merchant", merchant)
        put("account", account)
        put("category", category)
        put("ts", timestamp)
        put("body", body)
        put("note", note)
        put("ignored", if (ignored) 1 else 0)
        put("bank", bank)
        put("acct_type", acctType?.name)
        put("balance", balance)
    }

    private fun Cursor.toTxn() = Txn(
        id = getLong(getColumnIndexOrThrow("id")),
        smsKey = getString(getColumnIndexOrThrow("sms_key")),
        amount = getDouble(getColumnIndexOrThrow("amount")),
        isDebit = getInt(getColumnIndexOrThrow("is_debit")) == 1,
        merchant = getString(getColumnIndexOrThrow("merchant")),
        account = getString(getColumnIndexOrThrow("account")),
        category = getString(getColumnIndexOrThrow("category")),
        timestamp = getLong(getColumnIndexOrThrow("ts")),
        body = getString(getColumnIndexOrThrow("body")),
        note = getString(getColumnIndexOrThrow("note")),
        ignored = getInt(getColumnIndexOrThrow("ignored")) == 1,
        bank = getString(getColumnIndexOrThrow("bank")),
        acctType = getString(getColumnIndexOrThrow("acct_type"))?.let { n -> AcctType.entries.firstOrNull { it.name == n } },
        balance = getColumnIndexOrThrow("balance").let { if (isNull(it)) null else getDouble(it) },
    )

    /** Returns -1 if this SMS was already saved. */
    fun insert(t: Txn): Long =
        writableDatabase.insertWithOnConflict("txns", null, t.toCv(), SQLiteDatabase.CONFLICT_IGNORE)

    fun update(t: Txn) {
        writableDatabase.update("txns", t.toCv(), "id=?", arrayOf(t.id.toString()))
    }

    fun delete(id: Long) {
        writableDatabase.delete("txns", "id=?", arrayOf(id.toString()))
    }

    fun txnsInMonth(ym: YearMonth): List<Txn> {
        val (start, end) = monthRange(ym)
        return readableDatabase.rawQuery(
            "SELECT * FROM txns WHERE ts>=? AND ts<? ORDER BY ts DESC",
            arrayOf(start.toString(), end.toString()),
        ).use { c ->
            buildList { while (c.moveToNext()) add(c.toTxn()) }
        }
    }

    fun allTxns(): List<Txn> =
        readableDatabase.rawQuery("SELECT * FROM txns ORDER BY ts DESC", null).use { c ->
            buildList { while (c.moveToNext()) add(c.toTxn()) }
        }

    fun clearAll() {
        writableDatabase.delete("txns", null, null)
        writableDatabase.delete("budgets", null, null)
        writableDatabase.delete("rules", null, null)
        writableDatabase.delete("accounts", null, null)
    }

    fun budgets(): Map<String, Double> =
        readableDatabase.rawQuery("SELECT category, amount FROM budgets", null).use { c ->
            buildMap { while (c.moveToNext()) put(c.getString(0), c.getDouble(1)) }
        }

    fun setBudget(category: String, amount: Double) {
        if (amount <= 0.0) {
            writableDatabase.delete("budgets", "category=?", arrayOf(category))
        } else {
            val cv = ContentValues().apply { put("category", category); put("amount", amount) }
            writableDatabase.insertWithOnConflict("budgets", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    fun ruleFor(merchant: String): String? =
        readableDatabase.rawQuery("SELECT category FROM rules WHERE merchant=?", arrayOf(merchant.lowercase()))
            .use { c -> if (c.moveToFirst()) c.getString(0) else null }

    /** Remember merchant -> category and apply it to all past transactions of that merchant. */
    fun setRule(merchant: String, category: String) {
        val cv = ContentValues().apply { put("merchant", merchant.lowercase()); put("category", category) }
        writableDatabase.insertWithOnConflict("rules", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        val up = ContentValues().apply { put("category", category) }
        writableDatabase.update("txns", up, "LOWER(merchant)=?", arrayOf(merchant.lowercase()))
    }
}
