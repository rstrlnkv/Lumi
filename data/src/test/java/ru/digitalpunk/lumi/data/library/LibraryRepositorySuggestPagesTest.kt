package ru.digitalpunk.lumi.data.library

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class LibraryRepositorySuggestPagesTest {
    private val dao = FakeLibraryDao()
    private val repo = LibraryRepository(dao, Dispatchers.Unconfined)

    private fun bookmark(id: String, url: String, title: String) =
        BookmarkNodeEntity(id, LibraryIds.FAVORITES, BookmarkNodeEntity.BOOKMARK, title, url, 0, 5)

    @Test fun bookmarksAndHistoryComeOncePerUrlAndABookmarkTakesTheVisitCounts() = runBlocking<Unit> {
        dao.nodeRows.value = listOf(
            BookmarkNodeEntity(LibraryIds.FAVORITES, LibraryIds.ROOT, BookmarkNodeEntity.FOLDER, "", null, 0, 0),
            bookmark("b1", "https://github.com/", "GitHub"),
        )
        dao.visits = listOf(
            HistoryEntity(1, "https://github.com/", "old", 10),
            HistoryEntity(2, "https://github.com/", "GitHub home", 20),
            HistoryEntity(3, "https://kotlinlang.org/", "", 30),
            HistoryEntity(4, "https://kotlinlang.org/", "Kotlin", 40),
            HistoryEntity(5, "https://x.com/" + "a".repeat(3000), "long", 50),
        )
        assertEquals(
            listOf(
                SuggestPage("https://github.com/", "GitHub", 2, 20, bookmark = true),
                SuggestPage("https://kotlinlang.org/", "Kotlin", 2, 40, bookmark = false),
            ),
            repo.suggestionPages().sortedBy { it.url },
        )
    }

    @Test fun aFailingReadGivesAnEmptyList() = runBlocking<Unit> {
        val broken = object : LibraryDao by dao {
            override suspend fun historyPages(limit: Int): List<HistoryHit> = error("disk")
        }
        assertEquals(emptyList(), LibraryRepository(broken, Dispatchers.Unconfined).suggestionPages())
    }
}
