package ru.digitalpunk.lumi.data.icons

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SiteIconStoreTest {
    private val dir = File(createTempDirectory("icons").toFile(), "icons")

    @Test fun theFileNameIs40HexCharactersAndPng() {
        val store = SiteIconStore(dir)
        assertTrue(store.write("example.com", byteArrayOf(1, 2, 3)))
        val names = dir.list()!!.toList()
        assertEquals(1, names.size)
        assertTrue(Regex("[0-9a-f]{40}\\.png").matches(names.single()), names.single())
        assertContentEquals(byteArrayOf(1, 2, 3), store.read("example.com"))
    }

    @Test fun aHostileHostCannotEscapeTheFolder() {
        val store = SiteIconStore(dir)
        assertTrue(store.write("../../x", byteArrayOf(9)))
        assertEquals(1, dir.list()!!.size)
        assertFalse(File(dir.parentFile, "x.png").exists())
    }

    @Test fun aMissingIconHasNoModificationTime() = assertNull(SiteIconStore(dir).lastModified("none.com"))

    @Test fun aFailedWriteLeavesNoPng() {
        val store = SiteIconStore(dir)
        val name = store.write("a.com", byteArrayOf(1)).let { dir.list()!!.single().removeSuffix(".png") }
        File(dir, "$name.png").delete()
        File(dir, "$name.tmp").apply { mkdirs(); File(this, "block").writeText("x") }
        assertFalse(store.write("a.com", byteArrayOf(2)))
        assertFalse(File(dir, "$name.png").exists())
    }

    @Test fun aStaleTempFileIsRemovedWhenTheStoreOpens() {
        dir.mkdirs()
        val tmp = File(dir, "0123.tmp").apply { writeText("half") }
        SiteIconStore(dir)
        assertFalse(tmp.exists())
    }
}
