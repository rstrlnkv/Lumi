package ru.digitalpunk.lumi.data.icons

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest

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

    /** The [IconMeta] of the stored icon; null when none is stored or it was stored before Icons3 (it is fetched again once). Reads 512 bytes. */
    fun meta(host: String): IconMeta? = try {
        png(host).takeIf { it.isFile }?.let { f -> RandomAccessFile(f, "r").use { ByteArray(minOf(it.length(), 512L).toInt()).also(it::readFully) } }
    } catch (_: IOException) { null }?.let(IconMeta::parse)

    /** Marks the stored icon fresh without changing it: a fetch found nothing better. */
    fun touch(host: String, nowMillis: Long) {
        png(host).takeIf { it.isFile }?.setLastModified(nowMillis)
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
