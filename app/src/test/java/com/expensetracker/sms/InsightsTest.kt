package com.expensetracker.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class InsightsTest {
    private val today = LocalDate.of(2026, 11, 23)
    private var id = 1L

    private fun t(
        amount: Double, merchant: String, category: String, date: LocalDate, debit: Boolean = true,
        body: String = "Rs $amount debited", type: AcctType? = AcctType.BANK, ignored: Boolean = false,
    ) = Txn(
        id = id++, smsKey = "k$id", amount = amount, isDebit = debit, merchant = merchant, account = "1234",
        category = category, timestamp = date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        body = body, bank = "HDFC Bank", acctType = type, ignored = ignored,
    )

    @Test fun monthlySubscriptionIsRecurring() {
        val list = (0..2).map { t(649.0, "Netflix", "Entertainment", today.minusMonths(it.toLong()).withDayOfMonth(5)) }
        val r = Insights.detectRecurring(list, today)
        assertEquals(1, r.size)
        assertEquals("netflix", r[0].key)
        assertEquals(3, r[0].months)
        assertEquals(LocalDate.of(2026, 12, 5), r[0].nextDue)
    }

    @Test fun twoSameEmiPaymentsInARowAreRecurring() {
        val list = listOf(
            t(12000.0, "Bajaj Finance", "EMI/Loan", LocalDate.of(2026, 10, 3), body = "Rs 12000 debited NACH ACH-DR Bajaj"),
            t(12000.0, "Bajaj Finance", "EMI/Loan", LocalDate.of(2026, 11, 3), body = "Rs 12000 debited NACH ACH-DR Bajaj"),
        )
        val r = Insights.detectRecurring(list, today)
        assertEquals(1, r.size)
        assertTrue(r[0].autopay)
    }

    @Test fun foodAndChangingAmountsAreNotRecurring() {
        val food = (1..4).map { t(350.0, "Swiggy", "Food", today.minusMonths(it.toLong()).withDayOfMonth(10)) }
        val random = listOf(500.0, 2300.0, 900.0).mapIndexed { i, a -> t(a, "Croma", "Electronics", today.minusMonths(i + 1L).withDayOfMonth(8)) }
        val spread = listOf(2, 20, 11).mapIndexed { i, d -> t(999.0, "Gym", "Health & fitness", today.minusMonths(i + 1L).withDayOfMonth(d)) }
        assertTrue(Insights.detectRecurring(food + random + spread, today).isEmpty())
    }

    @Test fun dismissedKnownAndStaleAreSkipped() {
        val list = (1..3).map { t(199.0, "Spotify", "Entertainment", today.minusMonths(it.toLong()).withDayOfMonth(15)) }
        assertTrue(Insights.detectRecurring(list, today, dismissed = setOf("spotify")).isEmpty())
        assertTrue(Insights.detectRecurring(list, today, known = { it == "spotify" }).isEmpty())
        val old = (3..5).map { t(199.0, "Hotstar", "Entertainment", today.minusMonths(it.toLong()).withDayOfMonth(15)) }
        assertTrue(Insights.detectRecurring(old, today).isEmpty())
    }

    @Test fun autoTags() {
        val upi = t(250.0, "Ramesh Kirana", "Groceries", today, body = "Rs 250 debited via UPI to ramesh@okicici Ref 1234")
        assertTrue("UPI" in Insights.autoTags(upi))
        val card = t(1299.0, "Amazon", "Shopping", today, body = "Rs 1299 spent on Credit Card XX4321 at AMAZON", type = AcctType.CREDIT_CARD)
        val tags = Insights.autoTags(card)
        assertTrue("Card" in tags)
        assertTrue("Online" in tags)
        val atm = t(2000.0, "ATM", "ATM/Cash", today, body = "Rs 2000 withdrawn at ATM")
        assertTrue("Cash" in Insights.autoTags(atm))
        val nach = t(5000.0, "LIC", "Insurance", today, body = "Rs 5000 debited towards NACH mandate UMRN 123")
        assertTrue("Autopay" in Insights.autoTags(nach))
        assertTrue("Recurring" in Insights.autoTags(nach, setOf("lic")))
        val own = card.copy(tags = listOf("Office"))
        assertTrue("Office" in Insights.allTags(own))
    }

    @Test fun monthlyTotalsSkipSelfAndExcluded() {
        val list = listOf(
            t(100.0, "A", "Food", LocalDate.of(2026, 11, 2)),
            t(50.0, "B", "Food", LocalDate.of(2026, 10, 2)),
            t(1000.0, "Me", Categories.SELF, LocalDate.of(2026, 11, 3), ignored = true),
            t(80.0, "Dup", "Food", LocalDate.of(2026, 11, 4), ignored = true),
            t(5000.0, "Employer", "Salary", LocalDate.of(2026, 11, 1), debit = false),
        )
        val m = Insights.monthlyTotals(list, YearMonth.of(2026, 11), 3)
        assertEquals(listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 10), YearMonth.of(2026, 11)), m.map { it.month })
        assertEquals(listOf(0.0, 50.0, 100.0), m.map { it.spent })
        assertEquals(5000.0, m[2].income, 0.01)
    }

    @Test fun merchantsGroupNameVariants() {
        val list = listOf(
            t(100.0, "Swiggy", "Food", today),
            t(200.0, "SWIGGY", "Food", today),
            t(50.0, "Uber", "Travel", today),
        )
        val m = Insights.merchants(list)
        assertEquals(2, m.size)
        assertEquals("swiggy", m[0].key)
        assertEquals(2, m[0].count)
        assertEquals(300.0, m[0].total, 0.01)
    }

    @Test fun uncategorisedGroupsOthersByMerchant() {
        val list = listOf(
            t(100.0, "Sharma Store", Categories.OTHERS, today),
            t(120.0, "Sharma Store", Categories.OTHERS, today.minusDays(3)),
            t(90.0, "Gupta", Categories.OTHERS, today),
            t(300.0, "Swiggy", "Food", today),
        )
        val g = Insights.uncategorised(list)
        assertEquals(listOf("sharma store", "gupta"), g.map { it.first.key })
        assertEquals(2, g[0].second.size)
    }

    @Test fun recapNumbers() {
        val oct = YearMonth.of(2026, 10)
        val list = listOf(
            t(1000.0, "Rent", "Rent", LocalDate.of(2026, 10, 1)),
            t(300.0, "Swiggy", "Food", LocalDate.of(2026, 10, 5)),
            t(200.0, "Swiggy", "Food", LocalDate.of(2026, 10, 9)),
            t(800.0, "X", "Food", LocalDate.of(2026, 9, 9)),
        )
        val r = Insights.recap(list, oct, 2000.0)
        assertEquals(1500.0, r.spent, 0.01)
        assertEquals(800.0, r.prevSpent, 0.01)
        assertEquals("Rent", r.topCategories[0].first)
        assertEquals("swiggy", r.topMerchant?.key)
        assertEquals(31 - 3, r.noSpendDays)
        assertFalse(r.biggest == null)
    }
}
