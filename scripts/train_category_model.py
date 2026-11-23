#!/usr/bin/env python3
"""
Trains the on-device category model (Naive Bayes over hashed merchant-name features)
and writes app/src/main/assets/category_model.bin.

Training data is generated, not collected: brand and shop-type words from Categorizer.kt,
combined with common Indian shop-name and person-name patterns. No user data is used.

Run:  python3 scripts/train_category_model.py
      python3 scripts/train_category_model.py --extra-csv my-export.csv   # add your own labels

--extra-csv takes the CSV from the app (Settings → Export data). Only rows you categorised
yourself ("Picked by" = You or Auto) are used, and payments to people (Transfers / Self transfer)
are left out, so personal names don't end up inside the model that ships in the APK.
"""
import csv
import math, random, re, struct, sys
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CATEGORIZER = ROOT / "app/src/main/java/com/expensetracker/sms/Categorizer.kt"
OUT = ROOT / "app/src/main/assets/category_model.bin"
BUCKETS = 1 << 15
ALPHA = 0.3
random.seed(42)

# ---------------------------------------------------------------- features
# Must match CategoryModel.kt exactly.
def normalize(s):
    s = re.sub(r"[^a-z0-9 ]", " ", s.lower())
    return re.sub(r" +", " ", s).strip()

def features(name):
    out = []
    words = [w for w in normalize(name).split(" ") if w]
    for w in words:
        if len(w) >= 2:
            out.append("w:" + w)
        p = "#" + w + "#"
        for n in (3, 4, 5):
            for i in range(len(p) - n + 1):
                out.append("c:" + p[i:i + n])
    for a, b in zip(words, words[1:]):
        out.append("b:" + a + "_" + b)
    return out

def fnv(s):
    h = 0x811C9DC5
    for byte in s.encode("utf-8"):
        h ^= byte
        h = (h * 16777619) & 0xFFFFFFFF
    return h

def bucket(f):
    return (fnv(f) & 0x7FFFFFFF) % BUCKETS

# ------------------------------------------------------------- dictionaries
src = CATEGORIZER.read_text()
dicts = defaultdict(set)
for m in re.finditer(r'(?:"([^"]+)"|Categories\.SELF)\s+to\s+words\((.*?)\),\n', src, re.S):
    cat = m.group(1) or "Self transfer"
    for w in re.findall(r'"((?:[^"\\]|\\.)*)"', m.group(2)):
        dicts[cat].add(w)

SKIP = {"Self transfer"}
CLASSES = [c for c in dicts if c not in SKIP] + ["Transfers"]
CLASSES = list(dict.fromkeys(CLASSES))

# ---------------------------------------------------------- synthetic names
PREFIXES = """shree ganesh|sri sai|om sai|balaji|new|royal|krishna|jai mata di|maa durga|sharma|gupta|agarwal|patel|
reddy|nair|iyer|khan|singh|laxmi|annapurna|sagar|apna|modern|national|super|star|galaxy|kumar|raj|mahalaxmi|
venkateshwara|murugan|hanuman|guru kripa|sai baba|bharat|hindustan|city|green|fresh|daily|prime|golden|silver|
ambika|bhagwati|shiv shakti|jai bhavani|tirupati|vinayaka|amman|ganga|yamuna|kaveri|sunrise|sunshine|lucky|happy|
friends|smart|perfect|classic|delux|deluxe|metro city|aditya|vijay|anand|mehta|joshi|desai|kulkarni|rao|pillai|
menon|das|ghosh|banerjee|mukherjee|chauhan|yadav|verma|mishra|tiwari|pandey|saini|jain|shah|bansal|goyal|mittal|
al noor|al madina|zam zam|noor|taj|shalimar|janta|lok|seva|sri lakshmi|sri venkateswara|sri ram|ram|shyam|radhe|
radha krishna|mahadev|bajrang|kalpana|neha|pooja|kavya|arihant|mahavir|parshwa|shanti|ashirwad|vrindavan|""".replace("\n", "").split("|")
PREFIXES = [p.strip() for p in PREFIXES if p.strip()]
SUFFIXES = ["", "", "", " & sons", " brothers", " and co", " pvt ltd", " enterprises", " traders", " point", " centre", " corner", " house", " world"]

