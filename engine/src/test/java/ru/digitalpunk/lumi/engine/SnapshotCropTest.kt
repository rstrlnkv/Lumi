package ru.digitalpunk.lumi.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class SnapshotCropTest {
    private val cardAspect = 182f / 220f

    @Test fun tallPageIsCroppedFromTheTopToTheCardAspect() =
        assertEquals(CropRect(left = 0, top = 0, width = 1080, height = 1305), cropRect(1080, 2400, cardAspect))

    @Test fun pageShorterThanTheCardKeepsItsFullHeight() =
        assertEquals(CropRect(0, 0, 1080, 800), cropRect(1080, 800, cardAspect))

    @Test fun wideCaptureScalesDownToTheTargetWidth() =
        assertEquals(PixelSize(480, 580), scaledSize(1080, 1305, targetWidth = 480))

    @Test fun narrowCaptureIsNeverUpscaled() =
        assertEquals(PixelSize(300, 400), scaledSize(300, 400, targetWidth = 480))
}
