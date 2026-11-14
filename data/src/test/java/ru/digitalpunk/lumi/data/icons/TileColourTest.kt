package ru.digitalpunk.lumi.data.icons

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TileColourTest {
    private fun argb(r: Int, g: Int, b: Int, a: Int = 255) = (a shl 24) or (r shl 16) or (g shl 8) or b
    private fun lum(c: Int): Double {
        fun ch(v: Int) = (v / 255.0).let { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * ch(c shr 16 and 0xFF) + 0.7152 * ch(c shr 8 and 0xFF) + 0.0722 * ch(c and 0xFF)
    }
    private fun contrast(a: Int, b: Int) = (max(lum(a), lum(b)) + 0.05) / (min(lum(a), lum(b)) + 0.05)

    @Test fun aMulticolourIconOnWhiteGivesItsMainColour() {
        val blue = argb(66, 133, 244)
        val pixels = IntArray(400) { i -> when { i < 150 -> blue; i < 200 -> argb(234, 67, 53); i < 230 -> argb(251, 188, 5); else -> argb(255, 255, 255) } }
        val got = assertNotNull(dominantColour(pixels))
        assertTrue(got and 0xFF > got shr 16 and 0xFF, "blue wins: ${Integer.toHexString(got)}")
    }

    @Test fun aWhiteOrGreyIconOnTransparentHasNoColour() {
        assertNull(dominantColour(IntArray(100) { if (it % 2 == 0) argb(255, 255, 255) else argb(0, 0, 0, 0) }))
        assertNull(dominantColour(IntArray(100) { argb(128, 128, 128) }))
        assertNull(dominantColour(IntArray(100) { argb(255, 0, 0, 100) }))
    }

    @Test fun theFaviconColourComesFirstThenTheThemeColourThenTheHost() {
        val red = argb(200, 30, 30); val green = argb(30, 160, 60)
        assertEquals(red, tileColour(red, green, "a.com"))
        assertEquals(green, tileColour(null, green, "a.com"))
        assertEquals(green, tileColour(argb(250, 250, 250), green, "a.com"))
        val fromHost = tileColour(null, argb(255, 255, 255), "a.com")
        assertEquals(fromHost, tileColour(null, null, "a.com"))
        assertTrue(fromHost ushr 24 == 0xFF)
    }

    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()

    @Test fun theLetterIsWhiteWhenWhiteHasAtLeast3To1AndElseBlackOnAnyBackground() {
        val rnd = Random(7)
        val backgrounds = List(1000) { argb(rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256)) } + argb(255, 255, 0) + argb(128, 128, 128)
        for (bg in backgrounds) {
            val c = letterColours(bg)
            assertEquals(bg, c.background)
            assertEquals(if (contrast(bg, white) >= 3.0) white else black, c.foreground, Integer.toHexString(bg))
        }
    }

    @Test fun theLetterTurnsBlackExactlyWhereWhiteDropsBelow3To1() {
        val grey = (0..255).first { contrast(argb(it, it, it), white) < 3.0 }
        assertTrue(contrast(argb(grey - 1, grey - 1, grey - 1), white) >= 3.0)
        assertEquals(white, letterColours(argb(grey - 1, grey - 1, grey - 1)).foreground)
        assertEquals(black, letterColours(argb(grey, grey, grey)).foreground)
    }

    @Test fun brandBluesRedsAndOrangesGetAWhiteLetterAndAVeryLightColourABlackOne() {
        for (bg in listOf(argb(0x33, 0x88, 0xFF), argb(0xFF, 0x00, 0x33), argb(0xFF, 0x55, 0x00)))
            assertEquals(white, letterColours(bg).foreground, Integer.toHexString(bg))
        assertEquals(black, letterColours(argb(0xFF, 0xF2, 0xCC)).foreground)
        assertEquals(black, letterColours(argb(0xFF, 0x66, 0x00)).foreground) // 2.94:1 for white, just under the 3:1 line
    }

    @Test fun aSmallIconOnABigTileIsALetterAndInARowStaysTheIcon() {
        assertEquals(TileMode.Letter, tileMode(IconClass.Small, bigTile = true, fillsTile = false))
        assertEquals(TileMode.SmallIcon, tileMode(IconClass.Small, bigTile = false, fillsTile = false))
        assertEquals(TileMode.Image, tileMode(IconClass.Good, bigTile = true, fillsTile = true))
        assertEquals(TileMode.SmallIcon, tileMode(IconClass.Sharp, bigTile = true, fillsTile = false))
        assertEquals(TileMode.SmallIcon, tileMode(IconClass.Vector, bigTile = true, fillsTile = false))
    }

    @Test fun aLegacyIconWithoutMetaIsAnImageWhenOpaqueElseALetterOnABigTile() {
        assertEquals(TileMode.Image, tileMode(IconClass.None, bigTile = true, fillsTile = true))
        assertEquals(TileMode.Letter, tileMode(IconClass.None, bigTile = true, fillsTile = false))
        assertEquals(TileMode.SmallIcon, tileMode(IconClass.None, bigTile = false, fillsTile = false))
    }
}
