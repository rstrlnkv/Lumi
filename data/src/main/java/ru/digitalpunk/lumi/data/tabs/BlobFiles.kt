package ru.digitalpunk.lumi.data.tabs

import java.io.File

/**
 * Small files, one per key, in [dir]. A write goes to `.tmp` first and is renamed, so a crash never leaves half a file.
 * A key must match `[A-Za-z0-9-]{1,64}` (a UUID does); any other key never touches the disk.
 */
class BlobFiles(private val dir: File, private val extension: String, val maxBytes: Int) {
    private fun isSafe(id: String) = SAFE_KEY.matches(id)

    private fun file(id: String) = File(dir, "$id.$extension")

    /** False when [bytes] is over the limit (the older file is removed too), the key is unsafe, or the disk write failed (the older file stays). */
    fun write(id: String, bytes: ByteArray): Boolean {
        if (!isSafe(id)) return false
        if (bytes.size > maxBytes) {
            delete(id)
            return false
        }
        return try {
            dir.mkdirs()
            val tmp = File(dir, "$id.$extension.tmp")
            tmp.writeBytes(bytes)
            tmp.renameTo(file(id)).also { if (!it) tmp.delete() }
        } catch (_: java.io.IOException) {
            false
        }
    }

    /** Null when the key is unsafe, the file is missing, or it is larger than [maxBytes]. */
    fun read(id: String): ByteArray? {
        if (!isSafe(id)) return null
        return file(id).takeIf { it.isFile && it.length() <= maxBytes }?.readBytes()
    }

    fun delete(id: String) {
        if (isSafe(id)) file(id).delete()
    }

    /** Deletes every `.<extension>` file whose key is not in [keep], and every `.<extension>.tmp` file. Other files stay. */
    fun sweep(keep: Set<String>) {
        dir.listFiles()?.forEach { f ->
            when {
                f.name.endsWith(".$extension.tmp") -> f.delete()
                f.name.endsWith(".$extension") && f.name.removeSuffix(".$extension") !in keep -> f.delete()
            }
        }
    }

    private companion object {
        val SAFE_KEY = Regex("[A-Za-z0-9-]{1,64}")
    }
}
