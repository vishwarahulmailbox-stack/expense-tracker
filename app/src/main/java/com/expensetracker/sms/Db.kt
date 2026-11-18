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

class Db private constructor(ctx: Context) : SQLiteOpenHelper(ctx, "expenses.db", null, 1) {

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
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

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
