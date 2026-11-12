package ru.digitalpunk.lumi.data.privatetabs

import java.io.File
import java.nio.file.Files
import java.security.ProviderException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PrivateTabStoreTest {
    private val root: File = Files.createTempDirectory("private").toFile()
    private val dir = File(root, "private-tabs")
    private val aead = SoftwareAead()
    private fun store(max: Int = 8 * 1024 * 1024) = PrivateTabStore(dir, aead, Dispatchers.Unconfined, maxFileBytes = max)
    private val tabs = listOf(PrivateStoredTab("p1", "https://a.example/", "A", "{}"), PrivateStoredTab("p2", "https://b.example/", "B", null))

    @Test fun saveThenLoadReturnsTheTabs() = runBlocking {
        assertTrue(store().save(tabs))
        assertEquals(PrivateLoad.Loaded(tabs), store().load())
    }

    @Test fun wrongKeyDropsTheFileAndDeletesTheKey() = runBlocking {
        store().save(tabs)
        aead.key = SoftwareAead.newKey()
        assertEquals(PrivateLoad.Dropped, store().load())
        assertFalse(File(dir, "tabs.bin").exists())
        assertEquals(1, aead.deleted)
    }

    @Test fun corruptFileIsDroppedNotThrown() = runBlocking {
        dir.mkdirs()
        File(dir, "tabs.bin").writeBytes(byteArrayOf(1, 2, 3))
        assertEquals(PrivateLoad.Dropped, store().load())
        assertFalse(File(dir, "tabs.bin").exists())
    }

    @Test fun aTransientKeystoreFailureKeepsTheFileAndTheKeyForTheNextStart() = runBlocking {
        store().save(tabs)
        aead.failWith = AeadException(ProviderException("keystore"))
        assertEquals(PrivateLoad.Retry, store().load())
        assertFalse(store().save(tabs))
        assertTrue(File(dir, "tabs.bin").exists())
        assertEquals(0, aead.deleted)
        aead.failWith = null
        assertEquals(PrivateLoad.Loaded(tabs), store().load())
    }

    @Test fun aSaveOverTheFileCapDropsTheLargestSessionStatesFirst() = runBlocking {
        val big = listOf(
            PrivateStoredTab("p1", "https://a/", "A", "s".repeat(300)),
            PrivateStoredTab("p2", "https://b/", "B", "l".repeat(600)),
            PrivateStoredTab("p3", "https://c/", "C", "m".repeat(400)),
        )
        assertTrue(store(max = 1000).save(big))
        val loaded = assertIs<PrivateLoad.Loaded>(store(max = 1000).load()).tabs
        assertEquals(listOf("p1", "p2", "p3"), loaded.map { it.id })
        assertEquals(listOf(300, null, 400), loaded.map { it.sessionState?.length })
    }

    @Test fun aSaveWhoseSealedFileWouldBeOneByteOverTheCapStaysUnderIt() = runBlocking {
        val tab = PrivateStoredTab("p1", "https://a/", "A", "s".repeat(300))
        val max = PrivateTabsCodec.encode(listOf(tab)).size + PrivateEnvelope.OVERHEAD - 1
        assertTrue(store(max = max).save(listOf(tab)))
        assertTrue(File(dir, "tabs.bin").length() <= max)
        assertEquals(PrivateLoad.Loaded(listOf(tab.copy(sessionState = null))), store(max = max).load())
    }

    @Test fun savingNothingDeletesTheFile() = runBlocking {
        store().save(tabs)
        assertTrue(store().save(emptyList()))
        assertFalse(File(dir, "tabs.bin").exists())
        assertEquals(PrivateLoad.Empty, store().load())
    }

    @Test fun leftoverTmpFileIsRemovedOnLoad() = runBlocking {
        dir.mkdirs()
        val tmp = File(dir, "tabs.bin.tmp").apply { writeBytes(byteArrayOf(1)) }
        store().load()
        assertFalse(tmp.exists())
    }

    @Test fun oversizeStateIsDroppedButUrlKept() = runBlocking {
        val big = listOf(PrivateStoredTab("p", "https://a/", "A", "x".repeat(MAX_PRIVATE_STATE_BYTES + 1)))
        store().save(big)
        assertEquals(PrivateLoad.Loaded(listOf(PrivateStoredTab("p", "https://a/", "A", null))), store().load())
    }

    @Test fun oversizeFileIsRefused() = runBlocking {
        assertFalse(store(max = 64).save(tabs))
        assertFalse(File(dir, "tabs.bin").exists())
    }

    @Test fun wipeDeletesFileAndKey() = runBlocking {
        store().save(tabs)
        assertTrue(store().wipe())
        assertFalse(File(dir, "tabs.bin").exists())
        assertEquals(1, aead.deleted)
    }

    @Test fun wipeWithNoStoredFileReportsNothingWiped() = runBlocking {
        assertFalse(store().wipe())
    }

    @Test fun fileNameCarriesNoTabId() = runBlocking {
        store().save(tabs)
        assertEquals(listOf("tabs.bin"), dir.list()!!.toList())
        assertFalse(File(dir, "tabs.bin").readBytes().toString(Charsets.ISO_8859_1).contains("a.example"))
    }

    @Test fun plainTabStateFolderIsNeverTouched() = runBlocking {
        val plain = File(root, "tab-state").apply { mkdirs() }
        val kept = File(plain, "p1.json").apply { writeText("{}") }
        store().save(tabs)
        store().load()
        store().wipe()
        assertTrue(kept.exists())
        assertEquals("{}", kept.readText())
        assertEquals(listOf("p1.json"), plain.list()!!.toList())
    }

    @Test fun aMissingFileLoadsAsEmpty() = runBlocking<Unit> {
        assertIs<PrivateLoad.Empty>(store().load())
    }
}
