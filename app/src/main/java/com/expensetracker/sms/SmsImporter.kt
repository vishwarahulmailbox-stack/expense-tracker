package com.expensetracker.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

object SmsImporter {

    /** Same SMS -> same key, whether it came live or from an inbox scan. */
    private fun key(addr: String, body: String): String {
        val b = body.trim()
        return SmsParser.senderName(addr).uppercase() + "|" + b.hashCode() + "|" + b.length
    }

    /** Parses and saves one SMS. Returns true if a new transaction was saved. */
    fun handle(
        ctx: Context,
        addr: String,
        body: String,
        ts: Long,
        notify: Boolean,
        names: List<String> = SelfTransfer.myNames(ctx),
        known: Set<String> = Db.get(ctx).knownAccounts(),
    ): Boolean {
        val p = SmsParser.parse(addr, body) ?: return false
        val db = Db.get(ctx)
        val category = Categorizer.categorize(p.merchant, body, p.isDebit, db.ruleFor(p.merchant))
        var t = Txn(
            smsKey = key(addr, body),
            amount = p.amount,
            isDebit = p.isDebit,
            merchant = p.merchant,
            account = p.account,
            category = category,
            timestamp = ts,
            body = body,
            bank = p.bank,
            acctType = p.acctType,
            balance = p.balance,
        )
        if (category == Categories.SELF || SelfTransfer.byContent(t.merchant, body, t.account, names, known)) {
            t = SelfTransfer.mark(t)
        }
        val id = db.insert(t)
        if (id == -1L) return false
        t = t.copy(id = id)
        if (t.category != Categories.SELF) {
            SelfTransfer.findPair(db, t)?.let { pair ->
                db.update(SelfTransfer.mark(t))
                db.update(SelfTransfer.mark(pair))
                t = SelfTransfer.mark(t)
            }
        }
        if (notify && t.category != Categories.SELF) {
            Notifier.newTxn(ctx, t)
            if (t.isDebit) BudgetAlerts.onNewDebit(ctx, t)
        }
        return true
    }

    /** Scan existing inbox messages from the last [days] days. */
    fun importInbox(ctx: Context, days: Int): Int {
        val since = System.currentTimeMillis() - days * 86_400_000L
        var count = 0
        val db = Db.get(ctx).writableDatabase
        val names = SelfTransfer.myNames(ctx)
        try {
            ctx.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                "${Telephony.Sms.DATE} > ?",
                arrayOf(since.toString()),
                "${Telephony.Sms.DATE} ASC",
            )?.use { c ->
                val ia = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val ib = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val id = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
                db.beginTransaction()
                try {
                    while (c.moveToNext()) {
                        val addr = c.getString(ia) ?: continue
                        val body = c.getString(ib) ?: continue
                        if (handle(ctx, addr, body, c.getLong(id), notify = false, names = names, known = Db.get(ctx).knownAccounts())) count++
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
        } catch (e: SecurityException) {
            return 0
        }
        // Second pass: accounts seen later in the scan can reveal earlier self transfers.
        SelfTransfer.scanAll(ctx)
        return count
    }
}

/** Catches new SMS as they arrive, even when the app is closed. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val app = context.applicationContext
        val pending = goAsync()
        Thread {
            try {
                msgs.filterNotNull()
                    .groupBy { it.originatingAddress ?: "" }
                    .forEach { (addr, parts) ->
                        val body = parts.joinToString("") { it.messageBody ?: "" }
                        SmsImporter.handle(app, addr, body, System.currentTimeMillis(), notify = true)
                    }
                DataBus.bump()
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }.start()
    }
}
