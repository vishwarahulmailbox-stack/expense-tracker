package com.expensetracker.sms

import java.net.URLEncoder
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

/** What a bill is for. [txnCategory] is used for the expense when the bill gets paid. */
enum class BillKind(val label: String, val icon: String, val txnCategory: String, val defaultRecurrence: Recurrence) {
    CREDIT_CARD("Credit card", "card", Categories.SELF, Recurrence.MONTHLY),
    ELECTRICITY("Electricity", "bolt", "Bills", Recurrence.MONTHLY),
    LIC("LIC premium", "shield", "Insurance", Recurrence.YEARLY),
    INSURANCE("Insurance", "shield", "Insurance", Recurrence.YEARLY),
    PHONE("Mobile", "phone", "Bills", Recurrence.MONTHLY),
    INTERNET("Internet / DTH", "wifi", "Bills", Recurrence.MONTHLY),
    GAS("Gas", "fuel", "Bills", Recurrence.MONTHLY),
    WATER("Water", "water", "Bills", Recurrence.MONTHLY),
    LOAN_EMI("Loan EMI", "bank", "EMI/Loan", Recurrence.MONTHLY),
    RENT("Rent", "home", "Rent", Recurrence.MONTHLY),
    SUBSCRIPTION("Subscription", "movie", "Entertainment", Recurrence.MONTHLY),
    OTHER("Other", "receipt", "Bills", Recurrence.MONTHLY),
}

enum class Recurrence(val label: String, val months: Int) {
    NONE("One-time", 0),
    MONTHLY("Monthly", 1),
    QUARTERLY("Every 3 months", 3),
    HALF_YEARLY("Every 6 months", 6),
    YEARLY("Yearly", 12),
}

enum class BillStatus { PENDING, OVERDUE, PAID }

data class Bill(
    val id: Long = 0,
    /** Bills of the same series are the monthly/yearly occurrences of one bill. */
    val series: String,
    val name: String,
    val kind: BillKind,
    val amount: Double,
    /** Minimum amount due (credit cards). */
    val minDue: Double? = null,
    val dueDate: LocalDate,
    val recurrence: Recurrence,
    val paid: Boolean = false,
    val paidTs: Long? = null,
    val paidTxnId: Long? = null,
    val autopay: Boolean = false,
    /** Payee UPI ID for "Pay now". Never a card number. */
    val payeeVpa: String? = null,
    /** Last 4 digits of the card / policy / account, only for matching. */
    val last4: String? = null,
    /** Extra words that identify this biller in a payment SMS (e.g. "bescom"). */
    val hint: String? = null,
    val source: String = "manual",
    val smsKey: String? = null,
    /** Bit flags of reminders already sent: 1 = 3 days, 2 = 1 day, 4 = due day, 8 = overdue. */
    val notified: Int = 0,
    val note: String = "",
) {
    fun status(today: LocalDate = LocalDate.now()) = when {
        paid -> BillStatus.PAID
        dueDate.isBefore(today) -> BillStatus.OVERDUE
        else -> BillStatus.PENDING
    }

    fun daysLeft(today: LocalDate = LocalDate.now()) = ChronoUnit.DAYS.between(today, dueDate)
}

data class BillDraft(
    val name: String,
    val kind: BillKind,
    val amount: Double,
    val minDue: Double?,
    val dueDate: LocalDate,
    val last4: String?,
    val autopay: Boolean,
    val hint: String?,
    val recurrence: Recurrence,
) {
    /** Stable id so next month's statement for the same card/policy joins the same series. */
    val series: String get() = "sms:${kind.name}:${name.lowercase()}:${last4 ?: ""}"
}

// ------------------------------------------------------------------- dates

