package ru.digitalpunk.lumi.engine

import java.util.UUID

@JvmInline
value class TabId(val value: String) {
    companion object {
        fun random() = TabId(UUID.randomUUID().toString())
    }
}
