package ru.digitalpunk.lumi.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class BackgroundTabEdgeTest {
    private val a = TabId("a")
    private val b = TabId("b")
    private val c = TabId("c")
    private val n1 = TabId("n1")
    private val n2 = TabId("n2")
    private val n3 = TabId("n3")
    private val p1 = TabId("p1")
    private val p2 = TabId("p2")

    private fun store() = TabsStore().apply { add(a); add(b); add(c); select(a) }
    private val TabsStore.selected get() = tabs.value.selectedId

    @Test fun backgroundTabJoinsTheSelectedTabsGroupEvenWhenTheGridShowsAnotherGroup() {
        val store = store()
        val g = store.createGroup("Work")!!
        store.moveTab(a, g)
        store.select(a)
        store.setCurrentGroup(null)
        store.add(n1, select = false, url = "https://x.example/")
        assertEquals(g, store.tabs.value.tabs.first { it.id == n1 }.groupId)
        assertEquals(a, store.selected)
        assertEquals(null, store.tabs.value.currentGroup)
    }

    @Test fun manyBackgroundTabsKeepReadingOrder() {
        val store = store()
        val ids = List(30) { TabId("n$it") }
        ids.forEach { store.add(it, select = false, url = "https://x.example/$it") }
        assertEquals(listOf(a) + ids + listOf(b, c), store.idsIn(null))
        assertEquals(a, store.selected)
    }

    @Test fun twoPrivateBackgroundTabsKeepReadingOrder() {
        val store = store()
        store.add(p1, private = true, select = false, url = "https://x.example/1")
        store.add(p2, private = true, select = false, url = "https://x.example/2")
        assertEquals(listOf(p1, p2), store.privateIds())
        assertEquals(a, store.selected)
    }

    @Test fun aPrivateLinkBetweenTwoNormalLinksDoesNotReorderTheNormalTabs() {
        val store = store()
        store.add(n1, select = false, url = "https://x.example/1")
        store.add(p1, private = true, select = false, url = "https://x.example/p")
        store.add(n2, select = false, url = "https://x.example/2")
        assertEquals(listOf(a, n1, n2, b, c), store.idsIn(null))
    }

    @Test fun aNormalLinkBetweenTwoPrivateLinksDoesNotReorderThePrivateTabs() {
        val store = store()
        store.add(p1, private = true, select = false, url = "https://x.example/1")
        store.add(n1, select = false, url = "https://x.example/n")
        store.add(p2, private = true, select = false, url = "https://x.example/2")
        assertEquals(listOf(p1, p2), store.privateIds())
        assertEquals(listOf(a, n1, b, c), store.idsIn(null))
    }

    @Test fun threeBackgroundTabsAfterAClosedFirstChildStillFollowTheParent() {
        val store = store()
        store.add(n1, select = false, url = "https://x.example/1")
        store.close(n1)
        store.add(n2, select = false, url = "https://x.example/2")
        store.add(n3, select = false, url = "https://x.example/3")
        assertEquals(listOf(a, n2, n3, b, c), store.idsIn(null))
    }
}
