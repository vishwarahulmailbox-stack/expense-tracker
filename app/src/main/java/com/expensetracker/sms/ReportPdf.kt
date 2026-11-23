package com.expensetracker.sms

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A simple A4 monthly report made on the phone with Android's PdfDocument (no library, no internet). */
object ReportPdf {
    private const val W = 595
    private const val H = 842
    private const val M = 40f

    fun write(ctx: Context, month: YearMonth, txns: List<Txn>, budget: Double?, includeIncome: Boolean, accountLabel: String?): File {
        val doc = PdfDocument()
        val title = Paint().apply { textSize = 20f; typeface = Typeface.DEFAULT_BOLD; color = Color.rgb(20, 108, 67); isAntiAlias = true }
        val h2 = Paint().apply { textSize = 13f; typeface = Typeface.DEFAULT_BOLD; color = Color.BLACK; isAntiAlias = true }
        val body = Paint().apply { textSize = 10f; color = Color.rgb(40, 40, 40); isAntiAlias = true }
        val muted = Paint().apply { textSize = 9f; color = Color.rgb(110, 110, 110); isAntiAlias = true }
        val right = Paint(body).apply { textAlign = Paint.Align.RIGHT }
        val bar = Paint().apply { color = Color.rgb(20, 108, 67) }
        val track = Paint().apply { color = Color.rgb(230, 236, 232) }

        var pageNo = 0
        var page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, ++pageNo).create())
        var c = page.canvas
        var y = M + 10

        fun newPageIfNeeded(need: Float) {
            if (y + need > H - M) {
                doc.finishPage(page)
                page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, ++pageNo).create())
                c = page.canvas
                y = M
            }
        }

        val monthName = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
        c.drawText("Expense report · $monthName", M, y, title); y += 18
        c.drawText((accountLabel ?: "All accounts") + " · made on this phone by Expense Tracker", M, y, muted); y += 26

        val counted = txns.filter { !it.ignored && it.category != Categories.SELF }
        val debits = counted.filter { it.isDebit }
        val spent = debits.sumOf { it.amount }
        val income = counted.filter { !it.isDebit }.sumOf { it.amount }

        c.drawText("Summary", M, y, h2); y += 18
        listOfNotNull(
            "Total spent" to fmt(spent),
            ("Income" to fmt(income)).takeIf { includeIncome },
            ("Net" to fmt(income - spent)).takeIf { includeIncome },
            budget?.let { "Budget" to "${fmt(it)} (${Math.round(spent / it * 100)}% used)" },
            "Payments" to "${debits.size}",
        ).forEach { (k, v) ->
            c.drawText(k, M, y, body); c.drawText(v, W - M, y, right); y += 15
        }
        y += 14

        c.drawText("By category", M, y, h2); y += 16
        val cats = debits.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }.entries.sortedByDescending { it.value }
        cats.forEach { (cat, amt) ->
            newPageIfNeeded(18f)
            c.drawText(cat, M, y, body)
            c.drawText(fmt(amt), W - M, y, right)
            val x0 = M + 150; val x1 = W - M - 90
            c.drawRect(x0, y - 7, x1, y - 2, track)
            c.drawRect(x0, y - 7, x0 + ((x1 - x0) * (amt / spent.coerceAtLeast(1.0))).toFloat(), y - 2, bar)
            y += 16
        }
        y += 14

        newPageIfNeeded(60f)
        c.drawText("Top merchants", M, y, h2); y += 16
        Insights.merchants(debits).take(8).forEach { m ->
            newPageIfNeeded(16f)
            c.drawText("${m.name} · ${m.count}×", M, y, body); c.drawText(fmt(m.total), W - M, y, right); y += 15
        }
        y += 14

        newPageIfNeeded(60f)
        c.drawText("Transactions", M, y, h2); y += 16
        val df = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
        txns.sortedBy { it.timestamp }.forEach { t ->
            if (!t.isDebit && !includeIncome) return@forEach
            newPageIfNeeded(14f)
            c.drawText(toLocalDate(t.timestamp).format(df), M, y, muted)
            c.drawText(t.merchant.take(34), M + 50, y, body)
            c.drawText(t.category + if (t.ignored) " (not counted)" else "", M + 260, y, muted)
            c.drawText((if (t.isDebit) "−" else "+") + fmt(t.amount), W - M, y, right)
            y += 13
        }
        if (!includeIncome) {
            newPageIfNeeded(20f); y += 8
            c.drawText("Income is hidden in this report.", M, y, muted)
        }
        doc.finishPage(page)

        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "expense-report-$month.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }
}
