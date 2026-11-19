package com.expensetracker.sms

/** Account / card kinds shown on the Accounts tab. */
enum class AcctType(val label: String) {
    BANK("Bank account"),
    CREDIT_CARD("Credit card"),
    DEBIT_CARD("Debit card"),
    WALLET("Wallet"),
}

data class BankInfo(val name: String, val start: Long, val end: Long)

/** Who sent an SMS: a bank/card issuer, a payment or shopping app, or someone else. */
enum class SenderKind { BANK, APP, OTHER }

/** Recognises Indian banks / card issuers from the SMS sender ID or text. */
object Banks {
    // Order matters: more specific codes first.
    private val bySender: List<Pair<String, BankInfo>> = listOf(
        "SBICRD" to BankInfo("SBI Card", 0xFF1F5FBF, 0xFF0B2E66),
        "SBMSMS" to BankInfo("SBI", 0xFF2D6FD3, 0xFF153A80),
        "HDFC" to BankInfo("HDFC Bank", 0xFF004C8F, 0xFF06264A),
        "ICICI" to BankInfo("ICICI Bank", 0xFFB3272D, 0xFF5E1015),
        "AXIS" to BankInfo("Axis Bank", 0xFF97144D, 0xFF4E0A28),
        "KOTAK" to BankInfo("Kotak Bank", 0xFFE3262D, 0xFF0A2C5E),
        "IDFC" to BankInfo("IDFC FIRST", 0xFF9C1D26, 0xFF4F0E13),
        "YESB" to BankInfo("Yes Bank", 0xFF0057A6, 0xFF002E59),
        "INDUS" to BankInfo("IndusInd Bank", 0xFF8B1D41, 0xFF470E21),
        "FEDBNK" to BankInfo("Federal Bank", 0xFF0B4EA2, 0xFF062A57),
        "FEDERAL" to BankInfo("Federal Bank", 0xFF0B4EA2, 0xFF062A57),
        "PNB" to BankInfo("PNB", 0xFFA6192E, 0xFF560C17),
        "BOBTXN" to BankInfo("Bank of Baroda", 0xFFF15A22, 0xFF8A2E0C),
        "BARODA" to BankInfo("Bank of Baroda", 0xFFF15A22, 0xFF8A2E0C),
        "BOBCRD" to BankInfo("BOBCARD", 0xFFF15A22, 0xFF8A2E0C),
        "BOIIND" to BankInfo("Bank of India", 0xFF1B4F9C, 0xFF0C284F),
        "CANBNK" to BankInfo("Canara Bank", 0xFF0072BC, 0xFF003A61),
        "UNION" to BankInfo("Union Bank", 0xFFD71920, 0xFF0D3C78),
        "UBOI" to BankInfo("Union Bank", 0xFFD71920, 0xFF0D3C78),
        "AUBANK" to BankInfo("AU Bank", 0xFFEC6C1F, 0xFF6B2C8E),
        "RBL" to BankInfo("RBL Bank", 0xFF2B3990, 0xFF141C49),
        "SCB" to BankInfo("Standard Chartered", 0xFF0473EA, 0xFF009F6B),
        "CITI" to BankInfo("Citi", 0xFF056DAE, 0xFF003B5C),
        "AMEX" to BankInfo("American Express", 0xFF2E77BB, 0xFF173C5E),
        "PAYTM" to BankInfo("Paytm", 0xFF00BAF2, 0xFF002E6E),
        "AIRBNK" to BankInfo("Airtel Payments Bank", 0xFFE40000, 0xFF730000),
        "IPPB" to BankInfo("India Post Payments Bank", 0xFFE31E24, 0xFF7A1013),
        "ONECRD" to BankInfo("OneCard", 0xFF1C1C1C, 0xFF3A3A3A),
        "SLICE" to BankInfo("slice", 0xFF5B2EFF, 0xFF2A1380),
        "JUPITR" to BankInfo("Jupiter", 0xFFF26B5B, 0xFF7A2E9C),
        "IDBI" to BankInfo("IDBI Bank", 0xFF00836C, 0xFF00423A),
        "IOB" to BankInfo("Indian Overseas Bank", 0xFF1D4E9E, 0xFF0E2850),
        "CENTBK" to BankInfo("Central Bank", 0xFFDA251D, 0xFF6E120E),
        "INDBNK" to BankInfo("Indian Bank", 0xFF0A4D8C, 0xFF052747),
        "SBI" to BankInfo("SBI", 0xFF2D6FD3, 0xFF153A80),
    )

