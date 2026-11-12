package ru.digitalpunk.lumi.data.tabs

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
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

    private companion object {
        const val NAME = "migration-test"
    }
}