/** Finds due dates like 25-11-2026, 25/11/26, 25-Nov-26, 25th Nov 2026, Nov 25, 2026, 2026-11-25. */
object DueDates {
    private val IC = setOf(RegexOption.IGNORE_CASE)
    private const val MON = "(jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*"
    private val numeric = Regex("""(?<!\d)(\d{1,2})[-/.](\d{1,2})[-/.](\d{4}|\d{2})(?!\d)""")
    private val iso = Regex("""(?<!\d)(\d{4})-(\d{1,2})-(\d{1,2})(?!\d)""")
    private val dayMon = Regex("""(?<!\d)(\d{1,2})(?:st|nd|rd|th)?[\s\-/.]*$MON\.?(?:[\s\-/.,']*(\d{4}|\d{2})(?!\d))?""", IC)
    private val monDay = Regex("""\b$MON\.?\s+(\d{1,2})(?!\d)(?:st|nd|rd|th)?(?:,?\s*(\d{4}))?""", IC)
    private val dueKeyword = Regex(
        """(due\s*(?:date|on|by)?|payable\s*(?:on|by|before)?|pay\s*by|last\s*date|on\s*or\s*before|""" +
            """(?:will\s*be|to\s*be)\s*(?:auto[\s-]*)?debited\s*(?:on|by)?|debit\s*date|scheduled\s*(?:for|on)|expires?\s*on|before)""",
        IC,
    )
    private val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    private fun year(y: String?, d: Int, m: Int, received: LocalDate): Int? {
        if (y != null) return if (y.length == 2) 2000 + y.toInt() else y.toInt()
        // No year: the next occurrence that isn't long past.
        var guess = received.year
        val date = runCatching { LocalDate.of(guess, m, d) }.getOrNull() ?: return null
        if (date.isBefore(received.minusDays(30))) guess++
        return guess
    }

    private fun build(d: Int, m: Int, y: String?, received: LocalDate): LocalDate? {
        if (m !in 1..12 || d !in 1..31) return null
        val yy = year(y, d, m, received) ?: return null
        return runCatching { LocalDate.of(yy, m, d) }.getOrNull()
    }

    private fun mon(s: String) = months.indexOf(s.lowercase().take(3)) + 1

    private fun allDates(text: String, received: LocalDate): List<Pair<Int, LocalDate>> {
        val out = mutableListOf<Pair<Int, LocalDate>>()
        iso.findAll(text).forEach { m ->
            build(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1], received)?.let { out += m.range.first to it }
        }
        numeric.findAll(text).forEach { m ->
            build(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3], received)?.let { out += m.range.first to it }
        }
        dayMon.findAll(text).forEach { m ->
            build(m.groupValues[1].toInt(), mon(m.groupValues[2]), m.groupValues[3].ifEmpty { null }, received)?.let { out += m.range.first to it }
        }
        monDay.findAll(text).forEach { m ->
            build(m.groupValues[2].toInt(), mon(m.groupValues[1]), m.groupValues[3].ifEmpty { null }, received)?.let { out += m.range.first to it }
        }
        return out.sortedBy { it.first }
    }

    /** The due date in a bill SMS: the first date after a "due" phrase, else the latest plausible date. */
    fun find(text: String, received: LocalDate): LocalDate? {
        val dates = allDates(text, received).filter { (_, d) ->
            !d.isBefore(received.minusDays(60)) && !d.isAfter(received.plusDays(150))
        }
        if (dates.isEmpty()) return null
        val kw = dueKeyword.findAll(text).map { it.range.last }.toList()
        for (k in kw) {
            dates.firstOrNull { it.first > k && it.first - k <= 45 }?.let { return it.second }
        }
        return dates.maxByOrNull { it.second }?.second?.takeIf { !it.isBefore(received) }
    }
}

// ---------------------------------------------------------------- parser

/** Spots bill / premium / statement / upcoming auto-debit messages. */
object BillParser {
    private val IC = setOf(RegexOption.IGNORE_CASE)
    private fun rx(vararg p: String) = Regex(p.joinToString("|", "(", ")"), IC)

