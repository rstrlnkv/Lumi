package ru.digitalpunk.lumi.data.tabs

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Reads files, so it expects the module folder as the working directory (Gradle's default). */
class SchemaPolicyTest {
    private val schemas = File("schemas/ru.digitalpunk.lumi.data.tabs.TabDatabase")

    @Test fun everyDatabaseVersionUpToTheCurrentOneHasAnExportedSchemaFile() {
        (1..TAB_DATABASE_VERSION).forEach { assertTrue(File(schemas, "$it.json").isFile, "missing schema $it.json") }
    }

    @Test fun versionTwoSchemaHasTheGroupTableAndBothNewColumns() {
        val json = File(schemas, "2.json").readText()
        assertTrue("\"tableName\": \"tab_group\"" in json)
        assertTrue("\"fieldPath\": \"groupId\"" in json)
        assertTrue("\"fieldPath\": \"currentGroupId\"" in json)
    }

    @Test fun versionThreeSchemaHasTheBookmarkAndHistoryTables() {
        val json = File(schemas, "3.json").readText()
        assertTrue("\"tableName\": \"bookmark_node\"" in json)
        assertTrue("\"tableName\": \"history\"" in json)
    }

    @Test fun noMainSourceUsesADestructiveMigrationFallback() {
        val hits = File("src/main").walkTopDown().filter { it.isFile && it.extension == "kt" && "fallbackToDestructive" in it.readText() }.toList()
        assertTrue(hits.isEmpty(), "destructive fallback in $hits")
    }
}
