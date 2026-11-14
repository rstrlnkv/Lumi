package ru.digitalpunk.lumi.about

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Reads the AboutLibraries export the build generated for the debug variant (`./gradlew :app:prepareLibraryDefinitionsDebug`
 * writes it) and checks that every library the app bundles at runtime is listed with a license and its text.
 */
class LicenseCompletenessTest {
    private val export = File("build/generated/aboutLibraries/debug/res/raw/aboutlibraries.json")

    private fun libraries(): List<Library> {
        assertTrue(export.exists(), "run ./gradlew :app:prepareLibraryDefinitionsDebug first: ${export.absolutePath}")
        return parseLicenses(export.readText())
    }

    @Test fun `every runtime dependency of the three modules has an entry`() {
        val ids = libraries().map { it.id }.toSet()
        val required = listOf(
            "org.mozilla.geckoview:geckoview", "androidx.room:room-runtime", "androidx.datastore:datastore-preferences",
            "androidx.biometric:biometric", "androidx.compose.material3:material3", "androidx.compose.ui:ui",
            "androidx.compose.material:material-icons-core", "androidx.activity:activity-compose",
            "androidx.lifecycle:lifecycle-runtime-compose", "org.jetbrains.kotlinx:kotlinx-coroutines-core",
            "com.google.material-symbols:material-symbols-rounded",
        )
        assertEquals(emptyList(), required.filterNot { it in ids })
    }

    @Test fun `no test-only library is listed`() {
        val ids = libraries().map { it.id }
        assertEquals(emptyList(), ids.filter { "junit" in it || "kotlin-test" in it || it.startsWith("org.json:") })
    }

    @Test fun `every entry has a name, a version or a hand-written source, and a license`() {
        val bad = libraries().filter { it.name.isBlank() || it.licenses.isEmpty() }.map { it.id }
        assertEquals(emptyList(), bad)
    }

    @Test fun `every entry has its license text`() {
        val noText = libraries().filter { lib -> lib.licenses.any { it.text.isBlank() } }.map { "${it.id} ${it.licenses.map { l -> l.name }}" }
        assertEquals(emptyList(), noText)
    }

    @Test fun `library ids are unique`() {
        val ids = libraries().map { it.id }
        assertEquals(ids.distinct(), ids)
    }
}
