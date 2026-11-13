package ru.digitalpunk.lumi.data.downloads

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * Files in the system Downloads folder: `MediaStore.Downloads` on API 29+ (no permission), the public Downloads directory on
 * API 26-28 (needs `WRITE_EXTERNAL_STORAGE`). Blocking: call it on `Dispatchers.IO`. Names must come from [sanitizeFileName].
 */
class DownloadStorage(context: Context) {
    private val context = context.applicationContext
    private val resolver get() = context.contentResolver

    /** A file being written; [uri] is what the entry stores. */
    class Target(val uri: String, val output: OutputStream)

    fun create(name: String, mime: String): Target {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw IOException("MediaStore refused the file")
            val output = try {
                resolver.openOutputStream(uri) ?: throw IOException("No output stream")
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
            return Target(uri.toString(), output)
        }
        @Suppress("DEPRECATION")
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        dir.mkdirs()
        // createNewFile claims the name atomically, so another app cannot slip a file in between the check and the write.
        val file = File(dir, uniqueName(name) { !File(dir, it).createNewFile() })
        if (file.canonicalFile.parentFile != dir.canonicalFile) {
            file.delete()
            throw IOException("The name leaves the Downloads folder")
        }
        return Target(file.absolutePath, FileOutputStream(file))
    }

    /** Makes a written file visible to other apps and returns its final name (MediaStore may have added " (1)"). */
    fun publish(uri: String, name: String): String {
        if (!isContent(uri)) {
            MediaScannerConnection.scanFile(context, arrayOf(uri), null, null)
            return File(uri).name
        }
        val content = Uri.parse(uri)
        resolver.update(content, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        return resolver.query(content, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: name
    }

    /** Deletes the file; a file that is already gone or no longer ours is ignored. */
    fun delete(uri: String?) {
        uri ?: return
        runCatching { if (isContent(uri)) resolver.delete(Uri.parse(uri), null, null) else File(uri).delete() }
    }

    fun exists(uri: String?): Boolean {
        uri ?: return false
        if (!isContent(uri)) return File(uri).exists()
        return runCatching {
            resolver.query(Uri.parse(uri), arrayOf(MediaStore.MediaColumns._ID), null, null, null)?.use { it.count > 0 } ?: false
        }.getOrDefault(false)
    }

    /** Free bytes on the shared storage that holds Downloads. */
    @Suppress("DEPRECATION")
    fun freeBytes(): Long = StatFs(Environment.getExternalStorageDirectory().path).availableBytes

    private fun isContent(uri: String) = uri.startsWith("content:")
}