    private val dueClue = rx(
        "\\bdue\\b", "payable", "pay by", "last date", "on or before", "will be (?:auto[\\s-]*)?debited", "to be debited",
        "will be charged", "scheduled for", "renewal", "premium", "statement",
    )
    /** A thank-you / received message is a payment, not a bill. */
    private val paidClue = rx(
        "payment (?:of .{0,30})?(?:has been )?received", "thank you for (?:your )?payment", "has been paid",
        "successfully paid", "payment successful", "we have received", "is received", "paid successfully",
    )
    private val autopayClue = rx(
        "will be (?:auto[\\s-]*)?debited", "to be debited", "auto[\\s-]?debit", "autopay", "auto pay", "\\bnach\\b",
        "\\becs\\b", "e-?mandate", "mandate", "standing instruction", "\\bsi\\b",
    )

    private const val NUM = """(?:rs\.?|inr|₹)\s*([0-9]+(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?)"""
    private val totalDueRx = Regex(
        """(?:total\s*(?:amt|amount)\s*due|total\s*due|amount\s*due|amt\s*due|outstanding|bill\s*(?:amount|amt)|premium(?:\s*amount)?|""" +
            """bill\s*of|emi(?:\s*amount)?|amount\s*of|instal+ment\s*of|renewal\s*amount)\s*(?:of|is|:|-|=|for)?\s*(?:rs\.?|inr|₹)\s*([0-9]+(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?)""",
        IC,
    )
    private val minDueRx = Regex("""min(?:imum)?\.?\s*(?:amt|amount)?\s*due\s*(?:of|is|:|-|=)?\s*$NUM""", IC)
    private val anyAmountRx = Regex(NUM, IC)
    private val maskedRx = Regex("""(?:[xX*]{2,}|ending\s*(?:with)?\s*|no\.?\s*[xX*]*)\s?(\d{4,})""", IC)
    private val policyRx = Regex("""polic(?:y|ies)\s*(?:no\.?|number|#)?\s*[:\-]?\s*[xX*]*(\d{4,})""", IC)

    private val ccRx = rx("credit card", "\\bcc\\b", "card statement", "card\\s*(?:no\\.?\\s*)?[xX*]+\\d{4}.{0,60}(?:due|statement)", "min(?:imum)?\\.? (?:amt|amount) due")
    private val licRx = rx("\\blic\\b", "life insurance corporation", "licindia")
    private val insuranceRx = rx("insurance", "\\bpolicy\\b", "premium")
    private val electricityRx = rx(
        "electricity", "\\bpower\\b", "bescom", "msedcl", "mahadiscom", "tata power", "adani electricity", "bses", "tpddl",
        "cesc", "tneb", "tangedco", "kseb", "uppcl", "pspcl", "torrent power", "\\bunits?\\b", "consumer no", "\\bca no", "rr no",
        "\\bkwh\\b", "discom", "wbsedcl", "jvvnl", "apspdcl", "tsspdcl",
    )
    private val phoneRx = rx("postpaid", "mobile bill", "\\bairtel\\b", "\\bjio\\b", "\\bvi\\b", "vodafone", "\\bbsnl\\b", "mtnl")
    private val internetRx = rx("broadband", "fiber", "fibre", "wi-?fi", "act fibernet", "hathway", "excitel", "xstream", "\\bdth\\b", "tata play", "dish ?tv", "d2h")
    private val gasRx = rx("\\bgas\\b", "\\bpng\\b", "\\blpg\\b", "mahanagar gas", "\\bmgl\\b", "\\bigl\\b", "indane")
    private val waterRx = rx("water bill", "jal board", "\\bbwssb\\b", "water charges")
    private val emiRx = rx("\\bemi\\b", "\\bloan\\b")
    private val rentRx = rx("\\brent\\b")
    private val subscriptionRx = rx(
        "netflix", "spotify", "\\bprime\\b", "hotstar", "youtube", "\\bapple\\b", "google one", "icloud", "subscription", "membership",
        "sonyliv", "zee5", "jiocinema", "adobe", "microsoft",
    )

