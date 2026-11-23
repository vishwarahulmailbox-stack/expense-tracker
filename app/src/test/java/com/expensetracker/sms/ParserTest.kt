package com.expensetracker.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Plain JVM tests for SMS parsing, categories and self-transfer detection. */
class ParserTest {

    private fun parse(body: String, sender: String = "VM-HDFCBK") = SmsParser.parse(sender, body)

    @Test fun hdfcUpiDebit() {
        val p = parse("Sent Rs.500.00 From HDFC Bank A/C *1234 To SWIGGY On 12/01/25 Ref 412345678901 Not You? Call 18002586161")!!
        assertEquals(500.0, p.amount, 0.001)
        assertTrue(p.isDebit)
        assertEquals("Swiggy", p.merchant)
        assertEquals("1234", p.account)
    }

    @Test fun sbiDebitWithoutRupeeSymbol() {
        val p = parse("Dear UPI user A/C X1234 debited by 250.0 on date 12Jan25 trf to ZOMATO LTD Refno 412345678901. -SBI", "AD-SBIUPI")!!
        assertEquals(250.0, p.amount, 0.001)
        assertTrue(p.isDebit)
        assertEquals("Zomato Ltd", p.merchant)
    }

    @Test fun unionBankFavouringPayee() {
        val a = parse("Union Bank of India A/c *6081 Debited Rs:43.08 on 20-10-2026 20:41:10 by Mob Bk ref no 821549468932, Fvg: MUJEEB K Avl Bal Rs:30475.16. Not you?Call 66013348/SMS BLOCK 1607 to 7146551970", "AD-UNIONB")!!
        assertEquals("Mujeeb K", a.merchant)
        val b = parse("Union Bank of India A/c*6081 Dr Rs11.03 on 04-11-26 20:50 by MB 961537781313 Fv.Isara, Bal Rs26807.56 Not U?Call 66013348/SMS BLOCK 1607 to 7637009924", "AD-UNIONB")!!
        assertEquals("Isara", b.merchant)
    }

    @Test fun walletForPayeeAndTitles() {
        val w = parse("You've paid Rs.185 via PhonePe wallet for mahek gents parlur. Not you? Call us on 318-61681730. Remaining balance: Rs.334.", "VM-PHONPE")!!
        assertEquals("mahek gents parlur", w.merchant)
        val t = parse("Your A/C XXXXX165661 Debited INR 3,718.57 on 01/04/26 -Transferred to Mr. RAHUL RAVINDRA V. Avl Balance INR 17,202.27-SBI", "AD-SBIINB")!!
        assertEquals("Rahul Ravindra V", t.merchant)
    }

    @Test fun atmByLineIsCash() {
        val body = "Your SB A/c *6081 Debited for Rs.5933.73 on 25-02-2026 07:42:14 by ATM Avl Bal Rs.73309.80, If not you, Call 3596721962 -Union Bank of India"
        val p = parse(body, "AD-UNIONB")!!
        assertEquals("ATM/Cash", Categorizer.categorize(p.merchant, body, true, null))
    }

    @Test fun axisUpiPath() {
        val p = parse("INR 180.00 debited A/c no. XX5678 12-01-25, 10:30:00 UPI/P2M/412345678901/UBER INDIA Not you? SMS BLOCK to 919951860002 - Axis Bank", "AX-AXISBK")!!
        assertEquals("Uber India", p.merchant)
    }

    @Test fun cardSpendIgnoresAvailableLimit() {
        val p = parse("Rs.2,999.00 spent on HDFC Bank Card x4321 at FLIPKART INTERNET on 2025-01-12:10:30:11 Avl Lmt: Rs.45,000.00")!!
        assertEquals(2999.0, p.amount, 0.001)
        assertEquals("4321", p.account)
    }

    @Test fun skipsOtpReminderAndPersonal() {
        assertNull(parse("123456 is your OTP for txn of Rs.500 at Amazon. Do not share with anyone."))
        assertNull(parse("Your credit card bill of Rs.12,345 is due on 15-Jan-25. Pay now."))
        assertNull(parse("Sent Rs.500 to you", sender = "+919876543210"))
    }

    @Test fun credit() {
        val p = parse("Rs.50000.00 credited to A/c XX1234 on 01-01-25 by NEFT from ACME TECHNOLOGIES PVT LTD. Avl bal Rs.62,345.10 -HDFC")!!
        assertFalse(p.isDebit)
        assertEquals(50000.0, p.amount, 0.001)
    }

