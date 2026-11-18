package com.expensetracker.sms

object Categorizer {
    private val keywords: List<Pair<String, List<String>>> = listOf(
        "ATM/Cash" to listOf("atm", "cash withdrawal", "withdrawn at", "cash wdl"),
        "Food" to listOf(
            "swiggy", "zomato", "restaurant", "cafe", "dominos", "domino", "pizza", "kfc", "mcdonald",
            "burger", "starbucks", "eatsure", "biryani", "bakery", "chai", "haldiram", "subway", "dhaba",
        ),
        "Groceries" to listOf(
            "bigbasket", "blinkit", "zepto", "instamart", "dmart", "grofers", "jiomart", "grocery",
            "supermarket", "kirana", "reliance fresh", "spencer", "more retail", "big bazaar",
        ),
        "Shopping" to listOf(
            "amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa", "tata cliq", "snapdeal", "decathlon",
            "lifestyle", "westside", "croma", "reliance digital", "ikea", "zudio", "trends",
        ),
        "Travel" to listOf(
            "uber", "ola", "rapido", "irctc", "makemytrip", "goibibo", "redbus", "indigo", "air india",
            "vistara", "spicejet", "akasa", "metro", "fastag", "yatra", "cleartrip", "ixigo", "namma yatri",
        ),
        "Fuel" to listOf(
            "petrol", "fuel", "hpcl", "bpcl", "iocl", "indian oil", "bharat petroleum",
            "hindustan petroleum", "shell", "filling station", "nayara",
        ),
        "Bills" to listOf(
            "electricity", "airtel", "jio", "bsnl", "vodafone", "broadband", "recharge", "bescom",
            "tata power", "adani", "mahanagar gas", "water bill", "dth", "tata play", "act fibernet",
            "billdesk", "bill", "lpg", "indane",
        ),
        "Rent" to listOf("rent", "nobroker"),
        "Entertainment" to listOf(
            "netflix", "hotstar", "spotify", "bookmyshow", "prime video", "youtube", "sonyliv", "zee5",
            "jiocinema", "pvr", "inox", "steam", "playstation", "district",
        ),
        "Health" to listOf(
            "pharmacy", "apollo", "medplus", "hospital", "clinic", "1mg", "pharmeasy", "netmeds",
            "diagnostic", "practo", "chemist", "medical",
        ),
        "Education" to listOf("school", "college", "university", "udemy", "coursera", "byju", "unacademy", "tuition"),
        "EMI/Loan" to listOf("emi", "loan", "bajaj finserv", "home credit"),
        "Investment" to listOf("zerodha", "groww", "upstox", "mutual fund", "sip", "nps", "ppf", "angel one", "kuvera"),
    )

    private val regexes = keywords.map { (cat, kws) ->
        cat to Regex("\\b(" + kws.joinToString("|") { Regex.escape(it) } + ")", RegexOption.IGNORE_CASE)
    }

    private val IC = setOf(RegexOption.IGNORE_CASE)
    private val phoneVpa = Regex("""\b\d{10}@""")
    private val salaryRx = Regex("""\b(salary|sal|payroll|stipend)\b""", IC)
    private val dividendRx = Regex("""\b(dividend|div)\b""", IC)
    private val interestRx = Regex("""\b(interest|int\.?\s*(?:pd|paid|cr|credited|credit)|sb\s*int)\b""", IC)
    private val refundRx = Regex("""\b(refund|refunded|cashback|cash back|reversal|reversed|chargeback)\b""", IC)
    private val rentRx = Regex("""\b(rent|rental)\b""", IC)

    fun categorize(merchant: String, body: String, isDebit: Boolean, rule: String?): String {
        if (rule != null) return rule
        if (!isDebit) return incomeCategory(merchant, body)
        regexes.firstOrNull { it.second.containsMatchIn(merchant) }?.let { return it.first }
        regexes.firstOrNull { it.second.containsMatchIn(body) }?.let { return it.first }
        if (phoneVpa.containsMatchIn(body) || merchant.all { it.isDigit() }) return "Transfers"
        return Categories.OTHERS
    }

    fun incomeCategory(merchant: String, body: String): String {
        val text = "$merchant $body"
        return when {
            salaryRx.containsMatchIn(text) -> "Salary"
            dividendRx.containsMatchIn(text) -> "Dividend"
            interestRx.containsMatchIn(text) -> "Interest"
            refundRx.containsMatchIn(text) -> "Refund & cashback"
            rentRx.containsMatchIn(text) -> "Rent received"
            else -> Categories.OTHER_INCOME
        }
    }
}
