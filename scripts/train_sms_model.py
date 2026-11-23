#!/usr/bin/env python3
"""
Trains the on-device SMS-text category model and writes app/src/main/assets/sms_model.bin.

The merchant model (train_category_model.py) only sees the merchant name the parser pulled out.
This one reads the whole SMS, so it also catches clues the parser can't turn into a name:
"by ATM", "premium for policy", "EMI", "electricity bill", "Fvg: <name>", a merchant hidden in
an odd bank format, and so on. The app blends both models.

Training data is generated: realistic Indian bank SMS formats filled with merchant / shop /
person names from the merchant model's generator. Real SMS are never put in the model; pass
--eval <file.csv|xlsx> (a Settings → Export with the SMS column) to measure accuracy on your own
messages instead.

Run:  python3 scripts/train_sms_model.py [--eval my-export.xlsx]
"""
import math, random, re, struct, sys
from collections import defaultdict, Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import train_category_model as tcm  # noqa: E402  (shared generator, hashing and NB helpers)

ROOT = tcm.ROOT
OUT = ROOT / "app/src/main/assets/sms_model.bin"
BUCKETS = 1 << 15
ALPHA = 0.2
random.seed(7)

# ---------------------------------------------------------------- features
# Must match CategoryModel.smsFeatures in CategoryAI.kt exactly.
STOP = set("""
www pay ltd pvt limited private
rs inr on to from ac a c the your you is has been for of by and or in at with via upi ref no not call sms block
avl bal balance info txn dear customer user bank card credit debit debited credited dr cr amt amount date dt
if done tap https http www com in co kotak hdfc icici sbi axis union india bob canara pnb idfc yes indusind federal
reissue fraud help helpline please immediately report us our available limit lmt total refno rrn utr mob bk mb fv fvg
vpa sent spent paid paying payment trf transfer transferred mr mrs ms account acct xx xxxx a/c x
""".split())
VPA_RX = re.compile(r"([a-z0-9._\-]{2,})@[a-z]{2,}")

def sms_words(body):
    t = body.lower()
    t = VPA_RX.sub(lambda m: " " + m.group(1) + " ", t)
    t = re.sub(r"[^a-z ]", " ", t)
    return [w for w in t.split(" ") if len(w) >= 2 and w not in STOP]

def sms_features(body):
    words = sms_words(body)
    out = []
    for w in words:
        out.append("s:" + w)
        if len(w) >= 4:
            p = "#" + w + "#"
            for i in range(len(p) - 3):
                out.append("q:" + p[i:i + 4])
    for a, b in zip(words, words[1:]):
        out.append("t:" + a + "_" + b)
    return out

def bucket(f):
    return (tcm.fnv(f) & 0x7FFFFFFF) % BUCKETS

# ---------------------------------------------------------------- generator
BANKS = ["HDFC Bank", "ICICI Bank", "SBI", "Axis Bank", "Kotak Bank", "Union Bank of India", "Bank of Baroda",
         "Canara Bank", "PNB", "IDFC FIRST Bank", "Yes Bank", "IndusInd Bank", "Federal Bank"]
HANDLES = ["ybl", "okicici", "okaxis", "oksbi", "okhdfcbank", "paytm", "axl", "ibl", "upi", "apl", "icici", "hdfcbank", "yesbank"]

def num(n):
    return "".join(random.choice("0123456789") for _ in range(n))

def amt():
    a = random.choice([random.uniform(5, 300), random.uniform(100, 3000), random.uniform(1000, 50000)])
    return random.choice([f"{a:.2f}", f"{int(a)}", f"{a:,.2f}"])

def day():
    d, m, y = random.randint(1, 28), random.randint(1, 12), random.choice([25, 26])
    return random.choice([f"{d:02d}-{m:02d}-{y}", f"{d:02d}-{m:02d}-20{y}", f"{d:02d}/{m:02d}/{y}",
                          f"20{y}-{m:02d}-{d:02d}", f"{d:02d}{['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'][m-1]}{y}"])

def vpa(name):
    slug = re.sub(r"[^a-z0-9]", "", name.lower())[:14] or "pay"
    if random.random() < 0.3:
        slug += num(random.randint(1, 3))
    return slug + "@" + random.choice(HANDLES)

