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
        // Bill / statement / upcoming auto-debit? (OTPs and personal messages are ignored inside.)
        val isBill = runCatching { BillEngine.onBillSms(ctx, addr, body, toLocalDate(ts), key(addr, body)) }.getOrDefault(false)
        when (val r = SmsParser.classify(addr, body)) {
            is ParseResult.Skip -> {
                // OTPs and personal messages are never stored. Others are kept for review if they mention money.
                if (!isBill && r.reason.keep && SmsParser.hasAmount(body)) {
                    Db.get(ctx).addSkipped(key(addr, body), addr, body, ts, r.reason)
                }
                return false
            }
            is ParseResult.Ok -> return save(ctx, r.txn, addr, body, ts, notify, names, known)
        }
    }

    /** "Add as transaction" on a skipped message. */
    fun addSkipped(ctx: Context, s: SkippedSms): Boolean {
        val r = SmsParser.classify(s.sender, s.body, lenient = true)
        val ok = r is ParseResult.Ok && save(
            ctx, r.txn, s.sender, s.body, s.timestamp, notify = false,
            names = SelfTransfer.myNames(ctx), known = Db.get(ctx).knownAccounts(), force = true,
        )
        if (ok) Db.get(ctx).deleteSkipped(s.key)
        return ok
    }

    private fun save(
        ctx: Context,
        p: ParsedTxn,
        addr: String,
        body: String,
        ts: Long,
        notify: Boolean,
        names: List<String>,
        known: Set<String>,
        force: Boolean = false,
    ): Boolean {
        val db = Db.get(ctx)
        val rule = db.ruleFor(p.merchant)
        var category = Categorizer.categorize(p.merchant, body, p.isDebit, rule)
        // Rules found nothing: ask the on-device models (merchant name + SMS text; nothing leaves the phone).
        var aiConf: Double? = null
        if (p.isDebit && rule == null && category == Categories.OTHERS) {
            runCatching { CategoryAI.suggest(ctx, p.merchant, body) }.getOrNull()?.let { (cat, conf) ->
                category = cat
                aiConf = conf
            }
        }
        var t = Txn(
            smsKey = key(addr, body),
            amount = p.amount,
            isDebit = p.isDebit,
            merchant = p.merchant,
            account = p.account,
            category = category,
            timestamp = ts,
            body = body,
            bank = if (p.fromApp) null else p.bank,
            acctType = if (p.fromApp) null else p.acctType,
            balance = if (p.fromApp) null else p.balance,
            source = if (p.fromApp) "app" else "bank",
            aiConf = aiConf,
        )

        // A payment-app SMS for a payment the bank already reported: just improve the bank's entry.
        if (p.fromApp && !force) {
            Dedupe.bankMatch(db, t)?.let { bank ->
                Dedupe.merge(db, bank, t)
                return false
            }
        }

        if (category == Categories.SELF || SelfTransfer.byContent(t.merchant, body, t.account, names, known)) {
            t = SelfTransfer.mark(t)
        }
        val id = db.insert(t)
        if (id == -1L) return false
        t = t.copy(id = id)

        // The bank SMS arrived after an app SMS for the same payment: merge and drop the app copy.
        if (!p.fromApp) {
            Dedupe.appCopies(db, t).forEach { app ->
                t = Dedupe.merge(db, t, app)
                db.repointBillTxn(app.id, t.id)
                db.deleteTxn(app.id)
            }
        }

        if (t.category != Categories.SELF) {
            SelfTransfer.findPair(db, t)?.let { pair ->
                db.update(SelfTransfer.mark(t))
                db.update(SelfTransfer.mark(pair))
                t = SelfTransfer.mark(t)
            }
        }
        // Did this payment settle a bill (incl. autopay / NACH)?
        val paidBill = if (t.isDebit) runCatching { BillEngine.onNewTxn(ctx, t) }.getOrNull() else null
        if (paidBill != null) {
            db.txn(t.id)?.let { t = it }
            if (notify) Notifier.billPaid(ctx, paidBill, t)
        }
        // App SMS don't notify: the bank SMS that usually follows will.
        if (notify && !p.fromApp && t.category != Categories.SELF) {
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
        Dedupe.scanAll(ctx)
        Db.get(ctx).pruneSkipped(maxOf(400, days))
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
