package com.expensetracker.sms

data class ParsedTxn(
    val amount: Double,
    val isDebit: Boolean,
    val merchant: String,
    val account: String?,
)

/**
 * Turns an Indian bank / UPI / card SMS into a transaction.
 * Only transactional messages are accepted: OTPs, offers, reminders, failed
 * transactions and messages from personal phone numbers are skipped.
 */
object SmsParser {
    private val IC = setOf(RegexOption.IGNORE_CASE)

    private val amountRx = Regex("""(?:rs\.?|inr|₹)\s*:?\s*([0-9]+(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?)""", IC)
    private val bareAmountRx = Regex(
        """(?:debited|credited|spent|paid|sent|withdrawn|deducted)\s+(?:by|for|with|of)?\s*([0-9]+(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?)""",
        IC,
    )

    private val otpRx = Regex(
        """(otp is|is your otp|is the otp|otp for|otp to|one[\s-]?time[\s-]?password|verification code|\b\d{4,8} is your)""",
        IC,
    )

    private val ignoreRx = Regex(
        listOf(
            "will be debited", "will be auto[- ]?debited", "to be debited", "is due", "due on", "due by",
            "due date", "minimum amount", "min\\.? amt", "total amount due", "requested", "collect request",
            "request of", "offer", "pre-?approved", "eligible", "apply now", "congratulations", "\\bwin\\b",
            "failed", "declined", "unsuccessful", "insufficient", "reversal", "has been blocked",
            "e-?mandate", "standing instruction", "autopay set",
        ).joinToString("|", "(", ")"),
        IC,
    )

    private val debitRx = Regex(
        """\b(debited|spent|paid|sent|withdrawn|withdrawal|purchased?|deducted|txn of|transaction of|payment of|dr\b)""",
        IC,
    )
    private val creditRx = Regex("""\b(credited|received|deposited|refund(?:ed)?|cr\b)""", IC)

    private const val STOP =
        """(?=\s+on\b|\s+ref|\s+for\b|\s+via\b|\s+using\b|\s+upi\b|\s+avl|\s+txn|\s+dt\b|\s+date\b|\s+not you|\.\s|\.$|,|;|\(|$)"""
    private const val NAME = """[A-Za-z0-9][A-Za-z0-9&'._@\-\s*]{1,40}?"""

    private val upiPathRx = Regex("""UPI/(?:P2[AM]/)?\d+/([A-Za-z][A-Za-z0-9 .&\-]{1,30}?)$STOP""", IC)
    private val atmRx = Regex("""\b(atm|cash withdrawal|cash wdl)\b""", IC)
    private val toRx = Regex("""\b(?:to|towards)\s+(?:vpa\s+)?($NAME)$STOP""", IC)
    private val atRx = Regex("""\bat\s+([A-Za-z][A-Za-z0-9&'._\-\s*]{1,40}?)$STOP""", IC)
    private val fromRx = Regex("""\bfrom\s+(?:vpa\s+)?($NAME)$STOP""", IC)
    private val byRx = Regex("""\bby\s+(?:vpa\s+)?($NAME)$STOP""", IC)
    private val infoRx = Regex("""\binfo[:\s\-]+([A-Za-z0-9][A-Za-z0-9&'._@/\-\s*]{1,40}?)$STOP""", IC)
    private val beforeCreditedRx = Regex("""[;.]\s*([A-Za-z][A-Za-z0-9&'.\-\s]{1,40}?)\s+credited""", IC)
    private val vpaRx = Regex("""\b([a-zA-Z0-9.\-_]{2,})@([a-zA-Z]{2,})\b""")

    private val acctRx = Regex(
        """(?:a/c|acct|account|\bac|card)\s*(?:no\.?|number|ending(?:\s+with)?)?\s*[:\-]?\s*[xX*.]*\s*(\d{3,6})\b""",
        IC,
    )

    private val badStarts = listOf(
        "your", "a/c", "ac", "acct", "account", "card", "you", "mobile", "rs", "inr", "xx", "beneficiary",
        "bank a/c", "the a/c", "neft", "imps", "rtgs", "upi",
    )

    fun parse(sender: String, body: String): ParsedTxn? {
        if (sender.none { it.isLetter() }) return null // personal number
        val text = body.replace('\n', ' ').replace('\r', ' ')
        if (otpRx.containsMatchIn(text)) return null
        if (ignoreRx.containsMatchIn(text)) return null

        val d = debitRx.find(text)
        val c = creditRx.find(text)
        if (d == null && c == null) return null
        val isDebit = when {
            d == null -> false
            c == null -> true
            else -> d.range.first <= c.range.first
        }

        val amount = findAmount(text) ?: return null
        if (amount <= 0.0) return null

        val merchant = if (isDebit && atmRx.containsMatchIn(text)) "ATM Withdrawal"
        else findMerchant(text, isDebit) ?: senderName(sender)
        return ParsedTxn(amount, isDebit, merchant, findAccount(text))
    }

    private fun findAmount(text: String): Double? {
        for (m in amountRx.findAll(text)) {
            val before = text.substring(maxOf(0, m.range.first - 25), m.range.first).lowercase()
            if (listOf("bal", "limit", "lmt", "avl", "available").any { before.contains(it) }) continue
            val v = m.groupValues[1].replace(",", "").toDoubleOrNull()
            if (v != null) return v
        }
        return bareAmountRx.find(text)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
    }

    private fun findMerchant(text: String, isDebit: Boolean): String? {
        val order = if (isDebit) {
            listOf(upiPathRx, toRx, atRx, beforeCreditedRx, infoRx)
        } else {
            listOf(fromRx, infoRx, upiPathRx, byRx)
        }
        for (rx in order) {
            for (m in rx.findAll(text)) {
                val name = clean(m.groupValues[1])
                if (name != null) return name
            }
        }
        vpaRx.find(text)?.let { return clean(it.value) }
        return null
    }

    private fun clean(raw: String): String? {
        var s = raw.trim().trimEnd('.', '-', '*', ' ', '/').replace(Regex("\\s+"), " ")
        val wasVpa = s.contains('@')
        if (wasVpa) s = s.substringBefore('@').trim()
        if (s.length < 2) return null
        val low = s.lowercase()
        if (badStarts.any { low == it || low.startsWith("$it ") || low.startsWith("$it/") }) return null
        if (!wasVpa && s.all { it.isDigit() || it == 'x' || it == 'X' || it == '*' || it == ' ' }) return null
        s = s.take(30).trim()
        // Title-case shouty bank text: "SWIGGY LIMITED" -> "Swiggy Limited"
        if (s == s.uppercase()) {
            s = s.split(" ").joinToString(" ") { w -> w.lowercase().replaceFirstChar { it.uppercase() } }
        }
        return s
    }

    private fun findAccount(text: String): String? = acctRx.find(text)?.groupValues?.get(1)?.takeLast(4)

    /** "VM-HDFCBK-S" -> "HDFCBK" */
    fun senderName(sender: String): String =
        sender.split('-').maxByOrNull { it.length }?.trim()?.ifBlank { null } ?: sender
}
