package ru.digitalpunk.lumi.data.tabs

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SnapshotStoreTest {
    private val dir = createTempDirectory("snapshots").toFile()
    private val store = SnapshotStore(BlobFiles(dir, "webp", maxBytes = 8))

    @Test fun saveThenLoadReturnsSameBytes() {
        assertTrue(store.save("tab-1", byteArrayOf(1, 2, 3)))
        assertContentEquals(byteArrayOf(1, 2, 3), store.load("tab-1"))
    }

    @Test fun keyThatIsNotSafeNeverReachesTheDisk() {
        val outside = File(dir.parentFile, "outside.webp").apply { writeBytes(byteArrayOf(9)) }
        assertFalse(store.save("../outside", byteArrayOf(1)))
        assertNull(store.load("../outside"))
        assertContentEquals(byteArrayOf(9), outside.readBytes())
        outside.delete()
    }

    @Test fun snapshotOverTheLimitIsRefused() {
        assertFalse(store.save("tab-1", ByteArray(9)))
        assertNull(store.load("tab-1"))
    }

    @Test fun sweepRemovesSnapshotsOfClosedTabsOnly() {
        store.save("open", byteArrayOf(1))
        store.save("closed", byteArrayOf(2))
        store.sweep(setOf("open"))
        assertContentEquals(byteArrayOf(1), store.load("open"))
        assertNull(store.load("closed"))
    }

    @Test fun deleteRemovesOneSnapshot() {
        store.save("a", byteArrayOf(1))
        store.delete("a")
        assertNull(store.load("a"))
    }
}

class SnapshotStoreEdgeTest {
    private val dir = createTempDirectory("snapshots-edge").toFile()
    private val store = SnapshotStore(BlobFiles(dir, "webp", maxBytes = 8))

    @Test fun sweepWithAnEmptyKeepSetRemovesEverySnapshotAndLeavesOtherFiles() {
        store.save("a", byteArrayOf(1))
        store.save("b", byteArrayOf(2))
        val foreign = File(dir, "notes.txt").apply { writeText("x") }
        File(dir, "c.webp.tmp").writeBytes(byteArrayOf(3))
        store.sweep(emptySet())
        assertNull(store.load("a"))
        assertNull(store.load("b"))
        assertFalse(File(dir, "c.webp.tmp").exists())
        assertTrue(foreign.exists())
    }

    @Test fun sweepBeforeTheFolderExistsDoesNotThrow() {
        SnapshotStore(BlobFiles(File(dir, "missing"), "webp", 8)).sweep(setOf("a"))
    }

    @Test fun sweepWithAnUnsafeKeyInKeepDoesNotKeepOrCreateAnything() {
        store.save("a", byteArrayOf(1))
        store.sweep(setOf("../a"))
        assertNull(store.load("a"))
    }

    @Test fun emptyBytesAreStoredAndReadBackEmpty() {
        assertTrue(store.save("e", ByteArray(0)))
        assertContentEquals(ByteArray(0), store.load("e"))
    }

    @Test fun savingTwiceKeepsTheLatestBytes() {
        store.save("a", byteArrayOf(1))
        store.save("a", byteArrayOf(2, 2))
        assertContentEquals(byteArrayOf(2, 2), store.load("a"))
    }

    @Test fun oversizedSecondSaveRemovesTheOlderSnapshot() {
        store.save("a", byteArrayOf(1))
        assertFalse(store.save("a", ByteArray(9)))
        assertNull(store.load("a"))
    }

    @Test fun productionStoreLimitIs512KiB() {
        val limit = SnapshotStore::class.java.getDeclaredField("MAX_SNAPSHOT_BYTES").apply { isAccessible = true }.getInt(null)
        assertEquals(512 * 1024, limit)
    }
}
