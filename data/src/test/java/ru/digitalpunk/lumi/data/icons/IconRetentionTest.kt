package ru.digitalpunk.lumi.data.icons

import kotlin.test.Test
import kotlin.test.assertEquals

class IconRetentionTest {
    @Test fun deletingTheOnlyBookmarkOfAHostDropsItsIcon() =
        assertEquals(setOf("a.com"), orphanedIconHosts(listOf("https://a.com/x", "https://b.com/"), listOf("https://b.com/")))

    @Test fun deletingOneOfTwoBookmarksOnAHostKeepsItsIcon() =
        assertEquals(emptySet(), orphanedIconHosts(listOf("https://a.com/1", "https://a.com/2"), listOf("https://a.com/2")))

    @Test fun aFolderDeleteDropsOnlyHostsWithNoBookmarkLeftOutside() {
        val outside = listOf("https://a.com/out")
        val subtree = listOf("https://a.com/in", "https://b.com/1", "https://b.com/2", null)
        assertEquals(setOf("b.com"), orphanedIconHosts(outside + subtree, outside))
    }

    @Test fun aMoveKeepsTheSameUrlsAndDropsNothing() {
        val urls = listOf("https://a.com/", "https://b.com/")
        assertEquals(emptySet(), orphanedIconHosts(urls, urls.reversed()))
    }

    @Test fun editingAUrlToAnotherHostDropsTheOldHostButAPathEditDoesNot() {
        assertEquals(setOf("a.com"), orphanedIconHosts(listOf("https://a.com/x"), listOf("https://c.com/x")))
        assertEquals(emptySet(), orphanedIconHosts(listOf("https://a.com/x"), listOf("https://a.com/y?q=1")))
    }

    @Test fun httpAndHttpsShareAHostButWwwIsAnotherHost() {
        assertEquals(emptySet(), orphanedIconHosts(listOf("https://a.com/", "http://a.com/"), listOf("http://a.com/")))
        assertEquals(setOf("a.com"), orphanedIconHosts(listOf("https://a.com/", "https://www.a.com/"), listOf("https://www.a.com/")))
    }

    @Test fun foldersIpLiteralsAndOtherSchemesHaveNoIconHost() =
        assertEquals(setOf("a.com"), bookmarkIconHosts(listOf(null, "https://10.0.0.1/", "about:blank", "file:///x", "https://A.com./")))
}
