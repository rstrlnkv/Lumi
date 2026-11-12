package ru.digitalpunk.lumi.engine

/** How many tabs may keep a live Gecko session at once, from the device's RAM and app heap class. */
internal fun liveSessionCap(totalRamMb: Long, memoryClassMb: Int, isLowRam: Boolean): Int {
    if (isLowRam) return 2
    val byRam = (totalRamMb / 768).toInt()
    val byHeap = memoryClassMb / 48
    return minOf(byRam, byHeap).coerceIn(2, 6)
}

/**
 * The cap to apply under memory pressure; [level] is a `ComponentCallbacks2.TRIM_MEMORY_*` value.
 * `UI_HIDDEN` (20) fires on every backgrounding and does not evict.
 */
internal fun capForTrim(cap: Int, level: Int): Int = when {
    level >= 60 -> 1
    level >= 40 -> maxOf(1, cap / 2)
    level >= 20 -> cap
    level >= 15 -> 1
    level >= 10 -> maxOf(1, cap / 2)
    else -> cap
}