# {m} name as written, {M} upper case, {v} a VPA made from the name
MERCHANT_FORMATS = [
    "Sent Rs.{a} from {B} A/c X{n} to {m} on {d}. UPI Ref {r}. Not done by you? Tap https://bank.in/Fraud",
    "Sent Rs.{a} from {B} AC X{n} to {v} on {d}.UPI Ref {r}. Not you, https://bank.in/Fraud",
    "Spent Rs.{a} On {B} Card {n} At {M} On {d}.Not You? To Block+Reissue Call {p}/SMS BLOCK CC {n} to {p}",
    "Rs.{a} spent on {B} Card x{n} at {M} on {d}.Not U? To Block & Reissue Call {p}",
    "{B} A/c *{n} Debited Rs:{a} on {d} by Mob Bk ref no {r}, Fvg: {M} Avl Bal Rs:{b}. Not you?Call {p}",
    "{B} A/c*{n} Dr Rs{a} on {d} by MB {r} Fv.{m}, Bal Rs{b} Not U?Call {p}",
    "Rs.{a} Dr. from A/C XXXXXX{n} and Cr. to {v}. Ref:{r}. AvlBal:Rs{b}. Not you? Call {p}-BOB",
    "You've paid Rs.{a} via PhonePe wallet for {m}. Not you? Call us on {p}. Remaining balance: Rs.{b}.",
    "Dear UPI user A/C X{n} debited by {a} on date {d} trf to {M} Refno {r} If not u? call {p}-SBI",
    "Rs {a} debited from a/c **{n} on {d} to VPA {v} (UPI Ref No {r}). Not you? Call {p} -HDFC Bank",
    "INR {a} debited from A/c XX{n} on {d}. Info: UPI/P2M/{r}/{M}. Avl bal INR {b} -ICICI Bank",
    "Your A/c XX{n} is debited for Rs.{a} on {d} towards {M}. Avl Bal Rs.{b} -Axis Bank",
    "Rs.{a} spent on your {B} Credit Card ending {n} at {M} on {d}. Avl Lmt: Rs {b}",
    "Txn of INR {a} done on Acct XX{n} on {d} at {M}. Avl Bal: INR {b}. Not you? SMS BLOCK to {p}",
    "Paid Rs.{a} to {m} from Paytm Wallet. Txn ID {r}. Updated balance Rs.{b}",
    "{B}: Rs {a} debited via UPI from A/c XX{n} to {v} on {d}. Ref {r}. Not you? call {p}",
    "A/c XX{n} debited Rs.{a} on {d} for UPI payment to {m} ({v}). UPI Ref {r}",
    "Your A/C XXXXX{n} Debited INR {a} on {d} -Transferred to {m}. Avl Balance INR {b}-SBI",
    "Thank you for using {B} Debit Card {n} for Rs.{a} at {M} on {d}. Avl bal Rs.{b}",
    "POS txn of Rs.{a} at {M} using {B} Card XX{n} on {d}",
]

