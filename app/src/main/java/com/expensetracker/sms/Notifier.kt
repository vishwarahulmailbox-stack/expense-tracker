package com.expensetracker.sms

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.time.YearMonth

object Notifier {
    private const val CH_TXN = "txns"
    private const val CH_BUDGET = "budget"
    private const val CH_BILLS = "bills"
    const val EXTRA_OPEN_BILLS = "open_bills"
    const val EXTRA_PAY_BILL = "pay_bill_id"

    private fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_TXN, "New transactions", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_BUDGET, "Budget alerts", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CH_BILLS, "Bill reminders", NotificationManager.IMPORTANCE_HIGH))
    }

    @SuppressLint("MissingPermission")
    private fun show(ctx: Context, channel: String, id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannels(ctx)
        val pi = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(id, n)
    }

    fun newTxn(ctx: Context, t: Txn) {
        val hideIncome = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE).getBoolean("hide_income", true)
        val title = when {
            t.isDebit -> "${fmt(t.amount)} spent · ${t.merchant}"
            hideIncome -> "Money received · ${t.merchant}" // amount stays behind the screen lock
            else -> "${fmt(t.amount)} received · ${t.merchant}"
        }
        show(ctx, CH_TXN, (t.id % 100000).toInt() + 10, title, "${t.category} · tap to change category")
    }

    fun budget(ctx: Context, id: Int, title: String, text: String) = show(ctx, CH_BUDGET, id, title, text)

    /** Bill reminder with a "Pay now" action (opens the app, which opens the UPI app). */
    @SuppressLint("MissingPermission")
    fun billReminder(ctx: Context, bill: Bill, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannels(ctx)
        val id = 50_000 + (bill.id % 100_000).toInt()
        val open = PendingIntent.getActivity(
            ctx, id, Intent(ctx, MainActivity::class.java).putExtra(EXTRA_OPEN_BILLS, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = NotificationCompat.Builder(ctx, CH_BILLS)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
        if (!bill.autopay) {
            val pay = PendingIntent.getActivity(
                ctx, id + 1, Intent(ctx, MainActivity::class.java).putExtra(EXTRA_PAY_BILL, bill.id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            b.addAction(0, "Pay now", pay)
        }
        NotificationManagerCompat.from(ctx).notify(id, b.build())
    }

    fun billPaid(ctx: Context, bill: Bill, t: Txn) =
        show(ctx, CH_BILLS, 60_000 + (bill.id % 100_000).toInt(), "✅ ${bill.name} paid", "${fmt(t.amount)} payment detected and marked as paid.")
}

object BudgetAlerts {
    fun onNewDebit(ctx: Context, t: Txn) {
        val db = Db.get(ctx)
        val budgets = db.budgets()
        if (budgets.isEmpty()) return
        val month = db.txnsInMonth(YearMonth.now()).filter { it.isDebit && !it.ignored }

        budgets[Categories.TOTAL]?.let { limit ->
            val now = month.sumOf { it.amount }
            check(ctx, 1, "Monthly budget", now - t.amount, now, limit)
        }
        budgets[t.category]?.let { limit ->
            val now = month.filter { it.category == t.category }.sumOf { it.amount }
            check(ctx, 2 + (t.category.hashCode() and 0xFFFF), "${t.category} budget", now - t.amount, now, limit)
        }
    }

    private fun check(ctx: Context, id: Int, name: String, before: Double, now: Double, limit: Double) {
        when {
            before < limit && now >= limit ->
                Notifier.budget(ctx, id, "⚠️ $name exceeded", "Spent ${fmt(now)} of ${fmt(limit)} this month (over by ${fmt(now - limit)}).")
            before < 0.8 * limit && now >= 0.8 * limit ->
                Notifier.budget(ctx, id, "$name: 80% used", "Spent ${fmt(now)} of ${fmt(limit)}. Only ${fmt(limit - now)} left this month.")
        }
    }
}
