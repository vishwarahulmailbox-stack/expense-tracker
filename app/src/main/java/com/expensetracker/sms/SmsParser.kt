package com.expensetracker.sms

data class ParsedTxn(
    val amount: Double,
    val isDebit: Boolean,
    val merchant: String,
    val account: String?,
    val bank: String? = null,
    val acctType: AcctType? = null,
    /** Bank balance, or available limit for credit cards, if the SMS mentions it. */
    val balance: Double? = null,
    /** Sent by a payment app (PhonePe, Paytm, Amazon Pay…) rather than a bank: may duplicate a bank SMS. */
    val fromApp: Boolean = false,
)

data class AccountInfo(val bank: String, val type: AcctType?, val balance: Double?)

/** Why an SMS was not treated as a transaction. [keep] = show it in "Skipped messages". */
enum class SkipReason(val label: String, val keep: Boolean) {
    PERSONAL("Personal message", false),
    OTP("OTP", false),
    MANDATE_SETUP("Autopay / mandate setup", true),
    REMINDER("Reminder or upcoming payment", true),
    REQUEST("Payment request", true),
    FAILED("Failed or declined", true),
    PROMO("Offer or promotion", true),
    NO_DIRECTION("No debit / credit mentioned", true),
    NO_AMOUNT("No amount", false),
    NOT_FROM_BANK("Not from a bank", true),
}