# Clues in the SMS itself, without a usable merchant name.
CUE_FORMATS = {
    "ATM/Cash": [
        "Your SB A/c *{n} Debited for Rs.{a} on {d} by ATM Avl Bal Rs.{b}, If not you, Call {p}",
        "Rs.{a} withdrawn at ATM {M2} from A/c XX{n} on {d}. Avl bal Rs.{b}",
        "Cash withdrawal of Rs {a} from {B} A/c XX{n} at NFS ATM on {d}",
        "ATM WDL Rs.{a} A/c XX{n} {d} Bal Rs.{b}",
    ],
    "Charges & fees": [
        "Rs.{a} debited from A/c XX{n} on {d} towards {fee}. Avl Bal Rs.{b}",
        "{fee} of Rs {a} charged to your {B} Card XX{n} on {d}",
        "Dear Customer, Rs.{a} has been debited from your A/c XX{n} towards {fee} incl GST",
    ],
    "EMI/Loan": [
        "EMI of Rs.{a} for Loan A/c {r} debited from A/c XX{n} on {d}",
        "Rs.{a} debited from A/c XX{n} on {d} by ACH-DR {lender} EMI. Avl Bal Rs.{b}",
        "Your loan instalment of INR {a} has been debited from A/c XX{n} on {d}",
        "NACH debit of Rs {a} towards {lender} loan EMI from A/c XX{n} on {d}",
    ],
    "Insurance": [
        "Premium of Rs.{a} for policy {r} debited from A/c XX{n} on {d}",
        "Rs.{a} paid towards renewal premium of your {insurer} policy on {d}",
        "Spent Rs.{a} On {B} Card {n} At {insurer} On {d}",
        "NACH debit Rs {a} {insurer} premium A/c XX{n} {d}",
    ],
    "Investment": [
        "Rs.{a} debited from A/c XX{n} on {d} for SIP in {fund} mutual fund",
        "NACH debit of Rs {a} for ICCL / BSE StAR MF SIP from A/c XX{n} on {d}",
        "Rs.{a} debited towards purchase of units of {fund} Fund on {d}",
        "Funds of Rs.{a} added to your {broker} trading account from A/c XX{n}",
    ],
    "Bills": [
        "Rs.{a} debited from A/c XX{n} on {d} for {bill} bill payment. Ref {r}",
        "Payment of Rs.{a} towards {bill} bill for consumer no {r} received. Thank you",
        "Recharge of Rs.{a} for mobile {p} successful via {B} A/c XX{n}",
        "Your {bill} bill of Rs {a} has been paid through BBPS from A/c XX{n} on {d}",
    ],
    "Rent": [
        "Rs.{a} sent for house rent from A/c XX{n} on {d}",
        "Rent payment of Rs.{a} debited from A/c XX{n} on {d}",
        "Rs.{a} paid towards monthly rent via NoBroker from A/c XX{n}",
    ],
    "Education": [
        "Rs.{a} debited from A/c XX{n} on {d} towards school fees for {m2} Public School",
        "Tuition fee of Rs {a} paid to {m2} Academy from A/c XX{n}",
    ],
}
FEES = ["annual fee", "debit card annual charges", "SMS alert charges", "minimum balance charges", "late payment fee",
        "GST on charges", "AMC charges", "cheque return charges", "finance charges", "processing fee", "penalty charges"]
LENDERS = ["Bajaj Finance", "HDFC Ltd", "Tata Capital", "Home Credit", "LIC Housing", "IDFC First", "Muthoot", "Fullerton"]
INSURERS = ["LIC", "LIFEINSU", "HDFC Life", "ICICI Pru Life", "Star Health", "Policybazaar Insurance", "SBI Life", "Max Life", "Acko", "Digit Insurance"]
FUNDS = ["Axis Bluechip", "Parag Parikh Flexi Cap", "SBI Small Cap", "HDFC Index", "Nippon India", "Mirae Asset"]
BROKERS = ["Zerodha", "Groww", "Upstox", "Angel One"]
BILLS = ["electricity", "BESCOM", "MSEDCL", "Tata Power", "water", "gas", "Airtel postpaid", "Jio", "broadband", "DTH"]

# "Others" = the SMS says nothing about what was bought: a QR code / number instead of a name,
# or a bank format with no payee at all. Teaches the model to say "can't tell" instead of guessing.
NO_PAYEE_FORMATS = [
    "A/c *{n} Debited for Rs:{a} on {d} by Mob Bk ref no {r} Avl Bal Rs:{b}.If not you, Call {p} -Union Bank of India",
    "Your SB A/c *{n} Debited for Rs:{a} on {d} by {how} Avl Bal Rs:{b}.If not you, Call {p}",
    "{B} A/c {n} Debited Rs:{a} on {d} by Transfer Avl Bal Rs:{b}. Not you?Call {p}",
    "Rs.{a} Dr. from A/C XXXXXX{n} and Cr. to XXXXXX{n}. Ref:{r}. AvlBal:Rs{b}. Not you? Call {p}-BOB",
    "Your A/c XX{n} is debited with Rs.{a} on {d}. Avl Bal Rs.{b}. If not you call {p}",
    "Rs.{a} debited from A/c XX{n} on {d}. UPI Ref {r}. Not you? Call {p}",
    "INR {a} debited from A/c XX{n} on {d} by IMPS ref {r}. Avl bal INR {b}",
    "Sent Rs.{a} from {B} A/c X{n} to Bank Account XXXXXXX on {d}. UPI Ref {r}.",
]
def junk_payee():
    return random.choice([
        "Q" + num(9) + "@ybl", "paytmqr" + num(10) + random.choice(["ab", "zx", "kq"]) + "@paytm", num(10) + "@ybl",
        "BHARATPE" + num(10) + "@yesbankltd", "XXXXXX" + num(4), "MAB" + num(12), "UPI" + num(12), num(10),
        "gpay-" + num(11) + "@okbizaxis", "add-money@" + random.choice(HANDLES),
    ])

