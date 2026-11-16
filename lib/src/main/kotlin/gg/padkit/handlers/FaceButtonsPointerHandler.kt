/*
 * Copyright (c) Jam.gg 2024.
 * Copyright (c) Filippo Scognamiglio 2025.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package gg.padkit.handlers

import androidx.compose.ui.geometry.Offset
import gg.padkit.anchors.Anchor
import gg.padkit.ids.Id
import gg.padkit.inputstate.InputState
import gg.padkit.inputstate.setDigitalKeyIfPressed
import gg.padkit.utils.coerceIn
import kotlinx.collections.immutable.PersistentSet

internal class FaceButtonsPointerHandler(
    private val anchors: List<Anchor<Id.Key>>,
    private val trackPointers: Boolean,
) : PointerHandler<Unit> {
    private val keys =
        anchors
            .flatMap { it.buttons }
            .toSet()
    private val pressedKeys = HashSet<Id.Key>()
    private var cachedInputState = InputState.Empty

    override fun handle(
        pointers: List<Pointer>,
        inputState: InputState,
        trackedIds: Set<Long>,
        data: Unit,
    ): Result {
        if (anchors.isEmpty()) return Result(inputState)
        pressedKeys.clear()
        for (pointer in pointers) pressedKeys.addAll(findClosestAnchor(pointer))

        val finalState =
            if (inputState === InputState.Empty) {
                // PadKit passes Empty: preserve the contribution while touches move within the same buttons.
                for (key in keys) cachedInputState = cachedInputState.setDigitalKey(key, key in pressedKeys)
                cachedInputState
            } else {
                keys.fold(inputState) { updatedState, key ->
                    updatedState.setDigitalKeyIfPressed(key, key in pressedKeys)
                }
            }

        // Most drags retain one captured pointer; avoid rebuilding its list and set.
        val updatedTrackedIndices =
            when {
                !trackPointers || pointers.isEmpty() -> emptySet()
                pointers.size == 1 -> {
                    val pointerId = pointers.first().pointerId
                    if (trackedIds.size == 1 && pointerId in trackedIds) trackedIds else setOf(pointerId)
                }
                else -> pointers.map { it.pointerId }.toSet()
            }

        return Result(finalState, updatedTrackedIndices)
    }

    private fun findClosestAnchor(pointer: Pointer): PersistentSet<Id.Key> {
        val coercedPosition = pointer.position.coerceIn(Offset(-1f, -1f), Offset(1f, 1f))
        return anchors
            .minBy {
                it.distance(coercedPosition)
            }
            .buttons
    }
}
