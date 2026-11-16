package gg.padkit.inputstate

import androidx.compose.ui.geometry.Offset
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet

/** Visit presses before releases without constructing temporary difference sets. */
internal inline fun forEachKeyChange(
    previous: PersistentSet<Int>,
    current: PersistentSet<Int>,
    action: (Int, Boolean) -> Unit,
) {
    if (previous === current) return
    for (id in current) if (id !in previous) action(id, true)
    for (id in previous) if (id !in current) action(id, false)
}

/** Keep previous-key order, then newly added keys, without constructing a union. */
internal inline fun forEachDirectionChange(
    previous: PersistentMap<Int, Offset>,
    current: PersistentMap<Int, Offset>,
    action: (Int, Offset, Offset) -> Unit,
) {
    if (previous === current) return
    for ((id, before) in previous) {
        val after = current[id] ?: Offset.Unspecified
        if (before != after) action(id, before, after)
    }
    for ((id, after) in current) {
        if (id !in previous) action(id, Offset.Unspecified, after)
    }
}
