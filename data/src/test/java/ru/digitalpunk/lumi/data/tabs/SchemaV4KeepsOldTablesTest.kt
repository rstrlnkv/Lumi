package ru.digitalpunk.lumi.data.tabs

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What a migration test on a device would prove, from the exported schemas: v4 only adds `download`. */
class SchemaV4KeepsOldTablesTest {
    private val dir = File("schemas/ru.digitalpunk.lumi.data.tabs.TabDatabase")
    private fun sql(version: Int) = Regex(""""createSql": "([^"]*)"""").findAll(File(dir, "$version.json").readText()).map { it.groupValues[1] }.toList()

    @Test fun everyTableAndIndexOfVersionThreeIsUnchangedInVersionFour() {
        val v3 = sql(3)
        val v4 = sql(4)
        assertTrue(v3.size >= 5, "v3 sql statements: ${v3.size}")
        for (statement in v3) assertTrue(statement in v4, "changed or dropped in v4: $statement")
    }

    @Test fun versionFourAddsOnlyTheDownloadTable() {
        val added = sql(4) - sql(3).toSet()
        assertTrue(added.isNotEmpty())
        assertTrue(added.filter { it.startsWith("CREATE INDEX") }.all { "index_download_" in it }, added.toString())
        assertTrue(added.single { it.startsWith("CREATE TABLE") }.contains("`fileName`"))
        assertEquals(1, added.count { it.startsWith("CREATE TABLE") })
    }

    @Test fun theDatabaseDeclaresTheThreeToFourAutoMigrationAndTheVersionIsFour() {
        val source = File("src/main/java/ru/digitalpunk/lumi/data/tabs/TabDatabase.kt").readText()
        assertTrue("AutoMigration(from = 3, to = 4)" in source)
        assertEquals(4, TAB_DATABASE_VERSION)
    }
}
