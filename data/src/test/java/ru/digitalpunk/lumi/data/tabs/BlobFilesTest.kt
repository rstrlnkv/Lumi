package ru.digitalpunk.lumi.data.tabs

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlobFilesTest {
    private val dir = createTempDirectory("blobs").toFile()
    private val blobs = BlobFiles(dir, "json", maxBytes = 8)

    @Test fun writeThenReadReturnsSameBytes() {
        assertTrue(blobs.write("a", byteArrayOf(1, 2, 3)))
        assertContentEquals(byteArrayOf(1, 2, 3), blobs.read("a"))
    }

    @Test fun readOfMissingKeyIsNull() = assertNull(blobs.read("nope"))

    @Test fun overLimitIsRefusedAndOlderFileRemoved() {
        blobs.write("a", byteArrayOf(1))
        assertFalse(blobs.write("a", ByteArray(9)))
        assertNull(blobs.read("a"))
    }

    @Test fun sweepDeletesFilesNotKept() {
        blobs.write("a", byteArrayOf(1))
        blobs.write("b", byteArrayOf(2))
        File(dir, "c.json.tmp").writeText("x")
        blobs.sweep(setOf("a"))
        assertContentEquals(byteArrayOf(1), blobs.read("a"))
        assertNull(blobs.read("b"))
        assertEquals(listOf("a.json"), dir.list()!!.toList())
    }

    @Test fun failedWriteLeavesOlderFileIntact() {
        blobs.write("a", byteArrayOf(1))
        // A directory squatting on the .tmp name makes the next write fail.
        File(dir, "a.json.tmp").mkdir()
        assertFalse(blobs.write("a", byteArrayOf(2)))
        assertContentEquals(byteArrayOf(1), blobs.read("a"))
    }
}