    private val brandNames: List<Pair<Regex, String>> = listOf(
        "bescom" to "BESCOM", "msedcl|mahadiscom" to "MSEDCL", "tata power" to "Tata Power", "adani electricity" to "Adani Electricity",
        "bses" to "BSES", "tpddl" to "Tata Power DDL", "cesc" to "CESC", "tangedco|tneb" to "TANGEDCO", "kseb" to "KSEB",
        "uppcl" to "UPPCL", "torrent power" to "Torrent Power", "airtel" to "Airtel", "\\bjio\\b" to "Jio", "vodafone|\\bvi\\b" to "Vi",
        "bsnl" to "BSNL", "act fibernet" to "ACT Fibernet", "hathway" to "Hathway", "excitel" to "Excitel", "tata play" to "Tata Play",
        "mahanagar gas|\\bmgl\\b" to "Mahanagar Gas", "\\bigl\\b|indraprastha gas" to "IGL", "indane" to "Indane Gas",
        "hdfc life" to "HDFC Life", "icici pru" to "ICICI Prudential", "sbi life" to "SBI Life", "max life" to "Max Life",
        "tata aia" to "Tata AIA", "star health" to "Star Health", "niva bupa" to "Niva Bupa", "care health" to "Care Health",
        "hdfc ergo" to "HDFC ERGO", "icici lombard" to "ICICI Lombard", "acko" to "ACKO", "go digit|digit insurance" to "Digit",
        "bajaj allianz" to "Bajaj Allianz", "bajaj fin" to "Bajaj Finance", "home credit" to "Home Credit", "tata capital" to "Tata Capital",
        "netflix" to "Netflix", "spotify" to "Spotify", "amazon prime|prime video" to "Amazon Prime", "hotstar" to "Disney+ Hotstar",
        "youtube" to "YouTube Premium", "google one" to "Google One", "icloud|apple" to "Apple", "jiocinema" to "JioCinema",
    ).map { Regex(it.first, RegexOption.IGNORE_CASE) to it.second }

    private fun amount(rx: Regex, text: String) = rx.find(text)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()

