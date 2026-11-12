package ru.digitalpunk.lumi.data.tabs

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TabRepositorySaveTest {
    private val dir = File(createTempDirectory("repo").toFile(), "state")
    private val dao = FakeTabDao()
    private val repo = TabRepository(dao, BlobFiles(dir, "json", maxBytes = 8), Dispatchers.Unconfined, now = { 5L })

    private fun save(vararg tabs: StoredTab) = runBlocking { repo.save(tabs.toList(), tabs.firstOrNull()?.id) }

    @Test fun savingAStateThatFitsWritesItAndReturnsTrue() {
        assertTrue(save(StoredTab("a", "https://x", "t", "123")))
        assertContentEquals("123".encodeToByteArray(), File(dir, "a.json").readBytes())
        assertEquals(listOf("a"), dao.rows.map { it.id })
    }

    @Test fun stateFileThatCannotBeWrittenMakesSaveReturnFalse() {
        File(dir, "a.json").mkdirs() // a folder where the file must go: the rename fails
        assertFalse(save(StoredTab("a", "https://x", "t", "123")))
    }

    @Test fun stateOverTheLimitIsDroppedOnPurposeAndSaveStillReturnsTrue() {
        assertTrue(save(StoredTab("a", "https://x", "t", "123456789")))
        assertFalse(File(dir, "a.json").exists())
    }

    @Test fun tabIdThatIsNotASafeKeyMakesSaveReturnFalse() {
        assertFalse(save(StoredTab("../a", "https://x", "t", "1")))
    }
}
