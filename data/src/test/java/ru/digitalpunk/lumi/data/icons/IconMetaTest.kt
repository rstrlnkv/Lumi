package ru.digitalpunk.lumi.data.icons

import java.nio.ByteBuffer
import java.util.zip.CRC32
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IconMetaTest {
    private val meta = IconMeta(srcPx = 57, vector = false, themeColor = 0xFF336699.toInt())

    @Test fun aMetaChunkRoundTrips() {
        val png = IconMeta.insert(fakePng(), meta)!!
        assertEquals(meta, IconMeta.parse(png))
        assertEquals(IconMeta(192, vector = true, themeColor = null), IconMeta.parse(IconMeta.insert(fakePng(), IconMeta(192, true, null))!!))
    }

    @Test fun theChunkSitsRightAfterIhdrWithAValidCrcAndTheRestIsKept() {
        val src = fakePng()
        val png = IconMeta.insert(src, meta)!!
        assertContentEquals(src.copyOfRange(0, 33), png.copyOfRange(0, 33))
        assertEquals("tEXt", String(png, 37, 4, Charsets.ISO_8859_1))
        val len = ByteBuffer.wrap(png, 33, 4).int
        val crc = CRC32().apply { update(png, 37, 4 + len) }.value.toInt()
        assertEquals(crc, ByteBuffer.wrap(png, 41 + len, 4).int)
        assertContentEquals(src.copyOfRange(33, src.size), png.copyOfRange(45 + len, png.size))
    }

    @Test fun aLegacyPngWithoutTheChunkHasNoMeta() = assertNull(IconMeta.parse(fakePng()))

    @Test fun aTruncatedOrHostileFileHasNoMetaAndInsertRefusesANonPng() {
        val png = IconMeta.insert(fakePng(), meta)!!
        for (cut in listOf(0, 8, 20, 36, 50)) assertNull(IconMeta.parse(png.copyOf(cut)), "cut $cut")
        assertNull(IconMeta.parse(png.copyOf().also { ByteBuffer.wrap(it, 33, 4).putInt(Int.MAX_VALUE) }))
        assertNull(IconMeta.parse(png.copyOf().also { it[45] = 'x'.code.toByte() }))
        assertNull(IconMeta.insert(byteArrayOf(1, 2, 3), meta))
    }

    @Test fun anotherKeywordOrVersionIsNotOurs() {
        val other = IconMeta.insert(fakePng(), meta)!!.also { it[41] = 'L'.code.toByte() }
        assertNull(IconMeta.parse(other))
    }
}
