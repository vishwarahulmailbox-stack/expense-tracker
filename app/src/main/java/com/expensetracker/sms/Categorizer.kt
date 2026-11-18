package com.expensetracker.sms

/**
 * Guesses a category from the merchant name and SMS text.
 *
 * Order of checks (first hit wins):
 *  1. Your own rule for this merchant ("Always use…")
 *  2. Cash withdrawals
 *  3. Known brands in the merchant name (Swiggy, Uber, Amazon…)
 *  4. Shop-type words in the merchant name ("… Medical", "… Kirana", "… Restaurant")
 *  5. Known brands anywhere in the SMS
 *  6. Payment-type clues (SIPs, NACH/ECS loans, FASTag, BBPS bills)
 *  7. Money sent to a person (UPI/IMPS to a name or phone number) → Transfers
 *  8. Others
 */
object Categorizer {
    private val IC = setOf(RegexOption.IGNORE_CASE)

    /** Whole-word, case-insensitive match for any of [w]. */
    private fun words(vararg w: String) = Regex(
        "(?<![a-z0-9])(" + w.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) } + ")(?![a-z0-9])",
        IC,
    )

    // ---------------------------------------------------------------- brands
    // Order matters: "Swiggy Instamart" must hit Groceries before "Swiggy" hits Food,
    // and "Amazon Prime" must hit Entertainment before "Amazon" hits Shopping.
    private val brands: List<Pair<String, Regex>> = listOf(
        Categories.SELF to words(
            "cred club", "credclub", "cred.club", "credit card bill", "credit card payment", "cc bill", "cc payment",
            "card bill payment", "payment towards your credit card", "payment received towards your card",
            "sweep", "auto sweep", "fd booked", "rd installment",
        ),
        "Groceries" to words(
            "instamart", "swiggy instamart", "swiggyinstamart", "bigbasket", "big basket", "bbnow", "bb now", "blinkit",
            "grofers", "zepto", "kiranapro", "dmart", "d mart", "d-mart", "avenue supermarts", "jiomart", "jio mart",
            "reliance fresh", "reliance smart", "smart bazaar", "smart point", "more retail", "more supermarket",
            "spencers", "spencer's", "star bazaar", "natures basket", "nature's basket", "big bazaar", "easyday",
            "ratnadeep", "vijetha", "licious", "freshtohome", "fresh to home", "country delight", "milkbasket",
            "dunzo", "bbdaily", "otipy", "lulu hypermarket", "metro cash", "spar", "hypercity",
            "amul", "mother dairy", "nandini", "heritage foods",
        ),
        "Investment" to words(
            "zerodha", "groww", "upstox", "angel one", "angelone", "angel broking", "coin by zerodha",
            "kuvera", "paytm money", "etmoney", "et money", "smallcase", "mutual fund", "mutual funds", "sip",
            "nps", "nps trust", "ppf", "sukanya", "iccl", "indian clearing", "nsdl", "cdsl", "bse ltd", "nse clearing",
            "icici direct", "icicidirect", "hdfc securities", "kotak securities", "sharekhan", "motilal oswal",
            "5paisa", "dhan", "indmoney", "ind money", "fixed deposit", "recurring deposit", "safegold", "augmont",
            "mmtc pamp", "digital gold", "jar app", "gullak", "wint wealth", "goldenpi", "stable money",
            "camsonline", "cams", "kfintech", "bse star mf", "mf utilities",
        ),
        "Insurance" to words(
            "lic", "lic of india", "life insurance", "policybazaar", "policy bazaar", "hdfc life", "icici prudential",
            "icici pru", "sbi life", "max life", "tata aia", "bajaj allianz", "star health", "care health",
            "niva bupa", "max bupa", "hdfc ergo", "icici lombard", "acko", "go digit", "digit insurance",
            "new india assurance", "united india insurance", "oriental insurance", "national insurance",
            "insurance premium", "premium payment", "renewal premium", "insurance",
        ),
        "Entertainment" to words(
            "netflix", "hotstar", "disney", "jiocinema", "jio cinema", "jiohotstar", "prime video", "amazon prime",
            "primevideo", "sonyliv", "sony liv", "zee5", "voot", "altbalaji", "mx player", "sun nxt",
            "spotify", "gaana", "jiosaavn", "saavn", "wynk", "youtube", "youtube premium", "apple music",
            "itunes", "bookmyshow", "book my show", "pvr", "inox", "cinepolis", "district",
            "paytm insider", "steam", "playstation", "xbox", "epic games", "google play",
            "dream11", "my11circle", "mpl", "winzo", "zupee", "rummy", "pokerbaazi", "imagica", "wonderla",
            "smaaash", "timezone", "fun city",
        ),
        "Food" to words(
            "swiggy", "zomato", "eatsure", "faasos", "behrouz", "box8", "freshmenu", "rebel foods", "dominos",
            "domino's", "domino", "pizza hut", "pizzahut", "kfc", "mcdonald", "mcdonalds", "mcdonald's", "mcd",
            "burger king", "burgerking", "subway", "starbucks", "chaayos", "chai point", "third wave",
            "blue tokai", "costa coffee", "cafe coffee day", "ccd", "haldiram", "haldirams", "bikanervala",
            "barbeque nation", "bbq nation", "wow momo", "taco bell", "baskin", "naturals ice cream",
            "keventers", "theobroma", "lapinoz", "la pinoz", "ovenstory", "magicpin", "dineout", "eazydiner",
            "eatclub", "eat club", "biryani by kilo", "paradise biryani", "meghana", "truffles",
            "mainland china", "sagar ratna", "a2b", "adyar ananda bhavan", "saravana bhavan", "mtr",
            "vasudev adiga", "rameshwaram cafe", "kailash parbat", "burger singh", "krispy kreme", "dunkin",
            "tim hortons", "bundl technologies",
        ),
        "Shopping" to words(
            "amazon", "amazon.in", "amzn", "flipkart", "myntra", "ajio", "meesho", "nykaa", "tata cliq", "tatacliq",
            "snapdeal", "shopsy", "firstcry", "lenskart", "bewakoof", "decathlon", "lifestyle", "westside",
            "pantaloons", "max fashion", "zudio", "reliance trends", "shoppers stop", "h&m", "uniqlo", "zara",
            "croma", "reliance digital", "vijay sales", "ikea", "pepperfry", "urban ladder", "boat", "apple store",
            "samsung", "mi store", "xiaomi", "oneplus", "puma", "nike", "adidas", "bata", "metro shoes",
            "titan", "tanishq", "caratlane", "malabar gold", "kalyan jewellers", "purplle", "mamaearth",
            "sugar cosmetics", "the souled store", "snitch", "fabindia", "manyavar", "biba",
            "levis", "levi's", "jockey", "us polo", "allen solly", "peter england", "van heusen", "raymond",
            "hamleys", "miniso", "kindle", "audible",
        ),
        "Travel" to words(
            "uber", "ola", "olacabs", "ola cabs", "ani technologies", "rapido", "roppen", "namma yatri", "blusmart",
            "meru", "quick ride", "indrive", "irctc", "indian railway", "indian railways", "makemytrip",
            "make my trip", "goibibo", "cleartrip", "yatra", "ixigo", "easemytrip", "ease my trip", "redbus",
            "abhibus", "indigo", "interglobe", "air india", "vistara", "spicejet", "akasa", "airasia",
            "air asia", "metro", "dmrc", "bmrcl", "mmrcl", "mumbai metro", "delhi metro", "namma metro",
            "best bus", "ksrtc", "msrtc", "tsrtc", "apsrtc", "gsrtc", "upsrtc", "fastag", "netc", "toll plaza",
            "parking", "oyo", "airbnb", "booking.com", "agoda", "treebo", "fabhotels", "zostel", "zoomcar",
            "yulu", "bounce", "vogo", "royal brothers", "chalo", "railyatri",
        ),
        "Fuel" to words(
            "petrol", "diesel", "fuel", "hpcl", "hp pay", "hppay", "bpcl", "iocl", "indian oil", "indianoil",
            "bharat petroleum", "hindustan petroleum", "shell", "nayara", "essar oil", "jio-bp", "jio bp", "jiobp",
            "filling station", "service station", "petroleum", "cng", "ev charging", "statiq", "chargezone",
            "tata power ez", "ather grid",
        ),
        "Bills" to words(
            "electricity", "bescom", "msedcl", "mahadiscom", "tata power", "adani electricity", "bses", "tpddl",
            "cesc", "tneb", "tangedco", "kseb", "uppcl", "pspcl", "torrent power", "mseb", "apspdcl", "tsspdcl",
            "jvvnl", "avvnl", "dhbvn", "uhbvn", "wbsedcl", "water bill", "jal board", "bwssb", "gas bill",
            "mahanagar gas", "indraprastha gas", "igl", "adani gas", "gujarat gas", "indane", "hp gas", "bharat gas",
            "lpg", "airtel", "jio", "reliance jio", "vodafone", "vodafone idea", "vi prepaid", "vi postpaid",
            "bsnl", "mtnl", "act fibernet", "hathway", "excitel", "you broadband", "tikona", "broadband",
            "recharge", "postpaid", "prepaid", "dth", "tata play", "tatasky", "tata sky", "dish tv", "dishtv",
            "d2h", "airtel digital tv", "sun direct", "bbps", "billdesk", "bill payment", "bill pay", "billpay",
            "property tax", "municipal", "society maintenance", "maintenance charges", "mygate", "nobrokerhood",
            "google one", "icloud", "microsoft", "adobe", "chatgpt", "openai",
        ),
        "Rent" to words("rent", "house rent", "room rent", "pg rent", "nobroker", "housing.com", "nestaway", "stanza living", "zolo", "magicbricks"),
        "Health" to words(
            "pharmacy", "pharma", "chemist", "chemists", "medical", "medicals", "medicos", "apollo", "apollo pharmacy",
            "medplus", "netmeds", "1mg", "tata 1mg", "pharmeasy", "truemeds", "practo", "hospital", "hospitals",
            "clinic", "diagnostic", "diagnostics", "pathlab", "path lab", "thyrocare", "dr lal", "lal pathlabs",
            "metropolis", "healthians", "redcliffe", "max healthcare", "fortis", "manipal hospital", "narayana",
            "aster", "cloudnine", "rainbow hospital", "dental", "dentist", "eye care", "optical", "cult.fit",
            "cultfit", "curefit", "gym", "fitness", "anytime fitness", "healthifyme", "pristyn care",
        ),
        "Personal care" to words(
            "salon", "salons", "parlour", "parlor", "beauty parlour", "spa", "barber", "urban company",
            "urbancompany", "urbanclap", "lakme salon", "naturals salon", "jawed habib", "enrich salon",
            "looks salon", "bodycraft", "yes madam", "grooming",
        ),
        "Education" to words(
            "school", "college", "university", "institute", "academy", "classes", "tuition", "coaching",
            "udemy", "coursera", "byju", "byjus", "byju's", "unacademy", "vedantu", "upgrad", "simplilearn",
            "physicswallah", "physics wallah", "testbook", "duolingo", "skillshare", "edx", "great learning",
            "scaler", "allen career", "aakash", "fiitjee", "school fees", "college fees", "exam fee",
        ),
        "EMI/Loan" to words(
            "emi", "loan", "loan repayment", "bajaj finserv", "bajaj finance", "home credit", "hdb financial",
            "tata capital", "muthoot", "manappuram", "kreditbee", "moneyview", "money view", "navi finserv", "navi technologies", "lazypay",
            "simpl", "zestmoney", "paysense", "cashe", "earlysalary", "fibe", "mpokket", "moneytap", "dmi finance",
            "l&t finance", "aditya birla finance", "piramal finance", "shriram finance", "iifl", "lendingkart",
        ),
        "Charges & fees" to words(
            "annual fee", "joining fee", "renewal fee", "late fee", "late payment fee", "late payment charges",
            "finance charge", "finance charges", "sms charges", "sms alert charges", "service charge",
            "service charges", "amc", "penal charges", "cash advance fee", "processing fee", "convenience fee",
            "non maintenance", "min bal charges", "minimum balance charges", "chq bounce", "cheque bounce",
            "ecs return", "nach return", "forex markup", "cross currency markup", "interest charged",
        ),
        "Gifts & donations" to words(
            "donation", "donate", "charity", "temple", "mandir", "devasthanam", "gurudwara", "church", "masjid",
            "dargah", "foundation", "ngo", "giveindia", "give india", "ketto", "milaap",
            "akshaya patra", "iskcon", "gift card", "gift voucher",
        ),
    )

    // ------------------------------------------------------------- shop types
    /** Generic words in a merchant name that reveal the kind of shop. Checked on the merchant only. */
    private val shopTypes: List<Pair<String, Regex>> = listOf(
        "Health" to words(
            "medical", "medicals", "medicos", "pharma", "pharmacy", "chemist", "chemists", "druggist", "drug house",
            "hospital", "clinic", "diagnostic", "diagnostics", "lab", "labs", "dental", "nursing home",
            "healthcare", "ayurveda", "ayurvedic", "homeopathy", "opticals", "optical",
        ),
        "Fuel" to words("petroleum", "fuels", "fuel", "filling station", "service station", "petrol pump", "auto fuel"),
        "Personal care" to words("salon", "parlour", "parlor", "spa", "barber", "beauty", "unisex"),
        "Education" to words("school", "college", "academy", "institute", "classes", "coaching", "tutorial", "tutorials", "vidyalaya", "education"),
        "Shopping" to words(
            "fashion", "fashions", "garments", "garment", "textile", "textiles", "footwear", "shoes", "clothing",
            "apparel", "boutique", "jewellers", "jewellery", "jewelers", "electronics", "mobiles", "mobile",
            "computers", "furniture", "furnishing", "hardware", "stationery", "stationers", "gifts", "collection",
            "emporium", "cosmetics", "toys", "sports", "watch", "watches", "opticians", "book store", "books",
        ),
        "Food" to words(
            "restaurant", "restro", "resto", "hotel", "dhaba", "cafe", "caterers", "catering", "bakery", "bakers",
            "sweets", "sweet", "mithai", "foods", "food", "kitchen", "biryani", "pizza", "pizzeria", "juice",
            "tea", "chai", "canteen", "mess", "bhojanalaya", "tiffin", "ice cream", "icecream", "momos", "burger",
            "dosa", "idli", "chinese", "fast food", "snacks", "tea stall", "coffee", "brewery", "pub", "bar",
            "lounge", "wines", "liquor",
        ),
        "Groceries" to words(
            "kirana", "provision", "provisions", "general store", "general stores", "supermarket", "super market",
            "hypermarket", "mart", "grocery", "groceries", "vegetable", "vegetables", "fruits", "fruit", "dairy",
            "milk", "departmental", "department store",
        ),
        "Travel" to words("travels", "travel", "tours", "cabs", "cab", "taxi", "transport", "parking", "rentals"),
        "Bills" to words("telecom", "broadband", "internet", "cable", "networks", "electric"),
    )

    // ---------------------------------------------------------- payment clues
    private val investClue = Regex("""\b(sip|mutual fund|iccl|nsdl|cdsl|clearing corp)\b""", IC)
    private val emiClue = Regex("""\b(nach|ecs|e-?mandate|mandate|standing instruction)\b.*\b(loan|emi|finance)\b|\bemi\b""", IC)
    private val travelClue = Regex("""\b(fastag|netc|toll)\b""", IC)
    private val billClue = Regex("""\b(bbps|bill\s?pay|billpay|bill payment|recharge|electricity)\b""", IC)

    // ------------------------------------------------------- person transfers
    private val phoneVpa = Regex("""\b\d{10}@""")
    private val p2pClue = Regex("""\b(upi|imps|neft|rtgs|vpa|p2p|p2a|trf|transfer|transferred|sent)\b|@""", IC)
    private val businessWords = setOf(
        "ltd", "limited", "pvt", "private", "llp", "inc", "corp", "co", "company", "store", "stores", "mart",
        "shop", "shoppe", "enterprises", "enterprise", "traders", "trading", "services", "service", "solutions",
        "technologies", "technology", "tech", "industries", "agency", "agencies", "center", "centre", "foods",
        "hotel", "restaurant", "medical", "medicals", "pharma", "motors", "bank", "india", "retail", "bazaar",
        "kirana", "general", "sweets", "bakery", "cafe", "travels", "petroleum", "fuels", "hospital", "clinic",
        "studio", "salon", "fashion", "garments", "electronics", "mobiles", "collection", "payments", "merchant",
        "online", "digital", "express", "logistics", "finance", "capital", "money", "insurance", "school",
        "academy", "institute", "associates", "brothers", "bros", "sons", "and", "the", "club", "infra",
        "ventures", "global", "international", "systems", "network", "networks", "works", "house", "point",
        "world", "hub", "zone", "plaza", "mall", "market", "wala", "wale", "dairy", "farm", "kendra", "bhandar",
    )

    /** "Rahul Sharma" / "PRIYA K" with a UPI/IMPS clue → probably a person, not a shop. */
    fun looksLikePerson(merchant: String, body: String): Boolean {
        val parts = merchant.lowercase().split(Regex("[\\s._]+")).filter { it.isNotBlank() }
        if (parts.size !in 2..4) return false
        if (parts.any { w -> w in businessWords || w.any { !it.isLetter() } }) return false
        if (parts.none { it.length >= 3 }) return false
        return p2pClue.containsMatchIn(body)
    }

    // ------------------------------------------------------------------ income
    private val salaryRx = Regex("""\b(salary|sal|payroll|stipend|wages)\b""", IC)
    private val dividendRx = Regex("""\b(dividend|div)\b""", IC)
    private val interestRx = Regex("""\b(interest|int\.?\s*(?:pd|paid|cr|credited|credit)|sb\s*int)\b""", IC)
    private val refundRx = Regex("""\b(refund|refunded|cashback|cash back|reversal|reversed|chargeback|reward|rewards)\b""", IC)
    private val rentRx = Regex("""\b(rent|rental)\b""", IC)
    private val atmRx = Regex("""\b(atm|cash withdrawal|cash wdl|nfs)\b""", IC)
    private val cashRx = Regex("""withdr|wdl|cash""", IC)

    private fun firstBrand(text: String): String? = brands.firstOrNull { it.second.containsMatchIn(text) }?.first
    private fun firstShop(text: String): String? = shopTypes.firstOrNull { it.second.containsMatchIn(text) }?.first

    fun categorize(merchant: String, body: String, isDebit: Boolean, rule: String?): String {
        if (rule != null) return rule
        if (!isDebit) return incomeCategory(merchant, body)

        if (atmRx.containsMatchIn(body) && cashRx.containsMatchIn(body)) return "ATM/Cash"
        firstBrand(merchant)?.let { return it }
        firstShop(merchant)?.let { return it }
        firstBrand(body)?.let { return it }
        when {
            investClue.containsMatchIn(body) -> return "Investment"
            emiClue.containsMatchIn(body) -> return "EMI/Loan"
            travelClue.containsMatchIn(body) -> return "Travel"
            billClue.containsMatchIn(body) -> return "Bills"
        }
        if (phoneVpa.containsMatchIn(body) || merchant.all { it.isDigit() } || looksLikePerson(merchant, body)) return "Transfers"
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