    @Test fun incomeCategories() {
        assertEquals("Salary", Categorizer.incomeCategory("Acme", "Rs 85000 credited to A/c XX1234 towards SALARY FOR NOV"))
        assertEquals("Interest", Categorizer.incomeCategory("HDFCBK", "Rs 1240 credited to A/c XX1234 as Interest paid"))
        assertEquals("Dividend", Categorizer.incomeCategory("Infosys", "ACH C- INFOSYS LTD DIVIDEND Rs 3200 credited"))
        assertEquals("Refund & cashback", Categorizer.incomeCategory("Amazon", "Refund of Rs 499 credited to your card"))
        assertEquals(Categories.OTHER_INCOME, Categorizer.incomeCategory("Rohit", "Rs 500 credited from rohit@okaxis"))
    }

    @Test fun expenseCategories() {
        assertEquals("Food", Categorizer.categorize("Swiggy", "", true, null))
        assertEquals("Travel", Categorizer.categorize("Uber India", "", true, null))
        assertEquals("Salary", Categorizer.categorize("Acme", "salary credited", false, null))
        assertEquals("Pets", Categorizer.categorize("Heads Up For Tails", "", true, "Pets"))
    }

    @Test fun selfTransferByKeyword() {
        assertTrue(SelfTransfer.byContent("x", "Rs 5000 debited from A/c XX1234 for Self Transfer via IMPS", "1234", emptyList(), emptySet()))
        assertTrue(SelfTransfer.byContent("x", "Rs 20000 transferred to your FD from A/c XX1234 (auto sweep)", "1234", emptyList(), emptySet()))
    }

    @Test fun selfTransferByOwnName() {
        val names = listOf("vishwa rahul")
        assertTrue(SelfTransfer.byContent("Vishwa Rahul", "Sent Rs.5000 to VISHWA RAHUL", "1234", names, emptySet()))
        assertFalse(SelfTransfer.byContent("Rahul Sharma", "Sent Rs.5000 to RAHUL SHARMA", "1234", names, emptySet()))
    }

    @Test fun selfTransferByKnownAccount() {
        val known = setOf("1234", "5678", "4321")
        assertTrue(SelfTransfer.byContent("x", "Rs 10000 debited from A/c XX1234 to A/c XX5678 via IMPS", "1234", emptyList(), known))
        assertTrue(SelfTransfer.byContent("x", "Rs 15000 debited from A/c XX1234 towards credit card XX4321", "1234", emptyList(), known))
        assertFalse(SelfTransfer.byContent("x", "Rs 500 debited from A/c XX1234 to A/c XX9999", "1234", emptyList(), known))
    }

    @Test fun parsedNotNullForCommonFormats() {
        assertNotNull(parse("Dear Customer, Rs.649.00 has been debited from your account XX4321 towards NETFLIX on 03-01-25. -IDFC FIRST", "VM-IDFCFB"))
    }

    // ------------------------------------------------------------ accounts

    @Test fun bankAccountWithBalance() {
        val p = parse("Rs.500.00 debited from A/c XX1234 on 12-11-26 to VPA swiggy@icici. Avl Bal Rs.12,345.67 -HDFC Bank")!!
        assertEquals("HDFC Bank", p.bank)
        assertEquals(AcctType.BANK, p.acctType)
        assertEquals(12345.67, p.balance!!, 0.001)
    }

    @Test fun creditCardWithLimit() {
        val p = parse("Rs.2,999.00 spent on ICICI Bank Credit Card XX4321 at AMAZON on 12-Nov-26. Avl Lmt: Rs.86,500.00", "JD-ICICIT")!!
        assertEquals("ICICI Bank", p.bank)
        assertEquals(AcctType.CREDIT_CARD, p.acctType)
        assertEquals(86500.0, p.balance!!, 0.001)
    }

    @Test fun debitCard() {
        val p = parse("Rs 750 spent on your SBI Debit Card ending 5678 at BIG BAZAAR on 12Nov26", "BZ-SBIINB")!!
        assertEquals("SBI", p.bank)
        assertEquals(AcctType.DEBIT_CARD, p.acctType)
    }

    @Test fun gatewayPrefixRemoved() {
        val p = parse("Rs.249.00 spent on HDFC Bank Card x4321 at PAYTM*ZOMATO on 2026-11-12. Avl Lmt: Rs.45,000")!!
        assertEquals("Zomato", p.merchant)
    }

    // --------------------------------------------------------- categories

    private fun cat(merchant: String, body: String = "Sent Rs.100 via UPI") = Categorizer.categorize(merchant, body, true, null)

