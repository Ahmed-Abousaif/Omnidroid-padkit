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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import gg.padkit.inputstate.InputState
import gg.padkit.utils.Constants
import gg.padkit.utils.GeometryUtils
import gg.padkit.utils.coerceIn
import kotlin.time.TimeMark
import kotlin.time.TimeSource

internal abstract class AnalogGestureHandler : PointerHandler {
    data class Data(
        var lastDownEvent: TimeMark = TimeSource.Monotonic.markNow() - Constants.DOUBLE_TAP_INTERVAL,
        var pressed: Boolean = false,
        var startPosition: Offset = Offset.Unspecified,
    ) {
        var direction by mutableStateOf(Offset.Unspecified)
    }

    override fun reset(data: Any?) {
        val analogData = data as Data
        analogData.lastDownEvent = TimeSource.Monotonic.markNow() - Constants.DOUBLE_TAP_INTERVAL
        analogData.direction = Offset.Unspecified
        analogData.pressed = false
        analogData.startPosition = Offset.Unspecified
    }

    override fun handle(
        pointers: List<Pointer>,
        inputState: InputState,
        trackedIds: Set<Long>,
        data: Any?,
    ): Result {
        val analogData = data as Data

        val currentlyDraggedPointer =
            pointers
                .firstOrNull { trackedIds.contains(it.pointerId) }

        val result =
            when {
                pointers.isEmpty() -> {
                    analogData.pressed = false
                    analogData.startPosition = Offset.Unspecified
                    Result(
                        updateDirection(analogData, inputState, Offset.Unspecified),
                        emptySet(),
                    )
                }
                analogData.startPosition != Offset.Unspecified && currentlyDraggedPointer != null -> {
                    val deltaPosition =
                        Offset(
                            currentlyDraggedPointer.position.x - analogData.startPosition.x,
                            analogData.startPosition.y - currentlyDraggedPointer.position.y,
                        )
                    val offsetValue = deltaPosition.coerceIn(Offset(-1f, -1f), Offset(1f, 1f))
                    Result(
                        updateDirection(analogData, inputState, GeometryUtils.mapCircleToSquare(offsetValue)),
                        trackedIds,
                    )
                }
                else -> {
                    val firstPointer = pointers.first()
                    val previousTime = analogData.lastDownEvent
                    val currentTime = TimeSource.Monotonic.markNow()
                    analogData.lastDownEvent = currentTime
                    analogData.pressed = previousTime.elapsedNow() < Constants.DOUBLE_TAP_INTERVAL
                    analogData.startPosition = firstPointer.position

                    Result(updateDirection(analogData, inputState, Offset.Zero), setOf(firstPointer.pointerId))
                }
            }
        return result
    }

    private fun updateDirection(
        data: Data,
        state: InputState,
        direction: Offset,
    ): InputState {
        data.direction = direction
        return updateInputState(state, direction, data.pressed)
    }

    protected abstract fun updateInputState(
        inputState: InputState,
        direction: Offset,
        pressed: Boolean,
    ): InputState
}
