package com.expensetracker.sms

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.DataInputStream
import java.io.InputStream
import kotlin.math.exp
import kotlin.math.ln

/**
 * On-device category model: Naive Bayes over hashed features of the merchant name
 * (words, word pairs and 3–5 letter chunks). Trained by scripts/train_category_model.py;
 * feature extraction here must match that script exactly.
 *
 * Nothing leaves the phone: the model ships inside the APK and the app has no internet permission.
 */
class CategoryModel private constructor(
    val buckets: Int,
    private val alpha: Float,
    val temperature: Float,
    val classes: List<String>,
    private val docs: FloatArray,
    private val totals: FloatArray,
    private val start: IntArray,     // CSR index: entries for bucket b are [start[b], start[b+1])
    private val cls: ShortArray,
    private val cnt: FloatArray,
    private val featurizer: (String) -> List<String> = ::features,
    /** Ignore features the model never saw (else small classes win on unknown words). */
    private val skipUnseen: Boolean = false,
) {
    companion object {
        fun read(input: InputStream, featurizer: (String) -> List<String> = ::features, skipUnseen: Boolean = false): CategoryModel {
            val d = DataInputStream(input.buffered())
            val magic = ByteArray(4).also { d.readFully(it) }
            require(String(magic) == "ETCM") { "Not a category model" }
            d.readInt() // version
            val buckets = d.readInt()
            val k = d.readInt()
            val alpha = d.readFloat()
            val temp = d.readFloat()
            val names = ArrayList<String>(k)
            val docs = FloatArray(k)
            val totals = FloatArray(k)
            repeat(k) { i ->
                val len = d.readShort().toInt()
                val b = ByteArray(len).also { d.readFully(it) }
                names += String(b, Charsets.UTF_8)
                docs[i] = d.readFloat()
                totals[i] = d.readFloat()
            }
            val n = d.readInt()
            val bucketOf = IntArray(n)
            val cls = ShortArray(n)
            val cnt = FloatArray(n)
            for (i in 0 until n) {
                bucketOf[i] = d.readInt()
                cls[i] = d.readShort()
                cnt[i] = d.readUnsignedShort().toFloat()
            }
            // Entries are sorted by bucket in the file; build the CSR index.
            val start = IntArray(buckets + 1)
            for (b in bucketOf) start[b + 1]++
            for (i in 1..buckets) start[i] += start[i - 1]
            return CategoryModel(buckets, alpha, temp, names, docs, totals, start, cls, cnt, featurizer, skipUnseen)
        }

        fun normalize(s: String) = s.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex(" +"), " ").trim()

        fun features(name: String): List<String> {
            val out = ArrayList<String>()
            val words = normalize(name).split(' ').filter { it.isNotEmpty() }
            for (w in words) {
                if (w.length >= 2) out += "w:$w"
                val p = "#$w#"
                for (n in 3..5) for (i in 0..p.length - n) out += "c:" + p.substring(i, i + n)
            }
            for (i in 0 until words.size - 1) out += "b:${words[i]}_${words[i + 1]}"
            return out
        }

        // Must match sms_features in scripts/train_sms_model.py exactly.
        private val SMS_STOP = "www pay ltd pvt limited private rs inr on to from ac a c the your you is has been for of by and or in at with via upi ref no not call sms block avl bal balance info txn dear customer user bank card credit debit debited credited dr cr amt amount date dt if done tap https http www com in co kotak hdfc icici sbi axis union india bob canara pnb idfc yes indusind federal reissue fraud help helpline please immediately report us our available limit lmt total refno rrn utr mob bk mb fv fvg vpa sent spent paid paying payment trf transfer transferred mr mrs ms account acct xx xxxx a/c x".split(' ').toSet()
        private val smsVpaRx = Regex("([a-z0-9._\\-]{2,})@[a-z]{2,}")
        private val nonLetters = Regex("[^a-z ]")

        fun smsWords(body: String): List<String> {
            val t = smsVpaRx.replace(body.lowercase()) { " " + it.groupValues[1] + " " }.replace(nonLetters, " ")
            return t.split(' ').filter { it.length >= 2 && it !in SMS_STOP }
        }

        /** Features of a whole SMS: words, 4-letter pieces of longer words, and word pairs. */
        fun smsFeatures(body: String): List<String> {
            val words = smsWords(body)
            val out = ArrayList<String>()
            for (w in words) {
                out += "s:$w"
                if (w.length >= 4) {
                    val p = "#$w#"
                    for (i in 0..p.length - 4) out += "q:" + p.substring(i, i + 4)
                }
            }
            for (i in 0 until words.size - 1) out += "t:${words[i]}_${words[i + 1]}"
            return out
        }

        fun fnv(s: String): Int {
            var h = 0x811C9DC5.toInt()
            for (byte in s.toByteArray(Charsets.UTF_8)) {
                h = h xor (byte.toInt() and 0xFF)
                h *= 16777619
            }
            return h
        }
    }

    fun bucket(feature: String) = (fnv(feature) and 0x7FFFFFFF) % buckets

    /**
     * Probability per category, highest first. [user] adds the user's own corrections on top
     * of the shipped model (each correction weighs [boost] training examples).
     */
    fun predict(name: String, user: UserCounts? = null, boost: Float = 25f): List<Pair<String, Double>> {
        val feats = featurizer(name).map { bucket(it) }
        if (feats.isEmpty()) return emptyList()
        val all = (classes + (user?.classes() ?: emptySet())).distinct()
        val k = all.size
        val baseIndex = IntArray(k) { classes.indexOf(all[it]) }
        val nBase = docs.sum()
        val nUser = user?.totalDocs() ?: 0f
        val scores = DoubleArray(k)
        for (c in 0 until k) {
            val bi = baseIndex[c]
            val name = all[c]
            val d = (if (bi >= 0) docs[bi] else 0f) + boost * (user?.docs(name) ?: 0f)
            scores[c] = ln((d + 1.0) / (nBase + boost * nUser + k))
        }
        val row = FloatArray(classes.size)
        for (b in feats) {
            if (skipUnseen && start[b] == start[b + 1] && user == null) continue
            row.fill(0f)
            for (i in start[b] until start[b + 1]) row[cls[i].toInt()] = cnt[i]
            for (c in 0 until k) {
                val bi = baseIndex[c]
                val name = all[c]
                val count = (if (bi >= 0) row[bi] else 0f) + boost * (user?.count(b, name) ?: 0f)
                val total = (if (bi >= 0) totals[bi] else 0f) + boost * (user?.total(name) ?: 0f)
                scores[c] += ln((count + alpha) / (total + alpha.toDouble() * buckets))
            }
        }
        val max = scores.max()
        val e = DoubleArray(k) { exp((scores[it] - max) / temperature) }
        val z = e.sum()
        return all.indices.map { all[it] to e[it] / z }.sortedByDescending { it.second }
    }
}

