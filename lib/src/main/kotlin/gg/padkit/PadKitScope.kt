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
 * Scope for [PadKit] controls. Mutation is confined to the Compose/UI thread.
 * New pointers select the first registered control with an eligible circular hit;
 * captured pointers stay with their owner outside the bounds until release/cancellation.
 */
@Stable
class PadKitScope {
    private class HandlerState<D>(
        val pointerHandler: PointerHandler<D>,
        val data: D,
    ) {
        var trackedIds: Set<Long> = emptySet()
        private var usableBounds = false
        var rect = Rect.Zero
            set(value) {
                field = value
                usableBounds = value.width > 0f && value.height > 0f && value.width.isFinite() && value.height.isFinite() &&
                    value.left.isFinite() && value.top.isFinite() && value.right.isFinite() && value.bottom.isFinite()
            }
        var inputState = InputState.Empty
        var pointers = ArrayList<Pointer>()
        private var previousPointers = ArrayList<Pointer>()

        fun addPointer(pointer: Pointer) {
            val relative = pointer.position.relativeToCenter(rect)
            val previous = previousPointers.getOrNull(pointers.size)
            pointers.add(
                if (previous?.pointerId == pointer.pointerId && previous.position == relative) {
                    previous
                } else {
                    Pointer(pointer.pointerId, relative)
                },
            )
        }

        fun update(): Boolean {
            if (pointers == previousPointers) return false
            val result = pointerHandler.handle(pointers, InputState.Empty, trackedIds, data)
            trackedIds = result.trackedIds
            val changed = inputState != result.inputState
            if (changed) inputState = result.inputState
            val scratch = previousPointers
            previousPointers = pointers
            pointers = scratch
            pointers.clear()
            return changed
        }

        fun reset() {
            pointerHandler.reset(data)
            trackedIds = emptySet()
            pointers.clear()
            previousPointers.clear()
            inputState = InputState.Empty
        }

        fun hasUsableBounds(): Boolean = usableBounds

        fun hit(position: Offset): Boolean =
            hasUsableBounds() && rect.contains(position) && position.relativeToCenter(rect).getDistanceSquared() <= 1f
    }

    private val currentInputState = mutableStateOf(InputState.Empty)
    internal val inputState: State<InputState> = currentInputState
    private val handlers = linkedMapOf<PointerHandler<*>, HandlerState<*>>()
    private val pointerOwners = mutableMapOf<Long, HandlerState<*>>()
    private var touchState = InputState.Empty
    private var touchStateDirty = false
    private var simulatedControlIds: Set<Id> by mutableStateOf(emptySet())
    private var simulatedInputState = InputState.Empty
    private var inputStateListener: ((InputState) -> Unit)? = null
    private val pendingStates = ArrayDeque<InputState>()
    private var dispatching = false

    internal fun setInputStateListener(listener: ((InputState) -> Unit)?) {
        inputStateListener = listener
        if (listener != null) dispatchState(inputState.value)
    }

    internal fun registerHandler(pointerHandler: PointerHandler<Unit>) = registerHandler(pointerHandler, Unit)

    internal fun <D> registerHandler(
        pointerHandler: PointerHandler<D>,
        data: D,
    ) {
        handlers[pointerHandler] = HandlerState(pointerHandler, data)
    }

    internal fun unregisterHandler(pointerHandler: PointerHandler<*>) {
        val removed = handlers.remove(pointerHandler) ?: return
        for (id in removed.trackedIds) pointerOwners.remove(id)
        if (removed.inputState != InputState.Empty) touchStateDirty = true
        removed.reset()
        publishInputState()
    }

    internal fun updateHandlerPosition(
        pointerHandler: PointerHandler<*>,
        rect: Rect,
    ) {
        val handler = handlers[pointerHandler] ?: return
        if (handler.rect == rect) return
        handler.rect = rect
        if (!handler.hasUsableBounds()) {
            for (id in handler.trackedIds) pointerOwners.remove(id)
            if (handler.inputState != InputState.Empty) touchStateDirty = true
            handler.reset()
            publishInputState()
        }
    }

    internal fun isSimulated(id: Id): Boolean = id in simulatedControlIds

    /** Recompute held output after a policy change without disturbing capture. */
    internal fun updateHandlerInputState(
        pointerHandler: PointerHandler<*>,
        state: InputState,
    ) {
        val handler = handlers[pointerHandler] ?: return
        if (handler.inputState == state) return
        handler.inputState = state
        touchStateDirty = true
        publishInputState()
    }

    // Host routing and control dispatch must use the same complete hit predicate.
    internal fun hitsControl(position: Offset): Boolean = handlers.values.any { it.hit(position) }

    internal fun handleInputEvent(eventPointers: Sequence<Pointer>): InputState {
        for (handler in handlers.values) handler.pointers.clear()
        for (pointer in eventPointers) {
            if (!pointer.position.x.isFinite() || !pointer.position.y.isFinite()) continue
            val owner = pointerOwners[pointer.pointerId]
            if (owner != null) {
                if (owner.hasUsableBounds()) owner.addPointer(pointer)
            } else {
                handlers.values.firstOrNull { it.hit(pointer.position) }?.addPointer(pointer)
            }
        }
        for (handler in handlers.values) {
            val previousTrackedIds = handler.trackedIds
            if (handler.update()) touchStateDirty = true
            // A new set with the same IDs does not change capture ownership.
            if (previousTrackedIds != handler.trackedIds) {
                for (id in previousTrackedIds) {
                    if (id !in handler.trackedIds) pointerOwners.remove(id)
                }
                for (id in handler.trackedIds) pointerOwners[id] = handler
            }
        }
        publishInputState()
        return inputState.value
    }

    internal fun updateSimulatedInput(
        controlIds: Set<Id>,
        state: InputState,
    ) {
        val idsChanged = simulatedControlIds != controlIds
        if (!idsChanged && simulatedInputState == state) return
        if (idsChanged) simulatedControlIds = controlIds.toSet()
        simulatedInputState = state
        publishInputState()
    }

    /** Cancel touch ownership while preserving independently simulated controls. */
    internal fun resetTouchInput() {
        pointerOwners.clear()
        for (handler in handlers.values) {
            if (handler.inputState != InputState.Empty) touchStateDirty = true
            handler.reset()
        }
        publishInputState()
    }

    /** Release every source synchronously before disposal. */
    internal fun reset() {
        simulatedControlIds = emptySet()
        simulatedInputState = InputState.Empty
        resetTouchInput()
    }

    private fun publishInputState() {
        if (touchStateDirty) {
            var merged = InputState.Empty
            for (handler in handlers.values) merged = merged.merge(handler.inputState)
            touchState = merged
            touchStateDirty = false
        }
        var mergedState = touchState
        for (id in simulatedControlIds) {
            mergedState =
                when (id) {
                    is Id.Key -> mergedState.setDigitalKey(id, simulatedInputState.getDigitalKey(id))
                    is Id.ContinuousDirection -> mergedState.setContinuousDirection(id, simulatedInputState.getContinuousDirection(id))
                    is Id.DiscreteDirection -> mergedState.setDiscreteDirection(id, simulatedInputState.getDiscreteDirection(id))
                }
        }
        if (currentInputState.value != mergedState) {
            currentInputState.value = mergedState
            dispatchState(mergedState)
        }
    }

    private fun dispatchState(state: InputState) {
        // Keep quick transitions, reentrant changes, and disposal releases in order.
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
