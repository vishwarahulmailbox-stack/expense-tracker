package com.expensetracker.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The whole-SMS model must featurise exactly like scripts/train_sms_model.py and read the clues in real formats. */
class SmsModelTest {
    private val model by lazy {
        File("src/main/assets/sms_model.bin").inputStream().use { CategoryModel.read(it, CategoryModel::smsFeatures) }
    }

    @Test fun featuresMatchTrainer() {
        val s = "Union Bank of India A/c*6081 Dr Rs11.03 on 04-11-26 20:50 by MB 961537781313 Fv.Isara, Bal Rs26807.56 Sent to zomato.order@okicici"
        val expected = listOf(
            "s:isara", "q:#isa", "q:isar", "q:sara", "q:ara#", "s:zomato", "q:#zom", "q:zoma", "q:omat", "q:mato", "q:ato#",
            "s:order", "q:#ord", "q:orde", "q:rder", "q:der#", "t:isara_zomato", "t:zomato_order",
        )
        assertEquals(expected, CategoryModel.smsFeatures(s))
        assertEquals(listOf(15766, 12322, 9829, 16503, 9305), expected.take(5).map { model.bucket(it) })
    }

    private fun top(body: String) = model.predict(body).first()

    @Test fun readsCluesInTheSms() {
        assertEquals("ATM/Cash", top("Your SB A/c *6081 Debited for Rs.5933.73 on 25-02-2026 07:42:14 by ATM Avl Bal Rs.73309.80, If not you, Call 3596721962 -Union Bank of India").first)
        assertEquals("Insurance", top("Union Bank of India A/c *6081 Debited Rs:949.08 on 31-05-2026 13:21:04 by Mob Bk ref no 482955558105, Fvg: Policyba Avl Bal Rs:32642.86. Not you?Call 66013348").first)
        assertEquals("Travel", top("Spent Rs.210.3 On HDFC Bank Card 8422 At UBER INDIA SYSTE PVT L On 2026-01-16:07:00:17.Not You? To Block+Reissue Call 48054547103").first)
        assertEquals("Food", top("Spent Rs.646 On HDFC Bank Card 8422 At WWW SWIGGY IN On 2026-02-03:08:12:20.Not You? To Block+Reissue Call 48054547103").first)
        assertEquals("Health", top("Sent Rs.502.19 from Kotak Bank A/c X0119 to SHREE GAJANAND CLINI on 02-09-26. UPI Ref 841118253521.").first)
        assertEquals("Charges & fees", top("Rs.299 debited from A/c XX1234 on 03-11-26 towards debit card annual charges incl GST").first)
    }

    @Test fun staysUnsureWhenTheSmsSaysNothing() {
        // Nothing but bank boilerplate: no features at all, so no guess.
        val p = model.predict("A/c *6081 Debited for Rs:64.08 on 15-01-2026 22:31:18 by Mob Bk ref no 419613442486 Avl Bal Rs:369697.54.If not you, Call 3596721962 -Union Bank of India").firstOrNull()
        assertTrue(p == null || p.first == Categories.OTHERS || p.second < CategoryAI.APPLY)
    }
}