/** The user's own corrections, kept in SQLite on this phone. */
class UserCounts {
    private val counts = HashMap<Int, HashMap<String, Float>>()
    private val docs = HashMap<String, Float>()
    private val totals = HashMap<String, Float>()

    fun classes(): Set<String> = docs.keys
    fun totalDocs() = docs.values.sum()
    fun docs(c: String) = docs[c] ?: 0f
    fun total(c: String) = totals[c] ?: 0f
    fun count(bucket: Int, c: String) = counts[bucket]?.get(c) ?: 0f
    val corrections: Int get() = docs.values.sum().toInt()

    fun add(buckets: List<Int>, c: String, w: Float) {
        docs[c] = docs(c) + w
        totals[c] = total(c) + w * buckets.size
        for (b in buckets) counts.getOrPut(b) { HashMap() }.let { it[c] = (it[c] ?: 0f) + w }
    }

    /** Restore one stored row (used when loading from the database). */
    fun load(bucket: Int, c: String, w: Float) {
        counts.getOrPut(bucket) { HashMap() }[c] = w
        totals[c] = total(c) + w
    }

    fun setDocs(c: String, d: Float) {
        docs[c] = d
    }

    fun clear() {
        counts.clear(); docs.clear(); totals.clear()
    }
}

/** App-facing wrapper: loads the model once, applies the user's learning, persists corrections. */
object CategoryAI {
    /** Apply the AI's guess only above this confidence. */
    const val APPLY = 0.65
    /** Below this, the guess is shown for review. */
    const val SURE = 0.85
    /** Categories the model may suggest from a name alone. */
    private val notFromName = setOf("ATM/Cash", "Charges & fees", Categories.SELF, Categories.OTHERS)

