package ru.digitalpunk.lumi.data.downloads

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FakeDownloadDao(var failObserve: Boolean = false) : DownloadDao {
    val rows = MutableStateFlow<List<DownloadEntity>>(emptyList())
    private var nextId = 1L

    override suspend fun insert(entity: DownloadEntity): Long {
        val id = if (entity.id == 0L) nextId++ else entity.id
        rows.value = rows.value + entity.copy(id = id)
        return id
    }
    override suspend fun update(entity: DownloadEntity) { rows.value = rows.value.map { if (it.id == entity.id) entity else it } }
    override fun observe(): Flow<List<DownloadEntity>> =
        if (failObserve) flow { throw IllegalStateException("Row too big for CursorWindow") } else rows.map { r -> r.sortedByDescending { it.startedAt } }
    override suspend fun get(id: Long) = rows.value.firstOrNull { it.id == id }
    override suspend fun delete(id: Long) { rows.value = rows.value.filter { it.id != id } }
    override suspend fun active() = rows.value.filter { it.state == "Queued" || it.state == "Running" }
    override suspend fun deleteFinished(): Int {
        val before = rows.value.size
        rows.value = rows.value.filter { it.state == "Queued" || it.state == "Running" }
        return before - rows.value.size
    }
    override suspend fun failInterrupted() { rows.value = rows.value.map { if (it.state == "Queued" || it.state == "Running") it.copy(state = "Failed") else it } }
}

private fun item(state: DownloadState, private: Boolean = false, url: String = "https://example.org/a.pdf", name: String = "a.pdf") =
    DownloadItem(0, url, name, "application/pdf", null, "example.org", 10, 0, state, 1, private)

class DownloadRepositoryTest {
    @Test fun aPrivateItemIsNeverWrittenToTheDatabase() = runBlocking<Unit> {
        val dao = FakeDownloadDao()
        assertFailsWith<IllegalArgumentException> { DownloadRepository(dao).insert(item(DownloadState.Running, private = true)) }
        assertTrue(dao.rows.value.isEmpty())
    }

    @Test fun aFailingObservedFlowEndsInsteadOfCrashing() = runBlocking {
        assertEquals(emptyList(), DownloadRepository(FakeDownloadDao(failObserve = true)).observe().toList())
    }

    @Test fun interruptedEntriesBecomeFailedAndAreReturnedForCleanup() = runBlocking {
        val dao = FakeDownloadDao()
        val repo = DownloadRepository(dao)
        repo.insert(item(DownloadState.Running))
        repo.insert(item(DownloadState.Done))
        assertEquals(1, repo.recoverInterrupted().size)
        assertEquals(listOf("Failed", "Done"), dao.rows.value.map { it.state })
    }

    @Test fun stringsAreCappedOnWriteAndAnOverCapUrlIsStoredEmpty() = runBlocking {
        val dao = FakeDownloadDao()
        DownloadRepository(dao).insert(item(DownloadState.Running, url = "https://x.org/" + "a".repeat(3000), name = "é".repeat(400)))
        val row = dao.rows.value.single()
        assertEquals("", row.url)
        assertTrue(row.fileName.toByteArray().size <= 255)
    }

    @Test fun anUnknownStoredStateReadsAsFailed() {
        val entity = DownloadEntity(1, "u", "n", "m", null, "s", 1, 1, "Exploded", 1)
        assertEquals(DownloadState.Failed, entity.toItem().state)
    }
}

class PrivateDownloadsTest {
    @Test fun idsAreNegativeAndDistinct() {
        val store = PrivateDownloads()
        val a = store.add(item(DownloadState.Running, private = true))
        val b = store.add(item(DownloadState.Running, private = true))
        assertTrue(a.id < 0 && b.id < 0 && a.id != b.id)
        assertTrue(isPrivateId(a.id))
    }

    @Test fun dropFinishedKeepsRunningEntries() = runBlocking {
        val store = PrivateDownloads()
        val running = store.add(item(DownloadState.Running, private = true))
        store.add(item(DownloadState.Done, private = true))
        store.add(item(DownloadState.Failed, private = true))
        store.dropFinished()
        assertEquals(listOf(running.id), store.items.first().map { it.id })
        store.update(running.copy(state = DownloadState.Done))
        store.dropFinished()
        assertTrue(store.items.first().isEmpty())
    }

    @Test fun clearFinishedCountsWhatItRemoved() {
        val store = PrivateDownloads()
        store.add(item(DownloadState.Running, private = true))
        store.add(item(DownloadState.Cancelled, private = true))
        assertEquals(1, store.clearFinished())
    }
}
