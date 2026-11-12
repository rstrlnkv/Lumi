package ru.digitalpunk.lumi.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SnapshotCropEdgeTest {
    private val cardAspect = 182f / 220f

    @Test fun oneByOneBitmapCropsToOneByOne() = assertEquals(CropRect(0, 0, 1, 1), cropRect(1, 1, cardAspect))

    @Test fun oneByOneBitmapIsNotScaled() = assertEquals(PixelSize(1, 1), scaledSize(1, 1, 480))

    @Test fun aspectZeroKeepsTheFullHeight() = assertEquals(CropRect(0, 0, 1080, 2400), cropRect(1080, 2400, 0f))

    @Test fun cropNeverLeavesTheSourceOrCollapsesForAnyPositiveSize() {
        for (w in listOf(1, 2, 7, 360, 1080, 4000)) for (h in listOf(1, 2, 7, 800, 2400, 20000)) {
            val c = cropRect(w, h, cardAspect)
            assertTrue(c.width in 1..w && c.height in 1..h, "crop $c of ${w}x$h")
            assertEquals(0, c.left); assertEquals(0, c.top)
        }
    }

    @Test fun scaledSizeNeverCollapsesAnAxisToZeroForAPositiveSource() {
        for (w in listOf(481, 1080, 4000)) for (h in listOf(1, 2, 3, 800)) {
            val s = scaledSize(w, h, 480)
            assertTrue(s.width >= 1 && s.height >= 1, "scaled ${w}x$h -> $s")
        }
    }

    @Test fun scaledSizeToTargetZeroDoesNotAskForAnEmptyBitmap() {
        val s = scaledSize(1080, 1305, 0)
        assertTrue(s.width >= 1 && s.height >= 1, "scaled to target 0 -> $s")
    }

    @Test fun zeroSizedSourceDoesNotThrow() {
        cropRect(0, 0, cardAspect)
        cropRect(0, 0, 0f)
        scaledSize(0, 0, 480)
    }

    @Test fun negativeSizeNeverGivesANegativeCrop() {
        val c = cropRect(-10, 100, cardAspect)
        assertTrue(c.width >= 0 && c.height >= 0, "crop $c")
    }

    @Test fun negativeAspectNeverGivesANegativeHeight() {
        assertTrue(cropRect(1080, 2400, -1f).height >= 0)
    }

    @Test fun productionCardAspectIsPortraitSoACropExists() {
        val c = cropRect(1080, 2400, 182f / 220f)
        assertEquals(1305, c.height)
    }
}
