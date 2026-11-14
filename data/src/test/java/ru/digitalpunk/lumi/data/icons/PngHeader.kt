package ru.digitalpunk.lumi.data.icons

import java.nio.ByteBuffer

/** The first 24 bytes of a PNG of [width] x [height]: signature, IHDR length and type, width and height. */
internal fun pngHeader(width: Int, height: Int = width): ByteArray = ByteBuffer.allocate(24)
    .put(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A, 0x1A, 0x0A))
    .putInt(13).put("IHDR".toByteArray()).putInt(width).putInt(height).array()
