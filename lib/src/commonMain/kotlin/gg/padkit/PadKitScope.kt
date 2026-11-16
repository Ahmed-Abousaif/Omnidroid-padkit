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

package gg.padkit

import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import gg.padkit.handlers.Pointer
import gg.padkit.handlers.PointerHandler
import gg.padkit.ids.Id
import gg.padkit.inputstate.InputState
import gg.padkit.inputstate.merge
import gg.padkit.utils.relativeToCenter

/**
 * A scope for the children of the [PadKit] composable.
 */
@Stable
class PadKitScope {
    private data class HandlerState(
        val pointerHandler: PointerHandler,
        var trackedIds: Set<Long>,
        var rect: Rect = Rect.Zero,
        val data: Any? = null,
        var inputState: InputState = InputState(),
    )

    private val currentInputState = mutableStateOf(InputState())
    internal val inputState: State<InputState> = currentInputState

    private val handlers = mutableMapOf<PointerHandler, HandlerState>()
    private var simulatedControlIds: Set<Id> by mutableStateOf(emptySet())
    private var simulatedInputState = InputState()
    private var inputStateListener: ((InputState) -> Unit)? = null
    private val pendingStates = ArrayDeque<InputState>()
    private var dispatching = false

    internal fun setInputStateListener(listener: ((InputState) -> Unit)?) {
        inputStateListener = listener
        if (listener != null) dispatchState(inputState.value)
    }

    internal fun registerHandler(
        pointerHandler: PointerHandler,
        data: Any? = null,
    ) {
        handlers[pointerHandler] = HandlerState(pointerHandler, emptySet(), Rect.Zero, data)
    }

    internal fun unregisterHandler(pointerHandler: PointerHandler) {
        val removed = handlers.remove(pointerHandler) ?: return
        pointerHandler.reset(removed.data)
        publishInputState()
    }

    internal fun updateHandlerPosition(
        pointerHandler: PointerHandler,
        rect: Rect,
    ) {
        handlers[pointerHandler]?.rect = rect
    }

    internal fun isSimulated(id: Id): Boolean = id in simulatedControlIds

    /** Recompute a held control after changing its policy, preserving pointer capture. */
    internal fun updateHandlerInputState(
        pointerHandler: PointerHandler,
        state: InputState,
    ) {
        val handler = handlers[pointerHandler] ?: return
        handler.inputState = state
        publishInputState()
    }

    private fun getAllHandlers(): Collection<HandlerState> {
        return handlers.values
    }

    private fun getTrackedIds(): Set<Long> {
        return handlers.values
            .flatMap { it.trackedIds }
            .toSet()
    }

    private fun getHandlerAtPosition(position: Offset): PointerHandler? {
        return handlers.values
            .firstOrNull { it.rect.contains(position) }
            ?.pointerHandler
    }

    internal fun hitsControl(position: Offset): Boolean {
        val handler = handlers.values.firstOrNull { it.rect.contains(position) } ?: return false
        // Use the same activation ellipse as handleInputEvent, including empty corners.
        return position.relativeToCenter(handler.rect).getDistanceSquared() <= 1f
    }

    private fun getHandlerTracking(pointerId: Long): PointerHandler? {
        return handlers.values
            .firstOrNull { it.trackedIds.contains(pointerId) }
            ?.pointerHandler
    }

    internal fun handleInputEvent(eventPointers: Sequence<Pointer>): InputState {
        val trackedPointers = getTrackedIds()

        val handlersAssociations: Map<PointerHandler?, List<Pointer>> =
            eventPointers
                .groupBy { pointer ->
                    if (pointer.pointerId in trackedPointers) {
                        getHandlerTracking(pointer.pointerId)
                    } else {
                        getHandlerAtPosition(pointer.position)
                    }
                }

        getAllHandlers()
            .forEach { handler ->
                val pointerHandler = handler.pointerHandler
                val pointers =
                    handlersAssociations.getOrElse(pointerHandler) { emptyList() }

                val relativePointers =
                    pointers
                        .map {
                            Pointer(
                                it.pointerId,
                                it.position.relativeToCenter(handler.rect),
                            )
                        }
                        .filter { pointer ->
                            val isClose = pointer.position.getDistanceSquared() <= 1f
                            val isTracked = handler.trackedIds.contains(pointer.pointerId)
                            isClose || isTracked
                        }

                val (updatedState, updatedTrackedIndices) =
                    pointerHandler.handle(
                        relativePointers,
                        InputState(),
                        handler.trackedIds,
                        handler.data,
                    )

                handler.trackedIds = updatedTrackedIndices
                handler.inputState = updatedState
            }

        publishInputState()
        return inputState.value
    }

    internal fun updateSimulatedInput(
        simulatedControlIds: Set<Id>,
        simulatedInputState: InputState,
    ) {
        this.simulatedControlIds = simulatedControlIds.toSet()
        this.simulatedInputState = simulatedInputState
        publishInputState()
    }

    /** Cancel touch ownership without disturbing independently simulated controls. */
    internal fun resetTouchInput() {
        handlers.values.forEach { handler ->
            handler.pointerHandler.reset(handler.data)
            handler.trackedIds = emptySet()
            handler.inputState = InputState()
        }
        publishInputState()
    }

    /** Release every source before the PadKit composition is disposed. */
    internal fun reset() {
        simulatedControlIds = emptySet()
        simulatedInputState = InputState()
        resetTouchInput()
    }

    private fun publishInputState() {
        val touchState = handlers.values.fold(InputState()) { state, handler -> state.merge(handler.inputState) }
        val mergedState =
            simulatedControlIds.fold(touchState) { state, id ->
                when (id) {
                    is Id.Key -> state.setDigitalKey(id, simulatedInputState.getDigitalKey(id))
                    is Id.ContinuousDirection -> state.setContinuousDirection(id, simulatedInputState.getContinuousDirection(id))
                    is Id.DiscreteDirection -> state.setDiscreteDirection(id, simulatedInputState.getDiscreteDirection(id))
                }
            }
        if (currentInputState.value != mergedState) {
            currentInputState.value = mergedState
            dispatchState(mergedState)
        }
    }

    private fun dispatchState(state: InputState) {
        // Deliver every transition, including releases during disposal. A state
        // observer can conflate a quick press/release or stop before disposal.
        pendingStates.addLast(state)
        if (dispatching) return
        dispatching = true
        try {
            while (pendingStates.isNotEmpty()) {
                val next = pendingStates.removeFirst()
                inputStateListener?.invoke(next)
            }
        } finally {
            pendingStates.clear()
            dispatching = false
        }
    }
}