    @Volatile private var model: CategoryModel? = null
    @Volatile private var smsModel: CategoryModel? = null
    /** How much the merchant-name model counts when both models have an opinion. */
    private const val NAME_WEIGHT = 0.55
    private val user = UserCounts()
    @Volatile private var userLoaded = false

    private fun model(ctx: Context): CategoryModel? = model ?: synchronized(this) {
        model ?: runCatching { ctx.assets.open("category_model.bin").use { CategoryModel.read(it) } }.getOrNull()?.also { model = it }
    }

    private fun smsModel(ctx: Context): CategoryModel? = smsModel ?: synchronized(this) {
        smsModel ?: runCatching { ctx.assets.open("sms_model.bin").use { CategoryModel.read(it, CategoryModel::smsFeatures) } }
            .getOrNull()?.also { smsModel = it }
    }

    private val cueWords = mapOf(
        "ATM/Cash" to Regex("""\b(atm|cash|withdrawn|withdrawal|wdl|nfs)\b""", RegexOption.IGNORE_CASE),
        "Charges & fees" to Regex("""\b(fee|fees|charge|charges|gst|penalty|amc)\b""", RegexOption.IGNORE_CASE),
    )

    /** What the whole SMS suggests. Empty when the SMS itself says nothing about the purchase. */
    fun predictSms(ctx: Context, body: String): List<Pair<String, Double>> {
        val m = smsModel(ctx) ?: return emptyList()
        val raw = synchronized(this) { m.predict(body) }
        if (raw.firstOrNull()?.first == Categories.OTHERS) return emptyList()
        // Cash and fees only when the SMS actually says so.
        raw.firstOrNull()?.first?.let { top -> cueWords[top]?.let { if (!it.containsMatchIn(body)) return emptyList() } }
        val valid = Categories.expense.toSet()
        val keep = raw.filter { it.first in valid && it.first != Categories.SELF && it.first != Categories.OTHERS }
        val z = keep.sumOf { it.second }.takeIf { it > 0 } ?: return emptyList()
        return keep.map { it.first to it.second / z }
    }

    private fun ensureUser(ctx: Context) {
        if (userLoaded) return
        synchronized(this) {
            if (userLoaded) return
            val db = Db.get(ctx).readableDatabase
            db.rawQuery("SELECT bucket, cls, count FROM ml_user", null).use { c ->
                while (c.moveToNext()) user.load(c.getInt(0), c.getString(1), c.getFloat(2))
            }
            db.rawQuery("SELECT cls, docs FROM ml_user_docs", null).use { c ->
                while (c.moveToNext()) user.setDocs(c.getString(0), c.getFloat(1))
            }
            userLoaded = true
        }
    }

    /** A merchant "name" that is really a code (QR id, reference number) tells the model nothing. */
    fun usableName(merchant: String): Boolean {
        val n = CategoryModel.normalize(merchant)
        if (n.length < 3) return false
        val digits = n.count { it.isDigit() }
        return digits < 3 && n.any { it.isLetter() }
    }

    /**
     * Top guesses for an expense: the merchant-name model (with your corrections) blended with
     * the whole-SMS model when the SMS text is given. All on the phone.
     */
    fun predict(ctx: Context, merchant: String, body: String?): List<Pair<String, Double>> {
        val byName = predict(ctx, merchant)
        val bySms = body?.takeIf { it.isNotBlank() }?.let { runCatching { predictSms(ctx, it) }.getOrDefault(emptyList()) }.orEmpty()
        if (bySms.isEmpty()) return byName
        if (byName.isEmpty()) return bySms
        val a = byName.toMap()
        val b = bySms.toMap()
        return (a.keys + b.keys).map { c -> c to NAME_WEIGHT * (a[c] ?: 0.0) + (1 - NAME_WEIGHT) * (b[c] ?: 0.0) }
            .sortedByDescending { it.second }
    }

