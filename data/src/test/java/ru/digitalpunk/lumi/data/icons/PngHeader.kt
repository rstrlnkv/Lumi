package ru.digitalpunk.lumi.data.icons

import java.nio.ByteBuffer

/** The first 24 bytes of a PNG of [width] x [height]: signature, IHDR length and type, width and height. */
internal fun pngHeader(width: Int, height: Int = width): ByteArray = ByteBuffer.allocate(24)
    .put(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A, 0x1A, 0x0A))
    .putInt(13).put("IHDR".toByteArray()).putInt(width).putInt(height).array()

/** A PNG signature, a whole IHDR chunk of [width] px and an IDAT/IEND tail: enough for [IconMeta] and [SiteIconStore]. */
internal fun fakePng(width: Int = ICON_SIZE): ByteArray {
    fun chunk(type: String, data: ByteArray): ByteArray {
        val crc = java.util.zip.CRC32().apply { update(type.toByteArray()); update(data) }.value.toInt()
        return ByteBuffer.allocate(12 + data.size).putInt(data.size).put(type.toByteArray()).put(data).putInt(crc).array()
    }
    val ihdr = ByteBuffer.allocate(13).putInt(width).putInt(width).put(8).put(6).put(0).put(0).put(0).array()
    return pngHeader(width).copyOf(8) + chunk("IHDR", ihdr) + chunk("IDAT", byteArrayOf(1, 2, 3)) + chunk("IEND", ByteArray(0))
}
