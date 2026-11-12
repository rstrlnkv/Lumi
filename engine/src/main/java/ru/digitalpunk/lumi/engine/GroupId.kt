package ru.digitalpunk.lumi.engine

import java.util.UUID

@JvmInline
value class GroupId(val value: String) {
    companion object {
        fun random() = GroupId(UUID.randomUUID().toString())
    }
}

/** A named tab group. Also the shape used to save and restore groups. */
data class TabGroup(val id: GroupId, val name: String)
