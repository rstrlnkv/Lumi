package ru.digitalpunk.lumi.data.tabs

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlobFilesEdgeTest {
    private val root = createTempDirectory("blobroot").toFile()
    private val dir = File(root, "state")
    private val blobs = BlobFiles(dir, "json", maxBytes = 8)

    @Test fun writeWithTraversalIdDoesNotCreateFilesOutsideTheFolder() {
        blobs.write("../escaped", byteArrayOf(1))
        assertEquals(emptyList(), root.list()!!.filter { it != "state" }, "something landed beside the folder")
    }

    @Test fun readWithTraversalIdDoesNotReadOutsideTheFolder() {
        dir.mkdirs()
        File(root, "secret.json").writeBytes(byteArrayOf(7))
        assertNull(blobs.read("../secret"))
    }

    @Test fun deleteWithTraversalIdDoesNotRemoveOutsideTheFolder() {
        dir.mkdirs()
        val outside = File(root, "keep.json").apply { writeBytes(byteArrayOf(7)) }
        blobs.delete("../keep")
        assertTrue(outside.exists())
    }

    @Test fun idWithSlashDoesNotNeedASubfolder() {
        blobs.write("a/b", byteArrayOf(1))
        assertEquals(emptyList(), dir.walkTopDown().filter { it.isDirectory && it != dir }.toList())
    }

    @Test fun emptyIdIsRefusedOrHarmless() {
        val ok = blobs.write("", byteArrayOf(1))
        if (ok) assertContentEquals(byteArrayOf(1), blobs.read("")) else assertNull(blobs.read(""))
        assertTrue(dir.list().orEmpty().none { it.startsWith(".") && it != ".json" })
    }

    @Test fun readOfMissingFolderIsNull() = assertNull(blobs.read("a"))

    @Test fun sweepOfMissingFolderDoesNothing() = blobs.sweep(emptySet())

    @Test fun exactlyMaxBytesIsAccepted() {
        assertTrue(blobs.write("a", ByteArray(8)))
        assertEquals(8, blobs.read("a")!!.size)
    }

    @Test fun emptyContentRoundTrips() {
        assertTrue(blobs.write("a", ByteArray(0)))
        assertEquals(0, blobs.read("a")!!.size)
    }

    @Test fun secondWriteReplacesTheFirst() {
        blobs.write("a", byteArrayOf(1, 1, 1))
        blobs.write("a", byteArrayOf(2))
        assertContentEquals(byteArrayOf(2), blobs.read("a"))
        assertEquals(listOf("a.json"), dir.list()!!.toList())
    }

    @Test fun idsThatAreNotASafeKeyNeverTouchTheDisk() {
        listOf("a.b", "a b", "a\\b", "..", ".", "a".repeat(65)).forEach {
            assertFalse(blobs.write(it, byteArrayOf(1)), "write $it")
            assertNull(blobs.read(it), "read $it")
        }
        assertEquals(emptyList(), dir.list().orEmpty().toList())
    }

    @Test fun keyWithASlashNeverReachesAFileInASubfolder() {
        File(dir, "sub").mkdirs()
        File(dir, "sub/x.json").writeBytes(byteArrayOf(7))
        assertNull(blobs.read("sub/x"))
        assertFalse(blobs.write("sub/y", byteArrayOf(1)))
        blobs.delete("sub/x")
        assertTrue(File(dir, "sub/x.json").exists())
        assertFalse(File(dir, "sub/y.json").exists())
    }

    @Test fun uuidAndSixtyFourCharKeysAreAccepted() {
        listOf("0b9a3c1e-5d2f-4f7a-9c1d-2e8f6a4b7c10", "A".repeat(64)).forEach {
            assertTrue(blobs.write(it, byteArrayOf(1)), it)
            assertContentEquals(byteArrayOf(1), blobs.read(it))
        }
    }

    @Test fun readRefusesAFileLargerThanMaxBytes() {
        dir.mkdirs()
        File(dir, "a.json").writeBytes(ByteArray(9))
        assertNull(blobs.read("a"))
    }

    @Test fun sweepLeavesFilesThatAreNotStateFiles() {
        dir.mkdirs()
        File(dir, "notes.txt").writeText("x")
        File(dir, "b.json").writeText("x")
        File(dir, "c.json.tmp").writeText("x")
        blobs.sweep(emptySet())
        assertEquals(listOf("notes.txt"), dir.list()!!.toList())
    }

    @Test fun sweepRemovesStrayTmpOfAKeptKeyButKeepsItsRealFile() {
        blobs.write("a", byteArrayOf(1))
        File(dir, "a.json.tmp").writeText("half")
        blobs.sweep(setOf("a"))
        assertEquals(listOf("a.json"), dir.list()!!.toList())
    }
}
