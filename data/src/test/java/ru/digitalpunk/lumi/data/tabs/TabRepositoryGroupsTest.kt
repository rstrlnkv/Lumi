package ru.digitalpunk.lumi.data.tabs

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TabRepositoryGroupsTest {
    private val dao = FakeTabDao()
    private val repo = TabRepository(dao, BlobFiles(File(createTempDirectory("groups").toFile(), "state"), "json", maxBytes = 8), Dispatchers.Unconfined, now = { 5L })
    private val groups = listOf(StoredGroup("g2", "Reading"), StoredGroup("g1", "Work"))

    private fun tab(id: String, group: String? = null) = StoredTab(id, "https://x", "t", null, groupId = group)

    @Test fun savedGroupsAreLoadedInStoredOrder() = runBlocking {
        assertTrue(repo.save(listOf(tab("a")), "a", groups))
        assertEquals(groups, repo.load()!!.groups)
    }

    @Test fun eachTabsGroupIdRoundTrips() = runBlocking {
        repo.save(listOf(tab("a", "g1"), tab("b"), tab("c", "g2")), "a", groups)
        assertEquals(listOf("g1", null, "g2"), repo.load()!!.tabs.map { it.groupId })
    }

    @Test fun currentGroupRoundTrips() = runBlocking {
        repo.save(listOf(tab("a")), "a", groups, currentGroupId = "g1")
        assertEquals("g1", repo.load()!!.currentGroupId)
        repo.save(listOf(tab("a")), "a", groups, currentGroupId = null)
        assertNull(repo.load()!!.currentGroupId)
    }

    @Test fun saveWithoutGroupsStillWorks() = runBlocking {
        assertTrue(repo.save(listOf(tab("a")), "a"))
        assertEquals(emptyList(), repo.load()!!.groups)
    }

    @Test fun groupWithNoTabsIsStoredAndLoaded() = runBlocking {
        repo.save(listOf(tab("a")), "a", groups)
        assertEquals(listOf("g2", "g1"), repo.load()!!.groups.map { it.id })
        assertEquals(listOf(0, 1), dao.groupRows.map { it.position })
    }

    @Test fun deletedGroupDoesNotSurviveTheNextSave() = runBlocking {
        repo.save(listOf(tab("a")), "a", groups)
        repo.save(listOf(tab("a")), "a", groups.take(1))
        assertEquals(listOf("g2"), repo.load()!!.groups.map { it.id })
    }
}
