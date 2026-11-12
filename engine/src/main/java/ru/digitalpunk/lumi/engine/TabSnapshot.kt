package ru.digitalpunk.lumi.engine

import android.graphics.Bitmap

/** The top of a tab's page, cropped to the grid card's shape and scaled down. The receiver owns [bitmap]. */
class TabSnapshot(val id: TabId, val bitmap: Bitmap)