    private val byText: List<Pair<Regex, String>> = listOf(
        Regex("""\bsbi card\b""", RegexOption.IGNORE_CASE) to "SBICRD",
        Regex("""\bhdfc\b""", RegexOption.IGNORE_CASE) to "HDFC",
        Regex("""\bicici\b""", RegexOption.IGNORE_CASE) to "ICICI",
        Regex("""\baxis\b""", RegexOption.IGNORE_CASE) to "AXIS",
        Regex("""\bkotak\b""", RegexOption.IGNORE_CASE) to "KOTAK",
        Regex("""\bidfc\b""", RegexOption.IGNORE_CASE) to "IDFC",
        Regex("""\byes bank\b""", RegexOption.IGNORE_CASE) to "YESB",
        Regex("""\bindusind\b""", RegexOption.IGNORE_CASE) to "INDUS",
        Regex("""\b(sbi|state bank)\b""", RegexOption.IGNORE_CASE) to "SBI",
        Regex("""\bpnb\b""", RegexOption.IGNORE_CASE) to "PNB",
        Regex("""\bcanara\b""", RegexOption.IGNORE_CASE) to "CANBNK",
        Regex("""\bbank of baroda\b""", RegexOption.IGNORE_CASE) to "BARODA",
        Regex("""\bfederal bank\b""", RegexOption.IGNORE_CASE) to "FEDERAL",
    )

    private val fallbackColors = listOf(
        0xFF37474F to 0xFF1C262B, 0xFF4527A0 to 0xFF231450, 0xFF00695C to 0xFF00352E,
        0xFF6A1B9A to 0xFF350D4D, 0xFF283593 to 0xFF141B4A,
    )

    /** Payment / shopping apps whose "you paid ₹X" SMS usually duplicate the bank's SMS. */
    private val appCodes = listOf(
        "PHONPE", "PHNPE", "PHONEPE", "GPAY", "GOOGLE", "AMAZON", "AMZN", "MOBIKW", "FRCHRG", "FREECH",
        "CRED", "SWIGGY", "ZOMATO", "UBER", "OLACAB", "FLPKRT", "FLIPKT", "MYNTRA", "SUPRYM", "NAVIAP",
        "POPCLB", "JIOPAY", "BHRTPE", "BHARPE", "PAYZAP", "BLINKT", "ZEPTO", "BIGBSK", "IRCTC", "MMTRIP",
        "PAYTM",
    )

    fun senderKind(sender: String): SenderKind {
        val code = SmsParser.senderName(sender).uppercase()
        if (code.contains("PAYTMB") || code.contains("PYTMBK")) return SenderKind.BANK // Paytm Payments Bank
        if (appCodes.any { code.contains(it) }) return SenderKind.APP
        if (bySender.any { code.contains(it.first) }) return SenderKind.BANK
        return SenderKind.OTHER
    }

    fun identify(sender: String, body: String): BankInfo {
        val code = SmsParser.senderName(sender).uppercase()
        bySender.firstOrNull { code.contains(it.first) }?.let { return it.second }
        byText.firstOrNull { it.first.containsMatchIn(body) }?.let { (_, c) ->
            bySender.firstOrNull { it.first == c }?.let { return it.second }
        }
        val (a, b) = fallbackColors[(code.hashCode() and 0x7fffffff) % fallbackColors.size]
        return BankInfo(code.ifBlank { "Account" }, a, b)
    }

    fun colorsFor(bankName: String): Pair<Long, Long> {
        bySender.firstOrNull { it.second.name == bankName }?.let { return it.second.start to it.second.end }
        return fallbackColors[(bankName.hashCode() and 0x7fffffff) % fallbackColors.size]
    }
}
