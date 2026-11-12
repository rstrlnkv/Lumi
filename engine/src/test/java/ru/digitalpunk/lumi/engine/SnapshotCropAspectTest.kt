package ru.digitalpunk.lumi.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class SnapshotCropAspectTest {
    @Test fun nanAspectKeepsTheFullHeightInsteadOfThrowing() = assertEquals(CropRect(0, 0, 1080, 2400), cropRect(1080, 2400, Float.NaN))

    @Test fun negativeAspectKeepsTheFullHeight() = assertEquals(CropRect(0, 0, 1080, 2400), cropRect(1080, 2400, -0.5f))

    @Test fun aspectSoWideThatTheHeightRoundsToZeroStillKeepsOneRow() = assertEquals(CropRect(0, 0, 1080, 1), cropRect(1080, 2400, 100_000f))

    @Test fun aspectTallerThanTheSourceIsClampedToTheSourceHeight() = assertEquals(CropRect(0, 0, 1080, 2400), cropRect(1080, 2400, 0.0001f))
}
