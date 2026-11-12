package ru.digitalpunk.lumi.data.privatetabs

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

/** Files and inputs the M6b store tests did not feed: empty, truncated, unknown version, huge fields, a foreign IV size. */
class PrivateStoreEdgeInputsTest {
    private val root: File = Files.createTempDirectory("private-edge").toFile()
    private val dir = File(root, "private-tabs")
    private val file = File(dir, "tabs.bin")
    private val aead = SoftwareAead()
    private fun store() = PrivateTabStore(dir, aead, Dispatchers.Unconfined)
    private val tabs = listOf(PrivateStoredTab("p1", "https://a.example/", "A", null), PrivateStoredTab("p2", "https://b.example/", "B", "{}"))

    @Test fun zeroByteFileLoadsWithoutThrowingAndIsGone() = runBlocking<Unit> {
        dir.mkdirs()
        file.writeBytes(ByteArray(0))
        val load = store().load()
        assertFalse(load is PrivateLoad.Loaded)
        assertFalse(file.exists())
    }

    @Test fun truncatedStoredFileIsDroppedWithTheKey() = runBlocking {
        store().save(tabs)
        file.writeBytes(file.readBytes().copyOf(file.length().toInt() - 1))
        assertEquals(PrivateLoad.Dropped, store().load())
        assertFalse(file.exists())
        assertEquals(1, aead.deleted)
    }

    @Test fun storedFileCutInsideTheHeaderIsDropped() = runBlocking {
        store().save(tabs)
        file.writeBytes(file.readBytes().copyOf(3))
        assertEquals(PrivateLoad.Dropped, store().load())
        assertFalse(file.exists())
    }

    @Test fun storedFileWithAnUnknownVersionByteIsDroppedWithTheKey() = runBlocking {
        store().save(tabs)
        file.writeBytes(file.readBytes().also { it[4] = 2 })
        assertEquals(PrivateLoad.Dropped, store().load())
        assertFalse(file.exists())
        assertEquals(1, aead.deleted)
    }

    @Test fun fileLayoutIsPinnedSoOldFilesStayReadable() = runBlocking {
        store().save(tabs)
        val bytes = file.readBytes()
        assertContentEquals("LPT1".toByteArray(), bytes.copyOfRange(0, 4))
        assertEquals(1, bytes[4].toInt())
        assertEquals(12, bytes[5].toInt())
    }

    @Test fun anAeadThatReturnsAnIvOfAnotherSizeIsRefusedAtSeal() {
        val odd = object : Aead {
            override fun encrypt(plain: ByteArray, aad: ByteArray) = Sealed(ByteArray(16), plain)
            override fun decrypt(iv: ByteArray, ciphertext: ByteArray, aad: ByteArray) = ciphertext
            override fun deleteKey() = Unit
        }
        assertFailsWith<AeadException> { PrivateEnvelope.seal(odd, byteArrayOf(1)) }
    }

    @Test fun aTabWithNoHistoryComesBackWithNoState() = runBlocking {
        store().save(tabs)
        val loaded = assertIs<PrivateLoad.Loaded>(store().load())
        assertNull(loaded.tabs.first().sessionState)
        assertEquals(tabs, loaded.tabs)
    }

    @Test fun aFieldOfTheLimitSizeRoundTripsAndOneByteMoreIsRefusedByTheCodec() {
        val limit = 2 * 1024 * 1024
        val ok = listOf(PrivateStoredTab("p", "u", "x".repeat(limit), null))
        assertEquals(ok, PrivateTabsCodec.decode(PrivateTabsCodec.encode(ok)))
        val over = listOf(PrivateStoredTab("p", "u", "x".repeat(limit + 1), null))
        assertNull(PrivateTabsCodec.decode(PrivateTabsCodec.encode(over)))
    }

    @Test fun oneOversizeUrlDoesNotCostThePersonTheirOtherPrivateTabs() = runBlocking {
        val big = PrivateStoredTab("big", "data:text/html," + "x".repeat(3 * 1024 * 1024), "Big", null)
        store().save(listOf(tabs[0], big, tabs[1]))
        val loaded = store().load()
        // The saved file must either hold the small tabs on the next start, or the save must have refused the oversize one.
        val kept = (loaded as? PrivateLoad.Loaded)?.tabs?.map { it.id }.orEmpty()
        assertEquals(listOf("p1", "p2"), kept.filter { it != "big" })
    }
}