SHOP_WORDS = {
    "Food": "restaurant|family restaurant|veg restaurant|hotel|dhaba|da dhaba|cafe|cafe and bakery|bakery|bakers|sweets|sweet house|sweet mart|mithai bhandar|"
            "juice centre|juice corner|ice cream parlour|tea stall|chai point|chai wala|canteen|mess|bhojanalaya|bhojnalaya|tiffin centre|tiffin service|caterers|"
            "biryani house|biryani point|pizza corner|fast food|snacks|chaat bhandar|momos corner|dosa corner|idli house|food court|kitchen|foods|chinese corner|"
            "paratha house|thali|lassi|kulfi|wine shop|beer shop|bar and restaurant|pub|lounge|brewery|coffee house|udupi hotel|darshini|tea house|sandwich",
    "Groceries": "kirana store|kirana|general store|general stores|provision store|provisions|supermarket|super market|hypermarket|mart|grocery|groceries|"
                 "departmental store|vegetables|sabzi mandi|fruit centre|fruits|dairy|milk centre|doodh dairy|chicken centre|mutton shop|fish market|meat shop|"
                 "egg centre|rice traders|atta chakki|oil depot|dry fruits|masala",
    "Health": "medical|medicals|medical store|medicos|pharmacy|pharma|chemist|chemists and druggists|druggist|hospital|multispeciality hospital|clinic|"
              "dental clinic|dental care|diagnostic centre|diagnostics|pathology lab|path lab|lab|nursing home|healthcare|ayurvedic|aushadhalaya|homeopathy|"
              "eye hospital|opticals|optical|physiotherapy|polyclinic|gym|fitness centre|fitness studio|yoga centre|veterinary clinic|pet clinic",
    "Shopping": "fashion|fashions|garments|textiles|silks|saree centre|sarees|vastralaya|cloth house|readymade|footwear|shoe house|shoes|boutique|jewellers|"
                "jewellery|gold palace|electronics|electricals|mobile shop|mobiles|mobile point|mobile gallery|computers|laptop store|furniture|furnishing|"
                "hardware|hardware store|paints|stationery|stationers|xerox|book depot|book store|gift shop|gift gallery|collection|emporium|cosmetics|"
                "toys|toy world|sports|watch co|watches|opticians|tailors|auto parts|spare parts|tyres|cycle store|bag house|handloom|crockery|steel utensils|"
                "plastics|novelty|kids wear|men s wear|ladies wear",
    "Personal care": "salon|unisex salon|hair salon|beauty parlour|beauty parlor|parlour|spa|barber|hair cutting|mens parlour|makeup studio|nail studio|"
                     "laundry|dry cleaners|dhobi|tattoo studio|beauty care",
    "Education": "school|public school|international school|college|academy|institute|coaching centre|coaching classes|classes|tuition centre|tutorials|"
                 "vidyalaya|education|learning centre|library|computer institute|dance academy|music academy|driving school|play school|kidz",
    "Travel": "travels|tours and travels|tours|cabs|car rentals|taxi service|taxi|transport|parking|bus service|lodge|guest house|resort|residency|"
              "inn|stay|holidays|trip|bike rentals|auto stand|motors travel|logistics",
    "Fuel": "petroleum|petrol pump|fuels|filling station|service station|fuel station|auto fuels|cng station|gas station|fuel point",
    "Bills": "telecom|broadband|internet services|cable network|cable tv|networks|electricity board|power distribution|water supply|gas agency|"
             "lpg distributor|recharge point|mobile recharge|dth recharge",
    "Rent": "rent|house rent|pg|paying guest|hostel|rental|flat rent|room rent|co living|properties",
    "Entertainment": "cinema|cinemas|multiplex|theatre|talkies|gaming zone|game zone|bowling|amusement park|water park|club|events|concert|play zone",
    "Insurance": "insurance|life insurance|general insurance|insurance agency|insurance broker|assurance",
    "EMI/Loan": "finance|finserv|fincorp|financial services|capital|credit|loans|microfinance|gold loan|nidhi",
    "Investment": "securities|broking|stock broking|wealth|investments|mutual fund|share broker|capital markets|asset management",
    "Charges & fees": "charges|annual fee|late fee|service charge|processing fee|penalty|gst",
    "Gifts & donations": "temple|mandir|devasthanam|trust|charitable trust|foundation|gurudwara|church|masjid|dargah|ashram|seva samiti|ngo|donation",
    "ATM/Cash": "atm|cash withdrawal|atm wdl|cash wdl",
}

