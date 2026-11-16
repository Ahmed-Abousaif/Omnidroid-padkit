package gg.padkit

import androidx.compose.ui.geometry.Offset
import gg.padkit.handlers.Pointer

/** Keep a gesture outside the controls with its host until every pointer is released. */
internal class TouchPointerRouter {
    private var previousIds = emptySet<Long>()
    private var unhandledIds = emptySet<Long>()

    data class Result(val controls: List<Pointer>, val unhandled: List<UnhandledPointer>)

    fun route(
        pointers: List<Pointer>,
        acceptUnhandled: Boolean,
        hitsControl: (Offset) -> Boolean,
    ): Result {
        val pressedIds = pointers.map { it.pointerId }.toSet()
        val newIds = pressedIds - previousIds
        unhandledIds = unhandledIds.intersect(pressedIds)
        if (acceptUnhandled) {
            unhandledIds +=
                pointers
                    .filter { it.pointerId in newIds && !hitsControl(it.position) }
                    .map { it.pointerId }
        }
        previousIds = pressedIds
        return Result(
            controls = pointers.filter { it.pointerId !in unhandledIds },
            unhandled =
                pointers.filter { it.pointerId in unhandledIds }
                    .map { UnhandledPointer(it.pointerId, it.position, isNew = it.pointerId in newIds) },
        )
    }
}
