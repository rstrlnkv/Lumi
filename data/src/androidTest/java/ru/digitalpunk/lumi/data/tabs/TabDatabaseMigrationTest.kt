package ru.digitalpunk.lumi.data.tabs

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import ru.digitalpunk.lumi.data.downloads.DownloadEntity
import ru.digitalpunk.lumi.data.library.HistoryEntity
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Runs on a phone or emulator (`./gradlew :data:connectedDebugAndroidTest`); `./gradlew test` does not run it. */
class TabDatabaseMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val helper = MigrationTestHelper(instrumentation, TabDatabase::class.java)

    @Test fun migratingV1ToV2KeepsTabsAndPutsThemInTheSharedList() {
        helper.createDatabase(NAME, 1).apply {
            execSQL("INSERT INTO tab (id, position, url, title, lastActiveAt) VALUES ('a', 0, 'https://x', 't', 7)")
            execSQL("INSERT INTO tab_selection (id, tabId) VALUES (0, 'a')")
            close()
        }
        val db = helper.runMigrationsAndValidate(NAME, 2, true)
        db.query("SELECT id, groupId FROM tab").use {
            assertTrue(it.moveToFirst())
            assertEquals("a", it.getString(0))
            assertTrue(it.isNull(1))
        }
        db.query("SELECT tabId, currentGroupId FROM tab_selection").use {
            assertTrue(it.moveToFirst())
            assertEquals("a", it.getString(0))
            assertTrue(it.isNull(1))
        }
        db.query("SELECT COUNT(*) FROM tab_group").use {
            assertTrue(it.moveToFirst())
            assertEquals(0, it.getInt(0))
        }
    }

    @Test fun migratedDatabaseOpensThroughRoomAndLoadsTheOldTabs() = runBlocking {
        helper.createDatabase(NAME, 1).apply {
            execSQL("INSERT INTO tab (id, position, url, title, lastActiveAt) VALUES ('a', 0, 'https://x', 't', 7)")
            close()
        }
        val db = Room.databaseBuilder(instrumentation.targetContext, TabDatabase::class.java, NAME).build()
        try {
            assertEquals(listOf("a"), db.tabDao().tabs().map { it.id })
            assertNull(db.tabDao().tabs().single().groupId)
            assertEquals(emptyList(), db.tabDao().groups())
        } finally {
            db.close()
        }
    }

    @Test fun migratingV2ToV3KeepsTabsAndGroupsAndAddsEmptyLibraryTables() {
        helper.createDatabase(NAME, 2).apply {
            execSQL("INSERT INTO tab_group (id, name, position) VALUES ('g', 'Work', 0)")
            execSQL("INSERT INTO tab (id, position, url, title, lastActiveAt, groupId) VALUES ('a', 0, 'https://x', 't', 7, 'g')")
            execSQL("INSERT INTO tab_selection (id, tabId, currentGroupId) VALUES (0, 'a', 'g')")
            close()
        }
        val db = helper.runMigrationsAndValidate(NAME, 3, true)
        db.query("SELECT t.id, t.groupId, g.name, s.currentGroupId FROM tab t, tab_group g, tab_selection s").use {
            assertTrue(it.moveToFirst())
            assertEquals(listOf("a", "g", "Work", "g"), (0..3).map { i -> it.getString(i) })
        }
        listOf("bookmark_node", "history").forEach { table ->
            db.query("SELECT COUNT(*) FROM $table").use {
                assertTrue(it.moveToFirst())
                assertEquals(0, it.getInt(0), table)
            }
        }
    }

    @Test fun migratingV1ToV3InOneStepKeepsTheTabAndTheLibraryWorks() = runBlocking<Unit> {
        helper.createDatabase(NAME, 1).apply {
            execSQL("INSERT INTO tab (id, position, url, title, lastActiveAt) VALUES ('a', 0, 'https://x', 't', 7)")
            close()
        }
        helper.runMigrationsAndValidate(NAME, 3, true).close()
        val db = Room.databaseBuilder(instrumentation.targetContext, TabDatabase::class.java, NAME).build()
        try {
            assertEquals(listOf("a"), db.tabDao().tabs().map { it.id })
            assertEquals(emptyList(), db.libraryDao().nodes())
            db.libraryDao().insertVisit(HistoryEntity(url = "https://x", title = "", visitedAt = 1))
            db.libraryDao().titleLatest("https://x", "X")
            assertEquals("X", db.libraryDao().observeHistory(10).first().single().title)
        } finally {
            db.close()
        }
    }

    @Test fun migratingV3ToV4KeepsLibraryRowsAndAddsAnEmptyDownloadTable() {
        helper.createDatabase(NAME, 3).apply {
            execSQL("INSERT INTO tab_group (id, name, position) VALUES ('g', 'Work', 0)")
            execSQL("INSERT INTO tab (id, position, url, title, lastActiveAt, groupId) VALUES ('a', 0, 'https://x', 't', 7, 'g')")
            execSQL("INSERT INTO bookmark_node (id, parentId, kind, title, url, position, createdAt) VALUES ('b', 'root', 0, 'X', 'https://x', 0, 1)")
            execSQL("INSERT INTO history (id, url, title, visitedAt) VALUES (1, 'https://x', 'X', 5)")
            close()
        }
        val db = helper.runMigrationsAndValidate(NAME, 4, true)
        for ((table, count) in listOf("tab" to 1, "tab_group" to 1, "bookmark_node" to 1, "history" to 1, "download" to 0)) {
            db.query("SELECT COUNT(*) FROM $table").use {
                it.moveToFirst()
                assertEquals(count, it.getInt(0), table)
            }
        }
        db.close()
    }

    @Test fun migratingV1ToV4InOneStepKeepsTheTabAndDownloadsWork() = runBlocking<Unit> {
        helper.createDatabase(NAME, 1).apply {
            execSQL("INSERT INTO tab (id, position, url, title, lastActiveAt) VALUES ('a', 0, 'https://x', 't', 7)")
            close()
        }
        helper.runMigrationsAndValidate(NAME, 4, true).close()
        val db = Room.databaseBuilder(instrumentation.targetContext, TabDatabase::class.java, NAME).build()
        try {
            assertEquals(listOf("a"), db.tabDao().tabs().map { it.id })
            val dao = db.downloadDao()
            dao.insert(DownloadEntity(url = "https://x/a.pdf", fileName = "a.pdf", mimeType = "application/pdf", uri = null, site = "x", totalBytes = 1, doneBytes = 0, state = "Running", startedAt = 1))
            dao.insert(DownloadEntity(url = "https://x/b.pdf", fileName = "b.pdf", mimeType = "application/pdf", uri = null, site = "x", totalBytes = 1, doneBytes = 1, state = "Done", startedAt = 2))
            assertEquals(1, dao.deleteFinished())
            assertEquals(listOf("a.pdf"), dao.observe().first().map { it.fileName })
        } finally {
            db.close()
        }
    }

    private companion object {
        const val NAME = "migration-test"
    }
}