    /** Top guesses from the merchant name alone, restricted to existing expense categories. */
    fun predict(ctx: Context, merchant: String): List<Pair<String, Double>> {
        if (!usableName(merchant)) return emptyList()
        val m = model(ctx) ?: return emptyList()
        ensureUser(ctx)
        val valid = Categories.expense.toSet()
        val raw = synchronized(this) { m.predict(merchant, user) }
            .filter { it.first in valid && it.first !in notFromName }
        val z = raw.sumOf { it.second }.takeIf { it > 0 } ?: return emptyList()
        return raw.map { it.first to it.second / z }
    }

    /** Best guess if it's confident enough to apply automatically. */
    fun suggest(ctx: Context, merchant: String, body: String? = null): Pair<String, Double>? =
        predict(ctx, merchant, body).firstOrNull()?.takeIf { it.second >= APPLY }

    /** Learn from the user: [weight] 1 = a correction, 0.5 = a confirmation. */
    fun learn(ctx: Context, merchant: String, category: String, weight: Float = 1f) {
        if (!usableName(merchant) || category !in Categories.expense || category in notFromName) return
        val m = model(ctx) ?: return
        ensureUser(ctx)
        val buckets = CategoryModel.features(merchant).map { m.bucket(it) }.distinct()
        synchronized(this) { user.add(buckets, category, weight) }
        val db = Db.get(ctx).writableDatabase
        // Plain UPDATE-then-INSERT: works on the older SQLite of Android 8–10 (no upsert syntax there).
        val upd = db.compileStatement("UPDATE ml_user SET count = count + ? WHERE bucket = ? AND cls = ?")
        val ins = db.compileStatement("INSERT INTO ml_user(bucket, cls, count) VALUES(?, ?, ?)")
        db.beginTransaction()
        try {
            buckets.forEach { b ->
                upd.clearBindings(); upd.bindDouble(1, weight.toDouble()); upd.bindLong(2, b.toLong()); upd.bindString(3, category)
                if (upd.executeUpdateDelete() == 0) {
                    ins.clearBindings(); ins.bindLong(1, b.toLong()); ins.bindString(2, category); ins.bindDouble(3, weight.toDouble())
                    ins.executeInsert()
                }
            }
            val cv = ContentValues().apply { put("cls", category); put("docs", user.docs(category).toDouble()) }
            db.insertWithOnConflict("ml_user_docs", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun corrections(ctx: Context): Int {
        ensureUser(ctx)
        return user.corrections
    }

    fun reset(ctx: Context) {
        val db = Db.get(ctx).writableDatabase
        db.delete("ml_user", null, null)
        db.delete("ml_user_docs", null, null)
        synchronized(this) { user.clear() }
    }

    /**
     * Re-check "Others" transactions (used once after upgrading). With [reparse], the SMS is read
     * again with the newer parser first, so payees it used to miss ("Fvg: …", "wallet for …") show up.
     */
    fun sortOthers(ctx: Context, reparse: Boolean = false): Int {
        val db = Db.get(ctx)
        var n = 0
        db.allTxns().filter { it.isDebit && it.category == Categories.OTHERS && !it.aiReviewed }
            .forEach { t0 ->
                var t = t0
                if (reparse && t.smsKey != null) {
                    (SmsParser.classify("AD-BANK", t.body, lenient = true) as? ParseResult.Ok)?.txn?.merchant
                        ?.takeIf { it != "BANK" && it != t.merchant }
                        ?.let { t = t.copy(merchant = it) }
                }
                val rule = db.ruleFor(t.merchant)
                val cat = rule ?: Categorizer.categorize(t.merchant, t.body, true, null)
                when {
                    cat == Categories.SELF -> { db.update(SelfTransfer.mark(t)); n++ }
                    cat != Categories.OTHERS -> { db.update(t.copy(category = cat)); n++ }
                    else -> {
                        val s = suggest(ctx, t.merchant, t.body)
                        if (s != null) {
                            db.update(t.copy(category = s.first, aiConf = s.second)); n++
                        } else if (t !== t0) {
                            db.update(t) // at least keep the better merchant name
                        }
                    }
                }
            }
        return n
    }
}
