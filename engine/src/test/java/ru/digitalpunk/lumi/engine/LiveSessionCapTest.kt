package ru.digitalpunk.lumi.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class LiveSessionCapTest {
    @Test fun lowRamDeviceGetsTwo() = assertEquals(2, liveSessionCap(totalRamMb = 8000, memoryClassMb = 512, isLowRam = true))

    @Test fun fourGbWithClass256GetsFive() = assertEquals(5, liveSessionCap(totalRamMb = 4096, memoryClassMb = 256, isLowRam = false))

    @Test fun bigRamIsClampedToSix() = assertEquals(6, liveSessionCap(totalRamMb = 16000, memoryClassMb = 512, isLowRam = false))

    @Test fun tinyHeapIsClampedToTwo() = assertEquals(2, liveSessionCap(totalRamMb = 8000, memoryClassMb = 64, isLowRam = false))

    @Test fun criticalTrimKeepsOnlySelected() = assertEquals(1, capForTrim(cap = 5, level = 15))

    @Test fun runningLowHalvesTheCap() = assertEquals(2, capForTrim(cap = 5, level = 10))

    @Test fun runningLowNeverGoesBelowOne() = assertEquals(1, capForTrim(cap = 1, level = 10))

    @Test fun moderateTrimLeavesTheCap() = assertEquals(5, capForTrim(cap = 5, level = 5))
}