sealed interface ParseResult {
    data class Ok(val txn: ParsedTxn) : ParseResult
    data class Skip(val reason: SkipReason) : ParseResult
}

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

    /** OTP *delivery* phrases. A bank footer like "Never share your OTP" does not match. */
    private val otpRx = Regex(
        listOf(
            "otp is", "is your otp", "is the otp", "otp for", "otp to (?:complete|authenticate|verify|proceed|login|log in|confirm|approve)",
            "otp\\s*[:-]\\s*\\d", "use otp", "enter otp", "otp \\d{4,8}", "one[\\s-]?time[\\s-]?pass(?:word|code)\\s*(?:is|for|:)",
            "verification code", "secure code", "authentication code", "security code is", "code is \\d", "\\b\\d{4,8} is (?:your|the)\\b",
            "passcode is",
        ).joinToString("|", "(", ")"),
        IC,
    )
    private fun rx(vararg p: String) = Regex(p.joinToString("|", "(", ")"), IC)

    private val mandateSetupRx = rx(
        "(?:e-?mandate|mandate|autopay|auto-pay|auto pay|standing instruction)[^.]{0,50}?(?:has been |is |was )?(?:successfully )?(?:registered|created|set ?up|setup|approved|activated|enabled|modified|revoked|cancelled|paused)\\b",
        "mandate registration", "(?:upi )?autopay (?:request|mandate)",
    )
    private val reminderRx = rx(
        "will be debited", "will be auto[- ]?debited", "to be debited", "will be charged", "will be deducted",
        "is due", "due on", "due by", "due date", "minimum amount due", "min\\.? amt due", "total amount due",
        "total amt due", "payment reminder", "upcoming", "is scheduled", "scheduled for", "pay now",
    )
    private val requestRx = rx("has requested", "requested (?:rs|inr|₹|money|payment)", "collect request", "request of", "payment request", "is requesting")
    private val failedRx = rx("failed", "declined", "unsuccessful", "insufficient", "could not be processed", "not successful", "rejected")
    private val reversalRx = rx("reversed", "reversal", "refunded", "refund of", "credited back", "has been refunded")
    private val promoRx = rx(
        "offer", "offers", "pre-?approved", "eligible", "apply now", "congratulations", "\\bwin\\b", "\\bupto\\b",
        "limited period", "hurry", "exclusive", "voucher worth", "loan of up ?to", "claim now", "click to claim",
    )
    /** Signs that an SMS is about a real account: masked number or a payment reference. */
    private val accountRefRx = rx(
        "[xX*]{2,}\\s?\\d{3,}", "\\b(?:a/c|acct|account|card)\\s*(?:no\\.?)?\\s*(?:ending\\s*)?[xX*.]*\\d{3,}",
        "\\b(?:upi ref|ref no|ref#|refno|rrn|utr|txn id|transaction id|imps ref|ref\\.? no)",
    )

    private val debitRx = Regex(
        """\b(debited|spent|paid|sent|withdrawn|withdrawal|purchased?|deducted|txn of|transaction of|payment of|dr\b)""",
        IC,
    )
    private val creditRx = Regex("""\b(credited|received|deposited|refund(?:ed)?|reversed|reversal|credited back|cr\b)""", IC)

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

    fun parse(sender: String, body: String): ParsedTxn? = (classify(sender, body) as? ParseResult.Ok)?.txn

    /** True for OTP messages. These are never read further, stored or shown. */
    fun isOtp(body: String) = otpRx.containsMatchIn(body)

    fun hasAmount(body: String) = amountRx.containsMatchIn(body)

    /**
     * Decides whether an SMS is a transaction. [lenient] skips the "is this really a
     * transaction" checks; used when you tap "Add as transaction" on a skipped message.
     */
    fun classify(sender: String, body: String, lenient: Boolean = false): ParseResult {
        val text = body.replace('\n', ' ').replace('\r', ' ')
        if (!lenient) {
            if (sender.none { it.isLetter() }) return ParseResult.Skip(SkipReason.PERSONAL)
            if (isOtp(text)) return ParseResult.Skip(SkipReason.OTP)
            if (mandateSetupRx.containsMatchIn(text)) return ParseResult.Skip(SkipReason.MANDATE_SETUP)
            if (reminderRx.containsMatchIn(text)) return ParseResult.Skip(SkipReason.REMINDER)
            if (requestRx.containsMatchIn(text)) return ParseResult.Skip(SkipReason.REQUEST)
            if (failedRx.containsMatchIn(text) && !reversalRx.containsMatchIn(text)) return ParseResult.Skip(SkipReason.FAILED)
            if (promoRx.containsMatchIn(text) && !accountRefRx.containsMatchIn(text)) return ParseResult.Skip(SkipReason.PROMO)
        }

        val reversal = reversalRx.containsMatchIn(text)
        val d = debitRx.find(text)
        val c = creditRx.find(text)
        if (d == null && c == null && !lenient) return ParseResult.Skip(SkipReason.NO_DIRECTION)
        val isDebit = when {
            reversal -> false // money coming back
            d == null -> c == null // lenient + no clue -> expense
            c == null -> true
            else -> d.range.first <= c.range.first
        }

        val amount = findAmount(text)
        if (amount == null || amount <= 0.0) return ParseResult.Skip(SkipReason.NO_AMOUNT)

        val kind = Banks.senderKind(sender)
        if (!lenient && kind == SenderKind.OTHER && !accountRefRx.containsMatchIn(text)) {
            return ParseResult.Skip(SkipReason.NOT_FROM_BANK)
        }

        val merchant = if (isDebit && atmRx.containsMatchIn(text)) "ATM Withdrawal"
        else findMerchant(text, isDebit) ?: senderName(sender)
        val account = findAccount(text)
        val info = accountInfo(sender, text, account)
        return ParseResult.Ok(
            ParsedTxn(amount, isDebit, merchant, account, info.bank, info.type, info.balance, fromApp = kind == SenderKind.APP)
        )
    }

    private val creditCardRx = Regex("""\b(credit card|creditcard|cc\b|avl\.?\s*lmt|avl\.?\s*limit|available (?:credit )?limit|credit limit)""", IC)
    private val debitCardRx = Regex("""\b(debit card|atm card|dc\b|rupay debit)""", IC)
    private val walletRx = Regex("""\b(wallet|paytm balance|amazon pay balance)\b""", IC)
    private val bankAcctRx = Regex("""\b(a/c|acct|account|\bac)\b""", IC)
    private val cardWordRx = Regex("""\bcard\b""", IC)
    private const val NUM = """(?:rs\.?|inr|₹)?\s*([0-9]+(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?)"""
    private val balanceRx = Regex(
        """(?:avl\.?\s*bal(?:ance)?|available\s+bal(?:ance)?|a/c\s+bal(?:ance)?|clear\s+bal(?:ance)?|\bbal(?:ance)?)\s*(?:is|:|-|=)?\s*$NUM""",
        IC,
    )
    private val limitRx = Regex(
        """(?:avl\.?\s*lmt|avl\.?\s*limit|available\s+(?:credit\s+)?limit|avail\.?\s*limit)\s*(?:is|:|-|=)?\s*$NUM""",
        IC,
    )

    /** Bank name, account kind and balance/limit. Also used to back-fill old data. */
    fun accountInfo(sender: String, body: String, account: String?): AccountInfo {
        val bank = Banks.identify(sender, body)
        val isCard = cardWordRx.containsMatchIn(body)
        val type = when {
            bank.name.contains("Card", true) || bank.name in setOf("OneCard", "slice", "American Express") -> AcctType.CREDIT_CARD
            creditCardRx.containsMatchIn(body) -> AcctType.CREDIT_CARD
            debitCardRx.containsMatchIn(body) -> AcctType.DEBIT_CARD
            walletRx.containsMatchIn(body) -> AcctType.WALLET
            isCard && !bankAcctRx.containsMatchIn(body) -> AcctType.CREDIT_CARD
            account != null -> AcctType.BANK
            else -> null
        }
        val balRx = if (type == AcctType.CREDIT_CARD) limitRx else balanceRx
        val balance = balRx.find(body)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
        return AccountInfo(bank.name, type, balance)
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

    private val channelPrefixRx = Regex("""^(?:upi|pos|ecom|imps|neft|mmt|vps|vin|ach|nach)[\s/\-*:]+""", IC)
    private val gatewayPrefixRx = Regex(
        """^(?:paytm|razorpay|raz|rzp|pyu|payu|ccavenue|cca|bharatpe|phonepe|gpay|bdsk|billdesk|cashfree|easebuzz|juspay|pinelabs|ezetap)\s*[*\-#]\s*""",
        IC,
    )

    private fun clean(raw: String): String? {
        var s = raw.trim().trimEnd('.', '-', '*', ' ', '/').replace(Regex("\\s+"), " ")
        val wasVpa = s.contains('@')
        if (wasVpa) s = s.substringBefore('@').trim()
        // Payment-gateway prefixes: "PAYTM*SWIGGY", "RAZ*ZOMATO", "UPI-UBER", "POS AMAZON"
        s = s.replace(gatewayPrefixRx, "").replace(channelPrefixRx, "").trim()
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