FIRST = ("rahul priya amit neha rohit pooja vikas anjali suresh sunita rajesh kavita manoj rekha sanjay deepa arun anita vijay geeta ravi "
         "meena ajay sneha karan riya arjun divya nikhil shreya vishal swati aditya ishita akash kiran mohit nisha sachin payal gaurav "
         "preeti harish lata naveen asha prakash usha ramesh jyoti mahesh seema dinesh shweta ashok komal anil varsha sunil sapna "
         "imran farhan ayesha sana irfan zoya salman fatima abdul rizwan joseph mary thomas george anthony venkat srinivas lakshmi "
         "murali karthik ganesh deepak praveen sai harsha teja chaitanya bhavna tanvi aarav vivaan ananya diya kabir yash").split()
LAST = ("sharma verma gupta singh kumar patel shah mehta joshi desai reddy rao nair iyer menon pillai das ghosh banerjee mukherjee "
        "chatterjee bose sen yadav mishra tiwari pandey chauhan thakur saini jain agarwal bansal goyal mittal khan sheikh ansari "
        "qureshi siddiqui fernandes dsouza pereira naidu shetty hegde kulkarni patil pawar jadhav shinde more gaikwad bhat "
        "krishnan subramanian raman chandra prasad kapoor malhotra khanna arora chopra bhatia sethi gill sandhu").split()

def variants(word):
    out = {word, word.upper(), word.title()}
    for suf in (" ltd", " pvt ltd", " india", " limited", " online", " services", " retail"):
        out.add(word + suf)
    return out

def build():
    data = []  # (name, class)
    for cat in CLASSES:
        for w in dicts.get(cat, ()):
            if cat == "Transfers":
                continue
            for v in variants(w):
                data.append((v, cat))
    for cat, words in SHOP_WORDS.items():
        if cat not in CLASSES:
            CLASSES.append(cat)
        shops = [w.strip() for w in words.split("|") if w.strip()]
        for shop in shops:
            data.append((shop, cat))
            for _ in range(14):
                name = random.choice(PREFIXES) + " " + shop + random.choice(SUFFIXES)
                data.append((name, cat))
    # People (UPI / IMPS to a person)
    for _ in range(3500):
        f, l = random.choice(FIRST), random.choice(LAST)
        r = random.random()
        name = f"{f} {l}" if r < 0.6 else (f"{f} {l[0]}" if r < 0.75 else (f"{f[0]} {l}" if r < 0.85 else f"{f} kumar {l}"))
        data.append((name, "Transfers"))
    return data

def train(data):
    counts = defaultdict(lambda: defaultdict(float))   # bucket -> class -> count
    totals = defaultdict(float)
    docs = defaultdict(float)
    for name, cat in data:
        docs[cat] += 1
        for f in features(name):
            b = bucket(f)
            counts[b][cat] += 1
            totals[cat] += 1
    return counts, totals, docs

def scores(model, name):
    counts, totals, docs = model
    n = sum(docs.values())
    s = {}
    feats = [bucket(f) for f in features(name)]
    for c in CLASSES:
        v = math.log((docs[c] + 1) / (n + len(CLASSES)))
        denom = totals[c] + ALPHA * BUCKETS
        for b in feats:
            v += math.log((counts[b].get(c, 0.0) + ALPHA) / denom)
        s[c] = v
    return s, len(feats)

def softmax(s, t):
    m = max(s.values())
    e = {c: math.exp((v - m) / t) for c, v in s.items()}
    z = sum(e.values())
    return {c: v / z for c, v in e.items()}

