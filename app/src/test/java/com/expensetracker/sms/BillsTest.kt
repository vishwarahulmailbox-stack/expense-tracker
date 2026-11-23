package com.expensetracker.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class BillsTest {
    private val today = LocalDate.of(2026, 11, 20)
    private fun bill(body: String, sender: String = "VM-HDFCBK") = BillParser.parse(sender, body, today)

    // ------------------------------------------------------------- dates

    @Test fun dueDateFormats() {
        assertEquals(LocalDate.of(2026, 11, 25), DueDates.find("Total due Rs 500. Due date 25-11-2026", today))
        assertEquals(LocalDate.of(2026, 11, 25), DueDates.find("payable by 25/11/26", today))
        assertEquals(LocalDate.of(2026, 11, 25), DueDates.find("due on 25-Nov-26", today))
        assertEquals(LocalDate.of(2026, 12, 5), DueDates.find("due on 5th Dec 2026", today))
        assertEquals(LocalDate.of(2026, 12, 5), DueDates.find("due on Dec 5, 2026", today))
        assertEquals(LocalDate.of(2026, 12, 5), DueDates.find("due on 2026-12-05", today))
        // No year: next occurrence
        assertEquals(LocalDate.of(2027, 1, 3), DueDates.find("will be debited on 03 Jan", today))
        // "Nov 2026" alone is not a day
        assertNull(DueDates.find("Statement for Nov 2026 is ready", today))
    }

    // ------------------------------------------------------------ parser

    @Test fun creditCardStatement() {
        val d = bill("Your HDFC Bank Credit Card XX4321 statement: Total Amt Due Rs.12,345.00, Min Amt Due Rs.620.00, Due Date 25-11-2026.")!!
        assertEquals(BillKind.CREDIT_CARD, d.kind)
        assertEquals("HDFC Bank Credit Card", d.name)
        assertEquals(12345.0, d.amount, 0.01)
        assertEquals(620.0, d.minDue!!, 0.01)
        assertEquals(LocalDate.of(2026, 11, 25), d.dueDate)
        assertEquals("4321", d.last4)
    }

    @Test fun licPremium() {
        val d = bill("Premium of Rs 15,000.00 for policy no 123456789 is due on 28/11/2026. Pay via LIC Pay Direct.", "AD-LICIND")!!
        assertEquals(BillKind.LIC, d.kind)
        assertEquals(15000.0, d.amount, 0.01)
        assertEquals("6789", d.last4)
        assertEquals(Recurrence.YEARLY, d.recurrence)
    }

    @Test fun electricity() {
        val d = bill("BESCOM: Bill for RR No 1234567 of Rs 1,250 for Nov-2026 is due on 30-11-2026. Pay online.", "VM-BESCOM")!!
        assertEquals(BillKind.ELECTRICITY, d.kind)
        assertEquals("BESCOM", d.name)
        assertEquals(1250.0, d.amount, 0.01)
        assertEquals("bescom", d.hint)
    }

    @Test fun insuranceAndSubscriptionAndEmi() {
        assertEquals(BillKind.INSURANCE, bill("Your Star Health policy premium of Rs 18,500 is due on 01-12-2026", "VM-STARHL")!!.kind)
        val sub = bill("Your YouTube Premium membership of Rs 149 will be debited on 26-11-2026 via UPI AutoPay", "VM-HDFCBK")!!
        assertEquals(BillKind.SUBSCRIPTION, sub.kind)
        assertTrue(sub.autopay)
        val emi = bill("EMI of Rs 12,000 for loan a/c XX5566 will be debited on 05-12-2026. Maintain sufficient balance.", "VM-BAJAJF")!!
        assertEquals(BillKind.LOAN_EMI, emi.kind)
        assertTrue(emi.autopay)
    }

    @Test fun notBills() {
        assertNull(bill("Thank you for your payment of Rs 12,345 towards your credit card XX4321. Due date 25-11-2026"))
        assertNull(bill("123456 is your OTP for payment of Rs 1,250 due on 30-11-2026"))
        assertNull(bill("Sent Rs.500 From HDFC Bank A/C *1234 To SWIGGY On 12/11/26"))
    }

    // ---------------------------------------------------------- matching

    private fun ts(d: LocalDate) = d.atTime(11, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun txn(amount: Double, merchant: String, body: String, day: LocalDate, cat: String = "Others") =
        Txn(id = 1, smsKey = "k", amount = amount, isDebit = true, merchant = merchant, account = "1234", category = cat, timestamp = ts(day), body = body)

    private val bescom = Bill(id = 1, series = "s", name = "BESCOM", kind = BillKind.ELECTRICITY, amount = 1250.0,
        dueDate = LocalDate.of(2026, 11, 30), recurrence = Recurrence.MONTHLY, hint = "bescom")

    @Test fun electricityPaymentMatches() {
        val t = txn(1250.0, "Bescom", "Rs 1250 debited from A/c XX1234 to BESCOM via BBPS", LocalDate.of(2026, 11, 22), "Bills")
        assertTrue(BillEngine.score(bescom, t) >= 6)
    }

    @Test fun wrongAmountOrDateDoesNotMatch() {
        assertEquals(0, BillEngine.score(bescom, txn(1300.0, "Bescom", "to BESCOM", LocalDate.of(2026, 11, 22))))
        assertEquals(0, BillEngine.score(bescom, txn(1250.0, "Bescom", "to BESCOM", LocalDate.of(2026, 9, 1))))
        // Same amount but nothing identifies the biller
        assertTrue(BillEngine.score(bescom, txn(1250.0, "Rahul Sharma", "Sent Rs 1250 to Rahul", LocalDate.of(2026, 11, 22))) < 6)
    }

    @Test fun creditCardPartialPaymentMatches() {
        val cc = Bill(id = 2, series = "c", name = "HDFC Bank Credit Card", kind = BillKind.CREDIT_CARD, amount = 12345.0, minDue = 620.0,
            dueDate = LocalDate.of(2026, 11, 25), recurrence = Recurrence.MONTHLY, last4 = "4321")
        val t = txn(5000.0, "CRED Club", "Rs 5000 paid to CRED Club for card XX4321", LocalDate.of(2026, 11, 21), Categories.SELF)
        assertTrue(BillEngine.score(cc, t) >= 6)
    }

    @Test fun autopayDebitMatches() {
        val emi = Bill(id = 3, series = "e", name = "Bajaj Finance Loan EMI", kind = BillKind.LOAN_EMI, amount = 12000.0,
            dueDate = LocalDate.of(2026, 12, 5), recurrence = Recurrence.MONTHLY, autopay = true)
        val t = txn(12000.0, "Bajaj Finance", "Rs 12000 debited from A/c XX1234 via NACH UMRN BAJF0001", LocalDate.of(2026, 12, 5), "EMI/Loan")
        assertTrue(BillEngine.score(emi, t) >= 6)
    }

    // ----------------------------------------------------- reminders, UPI

    @Test fun reminderSchedule() {
        val b = bescom.copy(dueDate = today.plusDays(3))
        assertEquals(1, BillReminders.reminderFor(b, today))
        assertNull(BillReminders.reminderFor(b.copy(notified = 1), today))
        assertEquals(2, BillReminders.reminderFor(b, today.plusDays(2)))
        assertEquals(4, BillReminders.reminderFor(b, today.plusDays(3)))
        assertEquals(8, BillReminders.reminderFor(b, today.plusDays(5)))
        assertNull(BillReminders.reminderFor(b.copy(paid = true), today))
        assertNull(BillReminders.reminderFor(b, today.minusDays(5)))
    }

    @Test fun upi() {
        assertNull(Upi.validate("bescom@okaxis"))
        assertNotNull(Upi.validate("not a vpa"))
        assertNotNull(Upi.validate("hdfcbankcc.4111111111111111@hdfcbank")) // looks like a card number
        val uri = Upi.payUri("bescom@okaxis", "BESCOM Bill", 1250.0, "Bill BESCOM")
        assertTrue(uri.startsWith("upi://pay?pa=bescom%40okaxis&pn=BESCOM%20Bill&am=1250.00&cu=INR"))
        assertEquals(true, Upi.isSuccess("txnId=123&responseCode=00&Status=SUCCESS&txnRef=abc"))
        assertEquals(false, Upi.isSuccess("Status=FAILURE"))
        assertNull(Upi.isSuccess(null))
        assertFalse(Upi.isSuccess("status=failed") ?: true)
    }
}
