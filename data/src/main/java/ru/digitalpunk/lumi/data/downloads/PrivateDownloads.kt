package ru.digitalpunk.lumi.data.downloads

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

/** Private downloads, in memory only: never in Room, a file or the settings. Ids are negative, so they never meet a Room id. */
class PrivateDownloads {
    private val mutable = MutableStateFlow<List<DownloadItem>>(emptyList())
    val items: StateFlow<List<DownloadItem>> = mutable
    private val nextId = AtomicLong(-1)

    fun add(item: DownloadItem): DownloadItem = item.copy(id = nextId.getAndDecrement(), isPrivate = true).also { added -> mutable.update { it + added } }

    fun restore(item: DownloadItem) = mutable.update { list -> if (list.any { it.id == item.id }) list else list + item }

    fun update(item: DownloadItem) = mutable.update { list -> list.map { if (it.id == item.id) item else it } }

    fun remove(id: Long) = mutable.update { list -> list.filter { it.id != id } }

    /** Called when the last private tab closes and after each private download ends while none is open. */
    fun dropFinished() = mutable.update(::afterClear)

    fun clearFinished(): Int {
        var removed = 0
        mutable.update { list -> afterClear(list).also { removed = list.size - it.size } }
        return removed
    }
}
