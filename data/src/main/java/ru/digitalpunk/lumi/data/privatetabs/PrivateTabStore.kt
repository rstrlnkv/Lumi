package ru.digitalpunk.lumi.data.privatetabs

import android.content.Context
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.digitalpunk.lumi.data.tabs.BlobFiles

/** A stored session state over this size is dropped and the tab keeps its URL, as for normal tabs. */
internal const val MAX_PRIVATE_STATE_BYTES = 1024 * 1024

sealed interface PrivateLoad {
    data object Empty : PrivateLoad

    data class Loaded(val tabs: List<PrivateStoredTab>) : PrivateLoad

    /** The file could not be opened (wrong or lost key, damage); it and the key are gone. */
    data object Dropped : PrivateLoad

    /** The key could not be used this time; the file and the key stay for the next start. */
    data object Retry : PrivateLoad
}

/**
 * The private tabs, encrypted ([PrivateEnvelope]) in one file `tabs.bin` whose name carries no tab id. Any failure
 * to open the file drops it and the key, unless the key only failed for now ([PrivateLoad.Retry]); a failed save
 * returns false. Nothing throws. Every read and write runs one at a time on [io].
 */
class PrivateTabStore(
    dir: File,
    private val aead: Aead,
    private val io: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
    private val maxFileBytes: Int = MAX_FILE_BYTES,
) {
    private val files = BlobFiles(dir, "bin", maxFileBytes)
    private val mutex = Mutex()

    private suspend fun <T> serial(block: () -> T): T = mutex.withLock { withContext(io) { block() } }

    suspend fun load(): PrivateLoad = serial {
        files.sweep(setOf(KEY))
        val bytes = files.read(KEY)
        if (bytes == null) {
            files.delete(KEY)
            PrivateLoad.Empty
        } else {
            val load = try {
                PrivateEnvelope.open(aead, bytes)?.let(PrivateTabsCodec::decode)?.let(PrivateLoad::Loaded)
            } catch (_: Exception) {
                PrivateLoad.Retry
            }
            load ?: drop()
        }
    }

    /**
     * Saving no tabs deletes the file. A tab whose URL or title the file cannot hold is left out; over the file size,
     * the largest session states go first. False when nothing could be written.
     */
    suspend fun save(tabs: List<PrivateStoredTab>): Boolean = serial {
        if (tabs.isEmpty()) {
            files.delete(KEY)
            true
        } else {
            try {
                val kept = tabs.filter(PrivateTabsCodec::fits)
                    .map { t -> if ((t.sessionState?.toByteArray()?.size ?: 0) > MAX_PRIVATE_STATE_BYTES) t.copy(sessionState = null) else t }
                    .toMutableList()
                var plain = PrivateTabsCodec.encode(kept)
                while (plain.size + PrivateEnvelope.OVERHEAD > maxFileBytes) {
                    val largest = kept.indices.filter { kept[it].sessionState != null }.maxByOrNull { kept[it].sessionState!!.length } ?: break
                    kept[largest] = kept[largest].copy(sessionState = null)
                    plain = PrivateTabsCodec.encode(kept)
                }
                files.write(KEY, PrivateEnvelope.seal(aead, plain))
            } catch (_: Exception) {
                false
            }
        }
    }

    /** Deletes the file and the key; true when a stored file was there. */
    suspend fun wipe(): Boolean = serial {
        val had = files.delete(KEY)
        files.sweep(emptySet())
        aead.deleteKey()
        had
    }

    private fun drop(): PrivateLoad {
        files.delete(KEY)
        aead.deleteKey()
        return PrivateLoad.Dropped
    }

    companion object {
        private const val KEY = "tabs"
        private const val MAX_FILE_BYTES = 8 * 1024 * 1024

        fun create(context: Context) = PrivateTabStore(File(context.filesDir, "private-tabs"), KeystoreAead())
    }
}
