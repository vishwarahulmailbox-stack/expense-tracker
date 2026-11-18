# 💸 SMS Expense Tracker (Android)

An Android app that reads your bank, UPI and card SMS and turns them into an expense tracker on its own. Nothing is sent anywhere: no internet permission, no account, and all data stays on the phone.

## Features
- **Auto-tracking from SMS**: finds debits, UPI payments, card spends, ATM withdrawals and credits (HDFC, SBI, ICICI, Axis, Kotak, IDFC and similar formats)
- **Skips** OTPs, offers, bill reminders, failed transactions and personal messages
- **Live**: new transaction SMS are saved as soon as they arrive, with a notification
- **Smart categories**: Food, Groceries, Shopping, Travel, Fuel, Bills, Rent and more, guessed from the merchant (Swiggy → Food, Uber → Travel…)
- **Learns**: change a merchant's category once and tick "Always use this category" to apply it to all past and future payments to that merchant
- **Monthly budget**: an overall limit plus per-category limits, with notifications at 80% and 100%
- **Overview**: monthly spent vs income, daily spending chart, category breakdown
- Search and filter transactions, add cash expenses by hand, ignore self-transfers, add notes
- Light and dark mode (Material You colours on Android 12+)

## Download & install
1. Open the **[Releases](../../releases/latest)** page on your phone and download the `.apk`
2. Open it. If asked, allow *Install unknown apps* for your browser or file manager
3. Play Protect may warn you because the app is not from the Play Store: tap **More details → Install anyway**
4. Open the app and tap **Allow SMS access**
   - If Android says the setting is *restricted*: go to **Settings → Apps → Expense Tracker → ⋮ (top right) → Allow restricted settings**, then try again

Updates: every push to `main` builds a new APK on its own (GitHub Actions → Releases). Install it over the old one and your data is kept.

## Build it yourself
```
gradle assembleRelease   # needs JDK 17 + Android SDK
```
The APK is written to `app/build/outputs/apk/release/`.

## Code map
| File | What it does |
|---|---|
| `SmsParser.kt` | Pulls amount, debit/credit, merchant and account out of an SMS |
| `Categorizer.kt` | Merchant/keyword → category |
| `SmsImporter.kt` | Inbox scan + `SmsReceiver` for live SMS |
| `Db.kt` | SQLite storage (transactions, budgets, merchant rules) |
| `Notifier.kt` | New-transaction and budget-alert notifications |
| `ui/` | Jetpack Compose screens (Overview, Transactions, Budget) |

> Note: `app/app.keystore` is a signing key used only so that updates install over older versions. Keep the repo private if you don't want others to sign builds as this app.
