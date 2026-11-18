package com.expensetracker.sms

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.expensetracker.sms.ui.AddTxnContent
import com.expensetracker.sms.ui.AppTheme
import com.expensetracker.sms.ui.BudgetEditorContent
import com.expensetracker.sms.ui.BudgetScreen
import com.expensetracker.sms.ui.HomeScreen
import com.expensetracker.sms.ui.MonthPill
import com.expensetracker.sms.ui.CategoriesScreen
import com.expensetracker.sms.ui.AccountsScreen
import com.expensetracker.sms.ui.CategoryEditorContent
import com.expensetracker.sms.ui.OnboardingScreen
import com.expensetracker.sms.ui.SettingsScreen
import com.expensetracker.sms.ui.TransactionsScreen
import com.expensetracker.sms.ui.TxnEditorContent
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

/** Renders the main screens to PNGs so the UI can be reviewed without a phone. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h880dp-xxhdpi")
class ScreenshotTest {

    private fun ts(daysAgo: Long, hour: Int) =
        LocalDate.now().minusDays(daysAgo).atTime(LocalTime.of(hour, 15)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private var nextId = 1L
    private fun t(
        amount: Double, merchant: String, category: String, daysAgo: Long, hour: Int = 13, debit: Boolean = true,
        ignored: Boolean = false, acct: String = "1234", bank: String = "HDFC Bank", type: AcctType = AcctType.BANK,
        bal: Double? = null,
    ) =
        Txn(
            id = nextId++, smsKey = "k$nextId", amount = amount, isDebit = debit, merchant = merchant,
            account = acct, category = category, timestamp = ts(daysAgo, hour), bank = bank, acctType = type, balance = bal,
            body = "Sent Rs.$amount From HDFC Bank A/C *1234 To ${merchant.uppercase()} On 12/11/26 Ref 412345678901",
            ignored = ignored,
        )

    private val txns = listOf(
        t(349.0, "Swiggy", "Food", 0, 20, bal = 1_24_560.0),
        t(1249.0, "Amazon", "Shopping", 0, 11, acct = "4321", bank = "ICICI Bank", type = AcctType.CREDIT_CARD, bal = 86500.0),
        t(180.0, "Uber India", "Travel", 1, 9),
        t(85000.0, "Acme Technologies", "Salary", 1, 10, debit = false),
        t(1240.0, "HDFC Bank", "Interest", 2, 9, debit = false),
        t(3200.0, "Infosys Ltd", "Dividend", 6, 11, debit = false),
        t(10000.0, "Vishwa Rahul", Categories.SELF, 3, 12, ignored = true),
        t(620.0, "Blinkit", "Groceries", 2, 19),
        t(2000.0, "ATM Withdrawal", "ATM/Cash", 3, 17),
        t(649.0, "Netflix", "Entertainment", 4, 8),
        t(1500.0, "Rahul Sharma", "Transfers", 5, 21, ignored = true),
        t(899.0, "Jio Prepaid", "Bills", 6, 12),
        t(2450.0, "Indian Oil", "Fuel", 7, 18, acct = "9012", bank = "Axis Bank", type = AcctType.CREDIT_CARD, bal = 42000.0),
        t(412.0, "Zomato", "Food", 8, 21),
        t(15000.0, "Rent", "Rent", 10, 10, acct = "5678", bank = "SBI", bal = 48210.0),
        t(560.0, "Apollo Pharmacy", "Health", 12, 16),
    )

    private fun accountsOf(list: List<Txn>) = list.groupBy { it.accountKey!! }.map { (k, l) ->
        val b = l.filter { it.balance != null }.maxByOrNull { it.timestamp }
        Account(
            key = k, bank = l[0].bank!!, last4 = l[0].account!!, type = l[0].acctType!!,
            nickname = if (l[0].account == "1234") "Salary account" else null,
            balance = b?.balance, balanceTs = b?.timestamp,
            monthSpent = l.filter { it.isDebit && !it.ignored }.sumOf { it.amount },
            monthReceived = l.filter { !it.isDebit && !it.ignored }.sumOf { it.amount },
            monthCount = l.size, totalCount = l.size, lastTs = l.maxOf { it.timestamp },
        )
    }.sortedBy { it.type.ordinal }

    private val state = UiState(
        month = YearMonth.now(),
        accounts = accountsOf(txns),
        txns = txns,
        budgets = mapOf(Categories.TOTAL to 40000.0, "Food" to 700.0, "Shopping" to 3000.0, "Fuel" to 3000.0),
    )

    private fun shot(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        captureRoboImage("build/outputs/roborazzi/$name.png") {
            AppTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
            }
        }
    }

    /** Mimics the real top bar so screenshots show the true amount of space used. */
    @Composable
    private fun WithMonth(content: @Composable () -> Unit) {
        Column {
            androidx.compose.foundation.layout.Row(
                Modifier.padding(horizontal = 16.dp).height(64.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                androidx.compose.material3.Text("Overview", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                MonthPill(state.month, {}, {})
            }
            content()
        }
    }

    @Test fun home() = shot("1_home") { WithMonth { HomeScreen(state, {}, {}, {}) } }
    @Test fun homeDark() = shot("1b_home_dark", dark = true) { WithMonth { HomeScreen(state, {}, {}, {}) } }
    @Test fun homeEmpty() = shot("1c_home_empty") { WithMonth { HomeScreen(UiState(), {}, {}, {}) } }
    @Test fun transactions() = shot("2_transactions") { WithMonth { TransactionsScreen(state) {} } }
    @Test fun budgets() = shot("3_budgets") { WithMonth { BudgetScreen(state) {} } }
    @Test fun budgetsEmpty() = shot("3b_budgets_empty") { WithMonth { BudgetScreen(state.copy(budgets = emptyMap())) {} } }
    @Test fun settings() = shot("4_settings") { SettingsScreen({}, {}, {}, {}) }
    @Test fun onboarding() = shot("5_onboarding") { OnboardingScreen(false, {}, {}, {}) }
    @Test fun onboardingDenied() = shot("5b_onboarding_denied") { OnboardingScreen(true, {}, {}, {}) }
    @Test fun editSheet() = shot("6_edit_sheet") { Box { TxnEditorContent(txns[0], { _, _ -> }, {}) } }
    @Test fun addSheet() = shot("7_add_sheet") { AddTxnContent(onSave = {}) }
    @Test fun accounts() = shot("10_accounts") { WithMonth { AccountsScreen(state, null, {}, {}, { _, _ -> }) } }
    @Test fun accountDetail() = shot("10b_account_detail") { WithMonth { AccountsScreen(state, state.accounts[0].key, {}, {}, { _, _ -> }) } }
    @Test fun categories() = shot("9_categories") { CategoriesScreen({ _, _ -> }, {}) }
    @Test fun categoryEditor() = shot("9b_category_editor") { CategoryEditorContent(null, CatType.EXPENSE, { _, _ -> }) }
    @Test fun budgetSheet() = shot("8_budget_sheet") {
        BudgetEditorContent(null, null, state.byCategory, setOf("Food", "Shopping", "Fuel")) { _, _ -> }
    }
}
