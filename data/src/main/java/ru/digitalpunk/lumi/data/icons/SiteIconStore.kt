package ru.digitalpunk.lumi.data.icons

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.security.MessageDigest

private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

/** One PNG per host in [dir], named by the SHA-1 of the host: hex only, so no host can name a path. */
class SiteIconStore(private val dir: File) {
    init {
        dir.mkdirs()
        dir.listFiles { f -> f.name.endsWith(".tmp") }?.forEach { it.delete() }
    }

    private fun name(host: String) = MessageDigest.getInstance("SHA-1").digest(host.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun png(host: String) = File(dir, "${name(host)}.png")

    /** Null when no icon is stored. */
    fun lastModified(host: String): Long? = png(host).takeIf { it.isFile }?.lastModified()

    /** True when the stored icon is a PNG of [ICON_SIZE] px: an older, smaller one is fetched again once. Reads 24 bytes. */
    fun isCurrent(host: String): Boolean {
        val head = try {
            png(host).takeIf { it.isFile }?.let { f -> RandomAccessFile(f, "r").use { if (it.length() < 24) null else ByteArray(24).also(it::readFully) } }
        } catch (_: IOException) { null } ?: return false
        if (!head.copyOfRange(0, 8).contentEquals(PNG_SIGNATURE) || String(head, 12, 4, Charsets.ISO_8859_1) != "IHDR") return false
        return ByteBuffer.wrap(head, 16, 4).int == ICON_SIZE
    }

    fun read(host: String): ByteArray? = try {
        png(host).takeIf { it.isFile }?.readBytes()
    } catch (_: IOException) {
        null
    }

    /** Writes a temporary file and renames it, so a PNG appears only whole. False when it could not be written. */
    fun write(host: String, png: ByteArray): Boolean {
        val tmp = File(dir, "${name(host)}.tmp")
        return try {
            tmp.writeBytes(png)
            tmp.renameTo(png(host))
        } catch (_: IOException) {
            false
        }
    }

    /** Deletes the icon of [host] and a leftover temporary file; false when no icon was stored. */
    fun delete(host: String): Boolean {
        File(dir, "${name(host)}.tmp").delete()
        return png(host).delete()
    }

    /** Deletes every stored icon but those of [hosts]; the number deleted. Other files stay. */
    fun retainOnly(hosts: Set<String>): Int {
        val keep = hosts.mapTo(HashSet()) { "${name(it)}.png" }
        val files = dir.listFiles { f -> f.name.endsWith(".png") && f.name !in keep } ?: return 0
        return files.count { it.delete() }
    }
}
