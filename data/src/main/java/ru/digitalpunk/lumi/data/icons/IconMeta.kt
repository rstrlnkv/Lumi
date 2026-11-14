package ru.digitalpunk.lumi.data.icons

import java.nio.ByteBuffer
import java.util.zip.CRC32

private val SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
private const val KEYWORD = "lumi"
private const val VERSION = 4
private const val MAX_CHUNKS = 32
private val TEXT = Regex("v=$VERSION;px=(\\d{1,5});vec=([01]);tc=([0-9a-f]{6})?")

/**
 * What a stored icon was made from, kept in a `tEXt` chunk right after IHDR of the stored PNG (one file, so the write stays
 * atomic); BitmapFactory skips the chunk. A file without it, or with an older version, is fetched again once (v4: icons asked
 * for as Safari, and full-bleed icons with clear corners fill the tile).
 */
data class IconMeta(val srcPx: Int, val vector: Boolean, val themeColor: Int?) {
    val quality get() = IconQuality(srcPx, vector)

    companion object {
        /** The meta of [png], read from at most [MAX_CHUNKS] chunks before the first IDAT; null when absent or malformed. */
        fun parse(png: ByteArray): IconMeta? {
            if (png.size < 8 || !png.copyOf(8).contentEquals(SIGNATURE)) return null
            var at = 8
            repeat(MAX_CHUNKS) {
                if (at + 8 > png.size) return null
                val len = ByteBuffer.wrap(png, at, 4).int
                val type = String(png, at + 4, 4, Charsets.ISO_8859_1)
                if (len < 0 || len > png.size - at - 12 || type == "IDAT") return null
                if (type == "tEXt" && crc(png, at + 4, len + 4) == ByteBuffer.wrap(png, at + 8 + len, 4).int) {
                    val text = String(png, at + 8, len, Charsets.ISO_8859_1)
                    if (text.startsWith("$KEYWORD\u0000")) {
                        val m = TEXT.matchEntire(text.substring(KEYWORD.length + 1)) ?: return null
                        return IconMeta(m.groupValues[1].toInt(), m.groupValues[2] == "1", m.groupValues[3].takeIf { it.isNotEmpty() }?.let { it.toInt(16) or (0xFF shl 24) })
                    }
                }
                at += 12 + len
            }
            return null
        }

        /** [png] with [meta] inserted right after its IHDR chunk; null when [png] does not start with a PNG signature and IHDR. */
        fun insert(png: ByteArray, meta: IconMeta): ByteArray? {
            if (png.size < 33 || !png.copyOf(8).contentEquals(SIGNATURE) || String(png, 12, 4, Charsets.ISO_8859_1) != "IHDR" || ByteBuffer.wrap(png, 8, 4).int != 13) return null
            val tc = meta.themeColor?.let { "%06x".format(it and 0xFFFFFF) }.orEmpty()
            val data = "$KEYWORD\u0000v=$VERSION;px=${meta.srcPx};vec=${if (meta.vector) 1 else 0};tc=$tc".toByteArray(Charsets.ISO_8859_1)
            val chunk = ByteBuffer.allocate(12 + data.size).putInt(data.size).put("tEXt".toByteArray()).put(data)
            chunk.putInt(crc(chunk.array(), 4, 4 + data.size))
            return png.copyOf(33) + chunk.array() + png.copyOfRange(33, png.size)
        }

        private fun crc(bytes: ByteArray, from: Int, len: Int): Int = CRC32().apply { update(bytes, from, len) }.value.toInt()
    }
}
