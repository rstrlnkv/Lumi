package ru.digitalpunk.lumi.data.library

import ru.digitalpunk.lumi.data.library.LibraryIds.FAVORITES
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FavoritesSeedTest {
    @Test fun aMarkedLibraryIsNeverSeededWhetherFavoritesIsEmptyOrNot() {
        assertEquals(SeedAction.Nothing, seedAction(marked = true, favoritesCount = 0))
        assertEquals(SeedAction.Nothing, seedAction(marked = true, favoritesCount = 3))
    }

    @Test fun anUnmarkedEmptyFavoritesIsSeeded() {
        assertEquals(SeedAction.Seed, seedAction(marked = false, favoritesCount = 0))
    }

    @Test fun anUnmarkedFavoritesWithItemsIsOnlyMarked() {
        assertEquals(SeedAction.MarkOnly, seedAction(marked = false, favoritesCount = 1))
        assertEquals(SeedAction.MarkOnly, seedAction(marked = false, favoritesCount = Int.MAX_VALUE))
    }

    @Test fun theSeedIsTheTenSitesInTheOwnersOrderWithUniqueIdsAndHttpsHosts() {
        assertEquals(
            listOf("Google", "YouTube", "Wikipedia", "Gmail", "Amazon", "Reddit", "X", "Instagram", "GitHub", "Netflix"),
            FAVORITES_SEED.map { it.title },
        )
        assertEquals(10, FAVORITES_SEED.map { it.id }.toSet().size)
        val uris = FAVORITES_SEED.map { URI(it.url) }
        assertTrue(uris.all { it.scheme == "https" && !it.host.isNullOrEmpty() })
        assertEquals(10, uris.map { it.host }.toSet().size)
    }

    @Test fun seedNodesAreBookmarksUnderFavoritesAtPositionsZeroToNine() {
        val nodes = seedNodes(now = 42L)
        assertEquals((0..9).toList(), nodes.map { it.position })
        assertTrue(nodes.all { it.parentId == FAVORITES && it.kind == BookmarkNodeEntity.BOOKMARK && it.createdAt == 42L })
        assertEquals(FAVORITES_SEED.map { it.id to it.url }, nodes.map { it.id to it.url })
    }
}
