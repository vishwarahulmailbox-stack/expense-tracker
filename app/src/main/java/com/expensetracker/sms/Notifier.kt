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

    private fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_TXN, "New transactions", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_BUDGET, "Budget alerts", NotificationManager.IMPORTANCE_HIGH))
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
        val title = if (t.isDebit) "${fmt(t.amount)} spent · ${t.merchant}" else "${fmt(t.amount)} received · ${t.merchant}"
        show(ctx, CH_TXN, (t.id % 100000).toInt() + 10, title, "${t.category} · tap to change category")
    }

    fun budget(ctx: Context, id: Int, title: String, text: String) = show(ctx, CH_BUDGET, id, title, text)
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