def fill(fmt, name=""):
    return fmt.format(
        a=amt(), B=random.choice(BANKS), n=num(4), d=day(), r=num(12), b=amt(), p=num(10),
        m=name, M=name.upper(), v=vpa(name), M2=random.choice(["MUMBAI", "PUNE", "DELHI", "BLR", "KORAMANGALA"]),
        fee=random.choice(FEES), lender=random.choice(LENDERS), insurer=random.choice(INSURERS),
        fund=random.choice(FUNDS), broker=random.choice(BROKERS), bill=random.choice(BILLS),
        m2=random.choice(tcm.PREFIXES).title(), how=random.choice(["Transfer", "CLEARING", "BRANCH", "IMPS ref no " + num(12), "Mob Bk ref no " + num(12)]),
    )

MORE_FIRST = ("ramesh mohd mohammed abdul firoz jamil hasnain navin chirag vagataram harilal hiralal pappu ramanujam naim "
              "mukesh santosh rakesh dilip kailash gopal shankar bhagwan laxman bhimrao dattatray sandeep raju babu sonu monu "
              "lalit umesh vikash mujeeb salim saleem shabbir iqbal javed rafiq aslam yusuf usman sultan kishore pradeep jitendra "
              "manisha savita sushila kamla parvati nirmala shobha sarita poonam mamta rinku pinky bablu guddu").split()
MORE_LAST = ("khan shaikh shekh ansari qureshi pathan sayyed choudhary chaudhari jadhav gond yadav ya sahu nakti mali kumbhar "
             "vishwakarma lohar teli prajapati rathod solanki parmar chavan bhosale kamble waghmare sonawane gawde naik").split()

def person():
    f = random.choice(tcm.FIRST + MORE_FIRST)
    l = random.choice(tcm.LAST + MORE_LAST)
    mid = random.choice(tcm.FIRST + MORE_FIRST)
    name = random.choice([f"{f} {l}", f"{f} {mid} {l}", f"{f} {l[0]}", f"{f}", f"{f} {mid}", f"mr {f} {l}", f"{f} kumar {l}"])
    if random.random() < 0.5:
        name = name.upper()
    elif random.random() < 0.5:
        name = name.title()
    return name[:20].strip()  # banks cut names at ~20 characters

def build():
    names = tcm.build()  # (merchant/shop/person name, category)
    # "swiggy instamart" (Groceries) must not teach that "swiggy" alone is Groceries.
    single = {w: c for c, ws in tcm.dicts.items() for w in ws if " " not in w}
    brands = {w for ws in tcm.dicts.values() for w in ws}
    data = []
    for name, cat in names:
        if cat in ("Charges & fees", "ATM/Cash"):  # these come from cue formats only
            continue
        words = [w for w in name.split(" ") if single.get(w.lower(), cat) == cat]
        if not words:
            continue
        name = " ".join(words)
        reps = 3 if name.lower() in brands or name.lower().split(" ")[0] in brands else 1
        for _ in range(reps):
            data.append((fill(random.choice(MERCHANT_FORMATS), name), cat))
    for _ in range(3500):
        data.append((fill(random.choice(MERCHANT_FORMATS), person()), "Transfers"))
    # Cue formats get a random name from any category, so the name itself carries no signal there.
    pool = [n for n, _ in names]
    for cat, fmts in CUE_FORMATS.items():
        for _ in range(700):
            data.append((fill(random.choice(fmts), random.choice(pool)), cat))
    for _ in range(3000):
        if random.random() < 0.5:
            data.append((fill(random.choice(NO_PAYEE_FORMATS)), "Others"))
        else:
            j = junk_payee()
            body = fill(random.choice(MERCHANT_FORMATS), "x").replace("x@", "@")
            body = re.sub(r"\b(to|Fvg:|Fv\.|for|At|at|towards|trf to|Cr\. to|VPA)\s+(X|x@\w+|x)\b", lambda m: m.group(1) + " " + j, body)
            data.append((body, "Others"))
    return data

# ---------------------------------------------------------------- training
def train(data):
    counts = defaultdict(lambda: defaultdict(float))
    totals, docs = defaultdict(float), defaultdict(float)
    for body, cat in data:
        docs[cat] += 1
        for f in sms_features(body):
            b = bucket(f)
            counts[b][cat] += 1
            totals[cat] += 1
    # Scale every class to the same total, so a small class (few, short messages) doesn't win
    # just because unknown words cost it less. Only scales up, so no count rounds to zero.
    target = max(totals.values())
    for c in list(totals):
        f = target / totals[c]
        for row in counts.values():
            if c in row:
                row[c] *= f
        totals[c] = target
    return counts, totals, docs

