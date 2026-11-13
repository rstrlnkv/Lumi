package ru.digitalpunk.lumi.data.library

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import ru.digitalpunk.lumi.data.icons.IconRetention
import ru.digitalpunk.lumi.data.library.LibraryIds.ROOT
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/** Inputs nobody fed the icon retention in [LibraryRepository]. */
class LibraryIconRetentionInputsTest {
    private class Recorder(var failure: RuntimeException? = null) : IconRetention {
        val calls = mutableListOf<String>()
        override fun forget(hosts: Set<String>) { calls += "forget ${hosts.sorted()}"; failure?.let { throw it } }
        override fun retainOnly(hosts: Set<String>) { calls += "retainOnly ${hosts.sorted()}"; failure?.let { throw it } }
    }

    private val dao = FakeLibraryDao()
    private val icons = Recorder()
    private var counter = 0
    private val repo = LibraryRepository(dao, Dispatchers.Unconfined, now = { 1L }, newId = { "id${++counter}" }, icons = icons)

    @Test fun spellingsOfOneHostShareOneIconSoDeletingOneSpellingForgetsNothing() = runBlocking<Unit> {
        val a = repo.addBookmark(ROOT, "A", "https://A.com:8443/x")!!
        repo.addBookmark(ROOT, "B", "http://a.com./y")
        repo.delete(a)
        assertEquals(emptyList(), icons.calls)
    }

    @Test fun editingAUrlToANonWebAddressOrAnIpLiteralForgetsTheOldHost() = runBlocking<Unit> {
        val a = repo.addBookmark(ROOT, "A", "https://a.com/")!!
        assertEquals(true, repo.updateBookmark(a, "A", "about:blank", ROOT))
        assertEquals(listOf("forget [a.com]"), icons.calls)
    }

    @Test fun editingTheHostAndMovingToAnotherFolderInOneCallForgetsTheOldHostOnce() = runBlocking<Unit> {
        val a = repo.addBookmark(ROOT, "A", "https://a.com/")!!
        val folder = repo.createFolder(ROOT, "F")!!
        assertEquals(true, repo.updateBookmark(a, "A", "https://b.com/", folder))
        assertEquals(listOf("forget [a.com]"), icons.calls)
    }

    @Test fun aRefusedEditForgetsNothing() = runBlocking<Unit> {
        val a = repo.addBookmark(ROOT, "A", "https://a.com/")!!
        val folder = repo.createFolder(ROOT, "F")!!
        assertFalse(repo.updateBookmark(folder, "F", "https://b.com/", ROOT))
        assertFalse(repo.updateBookmark("missing", "x", "https://b.com/", ROOT))
        assertFalse(repo.updateBookmark(a, "A", "https://b.com/" + "x".repeat(5000), ROOT))
        assertFalse(repo.updateBookmark(a, "A", "https://b.com/", "missing-folder"))
        assertEquals(emptyList(), icons.calls)
    }

    @Test fun deletingAnUnknownIdFavoritesOrAnEmptyFolderForgetsNothing() = runBlocking<Unit> {
        repo.addBookmark(ROOT, "A", "https://a.com/")
        val empty = repo.createFolder(ROOT, "empty")!!
        repo.delete("missing")
        repo.delete(LibraryIds.FAVORITES)
        repo.delete(empty)
        assertEquals(emptyList(), icons.calls)
    }

    @Test fun clearingAllHistoryOfAnEmptyLibraryKeepsNoIcon() = runBlocking<Unit> {
        repo.clearHistory(ClearRange.AllTime, ZoneId.of("UTC"))
        assertEquals(listOf("retainOnly []"), icons.calls)
    }

    /** The rows are gone before the icon cleanup runs; a cleanup that throws must not turn a done delete into "failed, no Undo". */
    @Test fun aFailingIconCleanupStillReturnsTheRemovedBookmarkForUndo() = runBlocking<Unit> {
        val a = repo.addBookmark(ROOT, "A", "https://a.com/")!!
        icons.failure = IllegalStateException("disk")
        val removed = repo.delete(a)
        assertEquals(emptyList(), dao.nodes().filter { it.id == a })
        assertNotNull(removed, "the bookmark is deleted, so Undo must get it back")
    }

    @Test fun aFailingIconCleanupStillReportsAnEditThatWasSaved() = runBlocking<Unit> {
        val a = repo.addBookmark(ROOT, "A", "https://a.com/")!!
        icons.failure = IllegalStateException("disk")
        val ok = repo.updateBookmark(a, "A", "https://b.com/", ROOT)
        assertEquals("https://b.com/", dao.nodes().first { it.id == a }.url)
        assertEquals(true, ok, "the edit is saved, so it must not be reported as failed")
    }
}
