package ru.digitalpunk.lumi.library

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import ru.digitalpunk.lumi.data.library.BookmarkNodeEntity
import ru.digitalpunk.lumi.data.library.HistoryEntity
import ru.digitalpunk.lumi.data.library.LibraryDao
import ru.digitalpunk.lumi.data.library.LibraryRepository
import kotlin.test.Test
import kotlin.test.assertEquals

/** Just enough of the DAO for the recorder: visits and the seeded Favorites. */
private class VisitsDao : LibraryDao {
    val nodes = ArrayList<BookmarkNodeEntity>()
    var visits = emptyList<HistoryEntity>()
    var prunes = 0
    private var next = 1L
    override fun observeNodes(): Flow<List<BookmarkNodeEntity>> = MutableStateFlow(nodes.toList())
    override suspend fun nodes() = nodes.toList()
    override suspend fun insertIfAbsent(node: BookmarkNodeEntity): Long { if (nodes.any { it.id == node.id }) return -1; nodes += node; return 1 }
    override suspend fun insertAll(nodes: List<BookmarkNodeEntity>) = error("unused")
    override suspend fun update(node: BookmarkNodeEntity) = error("unused")
    override suspend fun deleteNodes(ids: List<String>) = error("unused")
    override suspend fun nextPosition(parent: String) = 0
    override suspend fun insertVisit(entry: HistoryEntity): Long { visits = visits + entry.copy(id = next++); return next - 1 }
    override suspend fun titleLatest(url: String, title: String) {
        val latest = visits.filter { it.url == url }.maxWithOrNull(compareBy({ it.visitedAt }, { it.id })) ?: return
        visits = visits.map { if (it.id == latest.id) it.copy(title = title) else it }
    }
    override fun observeHistory(limit: Int): Flow<List<HistoryEntity>> = MutableStateFlow(visits)
    override suspend fun insertVisits(entries: List<HistoryEntity>) = error("unused")
    override suspend fun deleteVisits(ids: List<Long>) = error("unused")
    override suspend fun deleteVisitsSince(from: Long) = error("unused")
    override suspend fun deleteAllVisits() = error("unused")
    override suspend fun pruneVisits(cutoff: Long) { prunes++; visits = visits.filterNot { it.visitedAt < cutoff } }
}

class HistoryRecorderTest {
    private val dao = VisitsDao()
    private var clock = 1_000_000_000_000L
    private val job = Job()
    private val recorder = HistoryRecorder(
        LibraryRepository(dao, Dispatchers.Unconfined, now = { clock }),
        CoroutineScope(Dispatchers.Unconfined + job),
        clock = { clock },
    )

    @Test fun visitsReportedBeforeStartAreKeptAndWrittenInOrderAfterIt() {
        recorder.visited("https://a.com/")
        clock += 1
        recorder.titled("https://a.com/", "A")
        clock += 1
        recorder.visited("https://b.com/")
        assertEquals(0, dao.visits.size)
        recorder.start()
        assertEquals(listOf("https://a.com/" to "A", "https://b.com/" to ""), dao.visits.map { it.url to it.title })
        assertEquals(listOf(1_000_000_000_000L, 1_000_000_000_002L), dao.visits.map { it.visitedAt })
    }

    @Test fun startingTwiceSeedsOnceAndWritesEachVisitOnce() {
        recorder.start()
        recorder.start()
        recorder.visited("https://a.com/")
        assertEquals(1, dao.visits.size)
        assertEquals(1, dao.nodes.size)
        assertEquals(1, dao.prunes)
    }

    @Test fun addressesThatAreNotWebPagesAreDropped() {
        recorder.start()
        listOf("about:blank", "javascript:alert(1)", "data:text/html,x", "file:///sdcard/a.html", "", "https://" + "a".repeat(2050)).forEach {
            recorder.visited(it)
            recorder.titled(it, "T")
        }
        assertEquals(0, dao.visits.size)
    }

    @Test fun aTitleForAPageNeverVisitedChangesNothing() {
        recorder.start()
        recorder.titled("https://nowhere.com/", "T")
        assertEquals(0, dao.visits.size)
    }

    @Test fun aBurstOfThousandsOfVisitsIsAllWritten() {
        recorder.start()
        repeat(3000) { recorder.visited("https://a.com/$it") }
        assertEquals(3000, dao.visits.size)
    }

    @Test fun eventsAfterTheScopeIsCancelledDoNotThrow() {
        recorder.start()
        job.cancel()
        recorder.visited("https://a.com/")
        recorder.titled("https://a.com/", "A")
        assertEquals(0, dao.visits.size)
    }
}
