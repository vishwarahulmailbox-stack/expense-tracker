package com.expensetracker.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The shipped model must load, hash exactly like the Python trainer, and categorise sensibly. */
class CategoryModelTest {
    private val model by lazy { File("src/main/assets/category_model.bin").inputStream().use { CategoryModel.read(it) } }

    @Test fun hashMatchesTrainer() {
        // Values printed by scripts/train_category_model.py
        assertEquals(2576452499L, CategoryModel.fnv("c:#sw").toLong() and 0xFFFFFFFFL)
        assertEquals(2963, model.bucket("c:#sw"))
        assertEquals(16182, model.bucket("w:swiggy"))
        assertEquals(14584, model.bucket("b:shree_balaji"))
    }

    private fun top(name: String, user: UserCounts? = null) = model.predict(name, user).first()

    @Test fun unseenShopNames() {
        assertEquals("Health", top("Shree Balaji Medicos").first)
        assertEquals("Food", top("Sharma Da Dhaba").first)
        assertEquals("Groceries", top("Kumar Vegetables").first)
        assertEquals("Travel", top("Royal Tours and Travels").first)
        assertEquals("Transfers", top("Priya Sharma").first)
        assertEquals("Fuel", top("Sai Krupa Petroleum").first)
    }

    @Test fun ambiguousNamesAreNotConfident() {
        assertTrue(top("Raj Enterprises").second < CategoryAI.APPLY)
        assertTrue(top("ABC Traders").second < CategoryAI.APPLY)
    }

    @Test fun learnsFromCorrections() {
        val user = UserCounts()
        val name = "Raj Enterprises"
        val buckets = CategoryModel.features(name).map { model.bucket(it) }.distinct()
        // A few corrections teach it this is (say) a custom "Office supplies" category
        repeat(3) { user.add(buckets, "Office supplies", 1f) }
        val p = top(name, user)
        assertEquals("Office supplies", p.first)
        assertTrue(p.second > 0.6)
        // ...and a similar name benefits too
        assertEquals("Office supplies", top("Raj Enterprises Pvt Ltd", user).first)
    }

    @Test fun codesAreNotUsableNames() {
        assertFalse(CategoryAI.usableName("PAYTMQR281005"))
        assertFalse(CategoryAI.usableName("12"))
        assertTrue(CategoryAI.usableName("Kabir Store"))
    }
}
