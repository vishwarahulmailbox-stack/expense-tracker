package com.expensetracker.sms

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** Reminders 3 days before, 1 day before and on the due date, plus one overdue alert. */
object BillReminders {
    private const val WORK = "bill-reminders"

    /** Runs about every 12 hours, first around 9 AM. Survives reboots (WorkManager). */
    fun schedule(ctx: Context) {
        val now = LocalDateTime.now()
        var first = now.toLocalDate().atTime(LocalTime.of(9, 0))
        if (!first.isAfter(now)) first = first.plusHours(12)
        val req = PeriodicWorkRequestBuilder<BillReminderWorker>(12, TimeUnit.HOURS)
            .setInitialDelay(Duration.between(now, first).toMinutes(), TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /** Which reminder (bit flag) is due for a bill today, or null. Pure, unit-tested. */
    fun reminderFor(bill: Bill, today: LocalDate): Int? {
        if (bill.paid) return null
        val days = bill.daysLeft(today)
        val flag = when {
            days in 2..3 -> 1
            days == 1L -> 2
            days == 0L -> 4
            days < 0 && days >= -30 -> 8
            else -> return null
        }
        return if (bill.notified and flag == 0) flag else null
    }

    fun checkNow(ctx: Context) {
        val db = Db.get(ctx)
        val today = LocalDate.now()
        db.bills().forEach { bill ->
            val flag = reminderFor(bill, today) ?: return@forEach
            val days = bill.daysLeft(today)
            val amount = fmt(bill.amount)
            val (title, text) = when {
                bill.autopay && days >= 0 -> "⏰ ${bill.name}: auto-debit ${if (days == 0L) "today" else "in $days day${if (days == 1L) "" else "s"}"}" to
                    "$amount will be auto-debited. Keep enough balance in your account."
                days > 1 -> "${bill.name} due in $days days" to "$amount due on ${bill.dueDate.dayOfMonth} ${bill.dueDate.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)}. Tap Pay now to pay via UPI."
                days == 1L -> "${bill.name} due tomorrow" to "$amount due tomorrow. Pay now to avoid late fees."
                days == 0L -> "⚠️ ${bill.name} due today" to "$amount is due today."
                else -> "❗ ${bill.name} is overdue" to "$amount was due ${-days} day${if (days == -1L) "" else "s"} ago. Pay now to avoid more charges."
            }
            Notifier.billReminder(ctx, bill, title, text)
            // Mark this reminder and the earlier ones as sent, so a late check doesn't send old ones.
            db.saveBill(bill.copy(notified = bill.notified or flag or (flag - 1)))
        }
    }
}

class BillReminderWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        runCatching { BillReminders.checkNow(applicationContext) }
        return Result.success()
    }
}
