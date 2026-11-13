package ru.digitalpunk.lumi.data.library

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import ru.digitalpunk.lumi.data.icons.IconRetention
import ru.digitalpunk.lumi.data.library.LibraryIds.ROOT
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class LibraryIconRetentionTest {
    private class Recorder : IconRetention {
        val calls = mutableListOf<String>()
        override fun forget(hosts: Set<String>) { calls += "forget ${hosts.sorted()}" }
        override fun retainOnly(hosts: Set<String>) { calls += "retainOnly ${hosts.sorted()}" }
    }

    private val dao = FakeLibraryDao()
    private val icons = Recorder()
    private var counter = 0
    private val repo = LibraryRepository(dao, Dispatchers.Unconfined, now = { 1L }, newId = { "id${++counter}" }, icons = icons)

    @Test fun deletingTheLastBookmarkOfAHostForgetsItsIcon() = runBlocking<Unit> {
        val a = repo.addBookmark(ROOT, "A", "https://a.com/")!!
        repo.addBookmark(ROOT, "B", "https://b.com/")
        repo.delete(a)
        assertEquals(listOf("forget [a.com]"), icons.calls)
    }

    @Test fun deletingOneOfTwoBookmarksOnAHostForgetsNothing() = runBlocking<Unit> {
        val a = repo.addBookmark(ROOT, "A", "https://a.com/1")!!
        repo.addBookmark(ROOT, "A2", "https://a.com/2")
        repo.delete(a)
        assertEquals(emptyList(), icons.calls)
    }

    @Test fun deletingAFolderForgetsOnlyHostsWithNoBookmarkLeft() = runBlocking<Unit> {
        repo.addBookmark(ROOT, "out", "https://a.com/out")
        val folder = repo.createFolder(ROOT, "F")!!
        repo.addBookmark(folder, "in", "https://a.com/in")
        repo.addBookmark(folder, "b1", "https://b.com/1")
        repo.addBookmark(folder, "c", "https://c.com/")
        repo.delete(folder)
        assertEquals(listOf("forget [b.com, c.com]"), icons.calls)
    }

    @Test fun editingAUrlToAnotherHostForgetsTheOldHostButAPathEditDoesNot() = runBlocking<Unit> {
        val a = repo.addBookmark(ROOT, "A", "https://a.com/x")!!
        repo.updateBookmark(a, "A", "https://a.com/y", ROOT)
        assertEquals(emptyList(), icons.calls)
        repo.updateBookmark(a, "A", "https://c.com/y", ROOT)
        assertEquals(listOf("forget [a.com]"), icons.calls)
    }

    @Test fun moveAndRestoreTouchNoIcon() = runBlocking<Unit> {
        val a = repo.addBookmark(ROOT, "A", "https://a.com/")!!
        val folder = repo.createFolder(ROOT, "F")!!
        repo.moveNode(a, folder)
        icons.calls.clear()
        val b = repo.addBookmark(ROOT, "B", "https://b.com/")!!
        val removed = repo.delete(b)!!
        icons.calls.clear()
        repo.restore(removed)
        assertEquals(emptyList(), icons.calls)
    }

    @Test fun clearingAllHistoryKeepsOnlyBookmarkIconsAndShorterRangesTouchNoIcon() = runBlocking<Unit> {
        repo.addBookmark(ROOT, "A", "https://a.com/")
        repo.addBookmark(ROOT, "B", "http://www.b.com/x")
        repo.clearHistory(ClearRange.LastHour, ZoneId.of("UTC"))
        repo.clearHistory(ClearRange.Today, ZoneId.of("UTC"))
        assertEquals(emptyList(), icons.calls)
        repo.clearHistory(ClearRange.AllTime, ZoneId.of("UTC"))
        assertEquals(listOf("retainOnly [a.com, www.b.com]"), icons.calls)
    }
}