# Realistic names that are NOT in the training data, to measure generalisation.
UNSEEN = [
    ("Shree Balaji Medicos", "Health"), ("Sharma Da Dhaba", "Food"), ("Om Sai Auto Parts", "Shopping"), ("Kumar Vegetables", "Groceries"),
    ("Annapurna Bhojanalaya", "Food"), ("Royal Tours and Travels", "Travel"), ("Priya Sharma", "Transfers"), ("Lakshmi Silks", "Shopping"),
    ("Galaxy Mobile Point", "Shopping"), ("New Krishna Sweets", "Food"), ("Sri Venkateswara Kirana", "Groceries"), ("City Diagnostics", "Health"),
    ("Sai Krupa Petroleum", "Fuel"), ("Glamour Unisex Salon", "Personal care"), ("Bright Future Academy", "Education"), ("Hotel Udupi Krishna", "Food"),
    ("Amit Verma", "Transfers"), ("R K Jewellers", "Shopping"), ("Fresh Fruits Corner", "Groceries"), ("Apollo Clinic Indiranagar", "Health"),
    ("Mahadev Hardware", "Shopping"), ("Kaveri Lodge", "Travel"), ("Shiv Shakti Chicken Centre", "Groceries"), ("Gupta Stationers", "Shopping"),
    ("Sunrise Public School", "Education"), ("Zam Zam Bakery", "Food"), ("Neha Kapoor", "Transfers"), ("Metro Shoes", "Shopping"),
    ("Vrindavan Dairy", "Groceries"), ("Ashirwad Medical Store", "Health"), ("Ganesh Tea Stall", "Food"), ("Fitness First Gym", "Health"),
    ("Sri Ram Fuel Station", "Fuel"), ("Joseph Thomas", "Transfers"), ("Classic Dry Cleaners", "Personal care"), ("Bajrang Cinema", "Entertainment"),
    ("Tirupati Finance", "EMI/Loan"), ("Star Securities", "Investment"), ("Hanuman Mandir Trust", "Gifts & donations"), ("Laxmi Gas Agency", "Bills"),
]

def extra_rows(path):
    rows = []
    with open(path, newline="", encoding="utf-8-sig") as f:
        for r in csv.DictReader(f):
            if r.get("Type") != "Expense" or r.get("Picked by", "Auto") not in ("You", "Auto"):
                continue
            cat, name = r.get("Category", "").strip(), r.get("Merchant", "").strip()
            if not name or cat in ("", "Others", "Transfers", "Self transfer"):
                continue
            if cat not in CLASSES:
                CLASSES.append(cat)
            rows += [(name, cat)] * 3   # your own labels count a bit more than generated ones
    print(f"extra rows from {path}: {len(rows) // 3}")
    return rows

def main():
    data = build()
    if "--extra-csv" in sys.argv:
        data += extra_rows(sys.argv[sys.argv.index("--extra-csv") + 1])
    random.shuffle(data)
    cut = int(len(data) * 0.9)
    model = train(data[:cut])
    # Temperature: NB scores are over-confident; pick T that maximises held-out log-likelihood.
    held = data[cut:][:3000]
    best_t, best_ll = 1.0, -1e18
    for t in [1, 1.5, 2, 3, 4, 5, 6, 8, 10, 12]:
        ll = 0.0
        for name, c in held:
            p = softmax(scores(model, name)[0], t)
            ll += math.log(max(p[c], 1e-9))
        if ll > best_ll:
            best_t, best_ll = t, ll
    acc_held = sum(max((s := scores(model, n)[0]), key=s.get) == c for n, c in held) / len(held)

    model = train(data)  # final model on everything
    right = 0
    print(f"classes={len(CLASSES)} examples={len(data)} temperature={best_t} held-out acc={acc_held:.3f}")
    for name, c in UNSEEN:
        p = softmax(scores(model, name)[0], best_t)
        top = max(p, key=p.get)
        right += top == c
        mark = "OK " if top == c else "XX "
        print(f"  {mark}{name:32s} -> {top:18s} {p[top]:.2f}   (want {c})")
    print(f"unseen accuracy: {right}/{len(UNSEEN)}")

    counts, totals, docs = model
    entries = sorted((b, CLASSES.index(c), v) for b, row in counts.items() for c, v in row.items())
    with open(OUT, "wb") as f:
        f.write(b"ETCM")
        f.write(struct.pack(">iiiff", 1, BUCKETS, len(CLASSES), ALPHA, float(best_t)))
        for c in CLASSES:
            enc = c.encode("utf-8")
            f.write(struct.pack(">h", len(enc)) + enc)
            f.write(struct.pack(">ff", docs[c], totals[c]))
        f.write(struct.pack(">i", len(entries)))
        for b, ci, v in entries:
            f.write(struct.pack(">ihH", b, ci, min(int(round(v)), 65535)))
    print(f"wrote {OUT.relative_to(ROOT)} ({OUT.stat().st_size // 1024} KB, {len(entries)} entries)")
    # Test vectors for the Kotlin parity test
    for s in ["c:#sw", "w:swiggy", "b:shree_balaji"]:
        print(f"  fnv({s!r}) = {fnv(s)}  bucket = {bucket(s)}")

if __name__ == "__main__":
    main()
