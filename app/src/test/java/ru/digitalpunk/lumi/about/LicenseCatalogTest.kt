package ru.digitalpunk.lumi.about

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LicenseCatalogTest {
    // A real AboutLibraries export cut to three libraries; Activity was given a second license.
    private val json = javaClass.getResource("/about/aboutlibraries.json")!!.readText()

    @Test fun `libraries are sorted by name ignoring case`() {
        val names = parseLicenses(json).map { it.name }
        assertEquals(names.sortedWith(String.CASE_INSENSITIVE_ORDER), names)
        assertEquals(3, names.size)
    }

    @Test fun `a library with two licenses gets two texts`() {
        val activity = parseLicenses(json).single { it.id == "androidx.activity:activity" }
        assertEquals("1.13.0", activity.version)
        assertEquals(listOf("Apache License 2.0", "Mozilla Public License 2.0"), activity.licenses.map { it.name })
        assertTrue(activity.licenses.all { it.text.isNotBlank() })
    }

    @Test fun `a license without text gives an empty text`() {
        val gms = parseLicenses(json).single { it.id == "com.google.android.gms:play-services-base" }
        assertEquals("", gms.licenses.single().text)
    }

    @Test fun `garbage input gives an empty list`() {
        assertEquals(emptyList(), parseLicenses(""))
        assertEquals(emptyList(), parseLicenses("{not json"))
        assertEquals(emptyList(), parseLicenses("[]"))
    }
}