    fun parse(sender: String, body: String, received: LocalDate): BillDraft? {
        val text = body.replace('\n', ' ').replace('\r', ' ')
        if (SmsParser.isOtp(text) || sender.none { it.isLetter() }) return null
        if (!dueClue.containsMatchIn(text) || paidClue.containsMatchIn(text)) return null
        val due = DueDates.find(text, received) ?: return null

        val minDue = amount(minDueRx, text)
        val total = amount(totalDueRx, text)
            ?: anyAmountRx.findAll(text).firstOrNull { m ->
                val before = text.substring(maxOf(0, m.range.first - 20), m.range.first).lowercase()
                listOf("min", "bal", "limit", "lmt", "avl").none { before.contains(it) }
            }?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
            ?: return null
        if (total <= 0) return null

        val kind = when {
            ccRx.containsMatchIn(text) -> BillKind.CREDIT_CARD
            licRx.containsMatchIn(text) -> BillKind.LIC
            electricityRx.containsMatchIn(text) -> BillKind.ELECTRICITY
            subscriptionRx.containsMatchIn(text) -> BillKind.SUBSCRIPTION
            insuranceRx.containsMatchIn(text) -> BillKind.INSURANCE
            emiRx.containsMatchIn(text) -> BillKind.LOAN_EMI
            internetRx.containsMatchIn(text) -> BillKind.INTERNET
            phoneRx.containsMatchIn(text) -> BillKind.PHONE
            gasRx.containsMatchIn(text) -> BillKind.GAS
            waterRx.containsMatchIn(text) -> BillKind.WATER
            rentRx.containsMatchIn(text) -> BillKind.RENT
            else -> BillKind.OTHER
        }
        val brand = brandNames.firstOrNull { it.first.containsMatchIn(text) }?.second
        val bank = Banks.identify(sender, text).name
        val last4 = (if (kind == BillKind.LIC || kind == BillKind.INSURANCE) policyRx.find(text) else null)
            ?.groupValues?.get(1)?.takeLast(4)
            ?: maskedRx.find(text)?.groupValues?.get(1)?.takeLast(4)

        val name = when (kind) {
            BillKind.CREDIT_CARD -> "$bank Credit Card"
            BillKind.LIC -> "LIC Premium"
            BillKind.LOAN_EMI -> "${brand ?: bank} Loan EMI"
            BillKind.INSURANCE -> brand?.let { "$it Premium" } ?: "Insurance Premium"
            BillKind.ELECTRICITY -> brand ?: "Electricity Bill"
            BillKind.PHONE -> brand?.let { "$it Postpaid" } ?: "Mobile Bill"
            BillKind.INTERNET -> brand ?: "Internet Bill"
            BillKind.GAS -> brand ?: "Gas Bill"
            BillKind.WATER -> "Water Bill"
            BillKind.SUBSCRIPTION -> brand ?: "Subscription"
            BillKind.RENT -> "Rent"
            BillKind.OTHER -> brand ?: SmsParser.senderName(sender).let { if (it.length <= 8) "$it Bill" else it }
        }
        val recurrence = when {
            Regex("quarterly", RegexOption.IGNORE_CASE).containsMatchIn(text) -> Recurrence.QUARTERLY
            Regex("half[\\s-]?yearly", RegexOption.IGNORE_CASE).containsMatchIn(text) -> Recurrence.HALF_YEARLY
            Regex("\\b(yearly|annual)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> Recurrence.YEARLY
            Regex("\\bmonthly\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> Recurrence.MONTHLY
            else -> kind.defaultRecurrence
        }
        val hint = when (kind) {
            BillKind.LIC -> "lic"
            BillKind.CREDIT_CARD -> null
            else -> brand?.lowercase()
        }
        return BillDraft(
            name = name,
            kind = kind,
            amount = total,
            minDue = if (kind == BillKind.CREDIT_CARD) minDue else null,
            dueDate = due,
            last4 = last4,
            autopay = autopayClue.containsMatchIn(text),
            hint = hint,
            recurrence = recurrence,
        )
    }
}

// -------------------------------------------------------------------- UPI

object Upi {
    private val vpaRx = Regex("""^[a-zA-Z0-9.\-_]{2,256}@[a-zA-Z][a-zA-Z0-9]{1,63}$""")
    private val longNumberRx = Regex("""\d{12,}""")

    /** Returns an error message, or null when the UPI ID is fine to save. */
    fun validate(vpa: String): String? = when {
        vpa.isBlank() -> null
        longNumberRx.containsMatchIn(vpa) ->
            "This looks like a card or account number. Card details are never stored — use the biller's UPI ID, or pay from your UPI app."
        !vpaRx.matches(vpa.trim()) -> "Enter a UPI ID like name@bank"
        else -> null
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    /** upi://pay link with payee and amount filled in. The UPI app asks for the PIN — this app never sees it. */
    fun payUri(vpa: String, payeeName: String, amount: Double, note: String): String =
        "upi://pay?pa=${enc(vpa.trim())}&pn=${enc(payeeName)}&am=${String.format(Locale.US, "%.2f", amount)}&cu=INR&tn=${enc(note.take(50))}"

    /** UPI apps return "txnId=..&Status=SUCCESS&..". true = success, false = failed, null = unknown. */
    fun isSuccess(response: String?): Boolean? {
        val r = response?.lowercase() ?: return null
        return when {
            Regex("status=(success|submitted)").containsMatchIn(r) -> true
            Regex("status=(failure|failed)").containsMatchIn(r) -> false
            else -> null
        }
    }
}
