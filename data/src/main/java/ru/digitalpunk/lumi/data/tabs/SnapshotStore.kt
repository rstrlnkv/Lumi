package ru.digitalpunk.lumi.data.tabs

import android.content.Context
import java.io.File

/** Page snapshots, one compressed image per tab, in the cache folder (the system may clear it; a missing one is fine). */
class SnapshotStore(private val files: BlobFiles) {
    fun save(id: String, bytes: ByteArray): Boolean = files.write(id, bytes)

    fun load(id: String): ByteArray? = files.read(id)

    fun delete(id: String) = files.delete(id)

    /** Removes the snapshots of every tab not in [keep]. */
    fun sweep(keep: Set<String>) = files.sweep(keep)

    companion object {
        private const val MAX_SNAPSHOT_BYTES = 512 * 1024

        fun create(context: Context) =
            SnapshotStore(BlobFiles(File(context.cacheDir, "tab-snapshots"), "webp", MAX_SNAPSHOT_BYTES))
    }
}
