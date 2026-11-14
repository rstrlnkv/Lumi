package ru.digitalpunk.lumi.data.library

import ru.digitalpunk.lumi.data.library.LibraryIds.FAVORITES

/** What the first start does with Favorites: fill it with [FAVORITES_SEED], only set the marker, or nothing. */
enum class SeedAction { Seed, MarkOnly, Nothing }

/**
 * Seeds only an unmarked, empty Favorites; an unmarked Favorites with items is only marked, so an upgrade never gets the
 * set later and a deleted seed bookmark never comes back. [favoritesCount]: the direct children of Favorites.
 */
fun seedAction(marked: Boolean, favoritesCount: Int): SeedAction = when {
    marked -> SeedAction.Nothing
    favoritesCount > 0 -> SeedAction.MarkOnly
    else -> SeedAction.Seed
}

/** The persisted "Favorites was seeded" marker. */
interface SeedMarker {
    suspend fun isSet(): Boolean
    suspend fun set()
}

internal class SeedSite(val id: String, val title: String, val url: String)

/** The fixed ids make a repeated seed insert nothing (INSERT OR IGNORE). */
internal val FAVORITES_SEED = listOf(
    SeedSite("seed-google", "Google", "https://www.google.com/"),
    SeedSite("seed-youtube", "YouTube", "https://m.youtube.com/"),
    SeedSite("seed-wikipedia", "Wikipedia", "https://www.wikipedia.org/"),
    SeedSite("seed-gmail", "Gmail", "https://mail.google.com/"),
    SeedSite("seed-amazon", "Amazon", "https://www.amazon.com/"),
    SeedSite("seed-reddit", "Reddit", "https://www.reddit.com/"),
    SeedSite("seed-x", "X", "https://x.com/"),
    SeedSite("seed-instagram", "Instagram", "https://www.instagram.com/"),
    SeedSite("seed-github", "GitHub", "https://github.com/"),
    SeedSite("seed-netflix", "Netflix", "https://www.netflix.com/"),
)

/** Bookmarks under Favorites, at positions 0..9 in list order. */
internal fun seedNodes(now: Long): List<BookmarkNodeEntity> = FAVORITES_SEED.mapIndexed { i, site ->
    BookmarkNodeEntity(site.id, FAVORITES, BookmarkNodeEntity.BOOKMARK, site.title, site.url, i, now)
}