    @Test fun brands() {
        assertEquals("Travel", cat("Uber India"))
        assertEquals("Travel", cat("Olacabs"))
        assertEquals("Travel", cat("Rapido"))
        assertEquals("Travel", cat("IRCTC"))
        assertEquals("Food", cat("Zomato Ltd"))
        assertEquals("Food", cat("Bundl Technologies"))
        assertEquals("Groceries", cat("Swiggy Instamart"))
        assertEquals("Groceries", cat("Zepto"))
        assertEquals("Shopping", cat("Myntra"))
        assertEquals("Entertainment", cat("Amazon Prime"))
        assertEquals("Fuel", cat("HPCL"))
        assertEquals("Bills", cat("Airtel"))
        assertEquals("Bills", cat("BESCOM"))
        assertEquals("Investment", cat("Zerodha Broking"))
        assertEquals("Insurance", cat("LIC of India"))
        assertEquals("Health", cat("Apollo Pharmacy"))
        assertEquals("Personal care", cat("Urban Company"))
        assertEquals("EMI/Loan", cat("Bajaj Finance"))
        assertEquals(Categories.SELF, cat("CRED Club"))
    }

    @Test fun shopTypes() {
        assertEquals("Health", cat("Shree Ganesh Medical"))
        assertEquals("Groceries", cat("Balaji Kirana Store"))
        assertEquals("Food", cat("Hotel Sagar Navi Mumbai"))
        assertEquals("Shopping", cat("Mobile Mart"))
        assertEquals("Fuel", cat("Om Sai Petroleum"))
        assertEquals("Personal care", cat("Style Unisex Salon"))
    }

    @Test fun bodyClues() {
        assertEquals("Investment", cat("BSE", "Rs 5000 debited via NACH for SIP"))
        assertEquals("EMI/Loan", cat("ACH", "Rs 12000 debited by NACH towards loan account"))
        assertEquals("Travel", cat("NETC", "Rs 85 debited for FASTag toll"))
    }

    @Test fun personToPerson() {
        assertEquals("Transfers", cat("Rahul Sharma", "Sent Rs.500 to RAHUL SHARMA via UPI"))
        assertEquals("Transfers", cat("x", "Sent Rs.500 to 9876543210@ybl"))
        assertEquals(Categories.OTHERS, cat("Sharma Enterprises", "Sent Rs.500 via UPI"))
    }

    // --------------------------------------------------------- filters

    private fun reason(body: String, sender: String = "VM-HDFCBK") =
        (SmsParser.classify(sender, body) as? ParseResult.Skip)?.reason

    @Test fun otpMessagesAreSkipped() {
        assertEquals(SkipReason.OTP, reason("Use OTP 482913 to complete txn of Rs 1,200 at Flipkart. Do not share."))
        assertEquals(SkipReason.OTP, reason("Your OTP for transaction of INR 999.00 at Amazon is 123456"))
        assertEquals(SkipReason.OTP, reason("482913 is the One Time Password for your txn of Rs 500 on card XX4321"))
    }

    @Test fun otpFooterOnRealDebitIsNotSkipped() {
        assertNotNull(parse("Rs.500 debited from A/c XX1234 to VPA swiggy@icici. Never share your OTP/PIN with anyone -HDFC Bank"))
    }

    @Test fun mandateDebitsAreKeptButSetupIsSkipped() {
        assertNotNull(parse("Rs 5,000 debited from A/c XX1234 towards SIP via e-mandate. UMRN HDFC0001 -HDFC Bank"))
        assertNotNull(parse("Rs 799 debited from card XX4321 for Netflix under standing instruction", "JD-ICICIT"))
        assertEquals(SkipReason.MANDATE_SETUP, reason("Your e-mandate for Rs 5000 has been successfully registered with HDFC Bank"))
        assertEquals(SkipReason.MANDATE_SETUP, reason("UPI AutoPay request of Rs 199 for Netflix. Approve in your UPI app"))
    }

    @Test fun promoFooterOnRealTxnIsKept() {
        assertNotNull(parse("Rs 349 spent on HDFC Bank Card XX4321 at Swiggy. Avl Lmt Rs 45000. Get exclusive offers on loans!"))
        assertEquals(SkipReason.PROMO, reason("Congratulations! You are eligible for a pre-approved loan of up to Rs 5,00,000. Apply now"))
    }

    @Test fun reversalIsARefundAndFailuresAreSkipped() {
        val p = parse("Rs 500 debited for failed txn has been reversed to your A/c XX1234 -HDFC Bank")!!
        assertFalse(p.isDebit)
        assertEquals(SkipReason.FAILED, reason("Txn of Rs 500 at Amazon using card XX4321 has been declined"))
    }

    @Test fun senders() {
        assertTrue(parse("You paid Rs.349 to Swiggy using UPI. UPI Ref 123456789012", "VM-PHONPE")!!.fromApp)
        assertFalse(parse("Sent Rs.349 From HDFC Bank A/C *1234 To SWIGGY On 12/11/26 Ref 412345678901")!!.fromApp)
        assertEquals(SkipReason.NOT_FROM_BANK, reason("Get Rs 100 credited to your wallet on first order", "AD-RANDOM"))
        assertTrue(SmsParser.classify("AD-RANDOM", "Get Rs 100 credited to your wallet on first order", lenient = true) is ParseResult.Ok)
    }
}