def scores(model, body):
    counts, totals, docs = model
    n = sum(docs.values())
    k = len(docs)
    feats = [bucket(f) for f in sms_features(body)]
    s = {}
    for c in docs:
        v = math.log((docs[c] + 1) / (n + k))
        denom = totals[c] + ALPHA * BUCKETS
        for b in feats:
            v += math.log((counts[b].get(c, 0.0) + ALPHA) / denom)
        s[c] = v
    return s

def softmax(s, t):
    m = max(s.values())
    e = {c: math.exp((v - m) / t) for c, v in s.items()}
    z = sum(e.values())
    return {c: v / z for c, v in e.items()}

def load_eval(path):
    rows = []
    if path.endswith(".xlsx"):
        import openpyxl
        ws = openpyxl.load_workbook(path, read_only=True).active
        it = ws.iter_rows(values_only=True)
        head = [str(h) for h in next(it)]
        for r in it:
            rows.append(dict(zip(head, [("" if v is None else str(v)) for v in r])))
    else:
        import csv
        with open(path, newline="", encoding="utf-8-sig") as f:
            rows = list(csv.DictReader(f))
    return [(r["SMS"], r["Category"]) for r in rows if r.get("SMS")]

def main():
    data = build()
    random.shuffle(data)
    classes = sorted({c for _, c in data})
    cut = int(len(data) * 0.9)
    model = train(data[:cut])
    held = data[cut:][:4000]
    best_t, best_ll = 1.0, -1e18
    for t in [1, 2, 3, 4, 6, 8, 10, 14, 18, 24]:
        ll = sum(math.log(max(softmax(scores(model, b), t)[c], 1e-9)) for b, c in held)
        if ll > best_ll:
            best_t, best_ll = t, ll
    acc = sum(max((s := scores(model, b)), key=s.get) == c for b, c in held) / len(held)
    model = train(data)
    print(f"classes={len(classes)} examples={len(data)} temperature={best_t} held-out acc={acc:.3f}")

    if "--eval" in sys.argv:
        ev = load_eval(sys.argv[sys.argv.index("--eval") + 1])
        ev = [(b, c) for b, c in ev if "sample@example" not in b]  # anonymised payee: nothing to learn or test
        known = [(b, c) for b, c in ev if c in classes and c != "Others"]
        unknown = [b for b, c in ev if c == "Others"]
        right = applied = applied_right = 0
        for b, c in known:
            p = softmax(scores(model, b), best_t)
            top = max(p, key=p.get)
            right += top == c
            if p[top] >= 0.65:
                applied += 1
                applied_right += top == c
        print(f"eval: {len(known)} labelled rows, top-1 {right / max(1, len(known)):.3f}, "
              f"auto-applied {applied} ({applied_right / max(1, applied):.3f} right)")
        guesses = Counter()
        for b in unknown:
            p = softmax(scores(model, b), best_t)
            top = max(p, key=p.get)
            if top != "Others" and p[top] >= 0.65:
                guesses[top] += 1
        print(f"eval: {len(unknown)} 'Others' rows, model now places {sum(guesses.values())}: {dict(guesses.most_common())}")

    counts, totals, docs = model
    entries = sorted((b, classes.index(c), v) for b, row in counts.items() for c, v in row.items())
    with open(OUT, "wb") as f:
        f.write(b"ETCM")
        f.write(struct.pack(">iiiff", 2, BUCKETS, len(classes), ALPHA, float(best_t)))
        for c in classes:
            enc = c.encode("utf-8")
            f.write(struct.pack(">h", len(enc)) + enc)
            f.write(struct.pack(">ff", docs[c], totals[c]))
        f.write(struct.pack(">i", len(entries)))
        for b, ci, v in entries:
            f.write(struct.pack(">ihH", b, ci, min(int(round(v)), 65535)))
    print(f"wrote {OUT.relative_to(ROOT)} ({OUT.stat().st_size // 1024} KB, {len(entries)} entries)")
    for s in ["Spent Rs.210 On HDFC Bank Card 8422 At UBER INDIA On 2026-01-16"]:
        print("  features:", sms_features(s))

if __name__ == "__main__":
    main()
