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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import gg.padkit.config.HapticFeedbackSettings
import gg.padkit.config.HapticFeedbackType
import gg.padkit.handlers.Pointer
import gg.padkit.haptics.HapticFeedback
import gg.padkit.haptics.InputHapticGenerator
import gg.padkit.haptics.rememberHapticFeedback
import gg.padkit.ids.Id
import gg.padkit.inputevents.InputEvent
import gg.padkit.inputevents.InputEventsGenerator
import gg.padkit.inputstate.InputState

/**
 * A layout composable that creates a virtual gamepad.
 *
 * PadKit places its children inside a [PadKitScope], allowing you to use various controls like
 * [gg.padkit.controls.ControlAnalog], [gg.padkit.controls.ControlButton], [gg.padkit.controls.ControlCross], and [gg.padkit.controls.ControlFaceButtons]. It listens for
 * pointer (touch) events within its bounds and updates the state of the controls accordingly.
 *
 * The most recent state of all controls is available through the [onInputStateUpdated] callback,
 * which provides an [InputState] object. For a stream of discrete input changes, use the
 * [onInputEvents] callback.
 *
 * By default, PadKit fills the maximum size available to it. You can change this behavior by
 * providing a different `modifier`. The aspect ratio of the gamepad can be controlled via the
 * modifier chain, which is useful for maintaining a consistent layout across different screen sizes.
 *
 * For testing or programmatic control, you can use [simulatedState] and [simulatedControlIds]
 * to override the input from user touches for specific controls.
 *
 * @param modifier The modifier to be applied to the layout.
 * @param onInputStateUpdated A callback that is invoked frequently with the complete, up-to-date
 * [InputState] of all controls on the gamepad.
 * @param onInputEvents A callback that is invoked only when a control's state changes, providing a
 * list of specific [InputEvent]s that have occurred.
 * @param hapticFeedbackType The type of haptic feedback to generate when a control is used.
 * @param simulatedControlIds A set of [Id]s that should be controlled by the [simulatedState]
 * instead of user input. This is useful for testing or creating demos.
 * @param simulatedState The [InputState] to use for the controls specified in [simulatedControlIds].
 * @param hapticFeedbackSettings Feedback strength, transition policy, and excluded control IDs.
 * @param hapticFeedback Optional output adapter. Null selects the platform implementation.
 * @param onUnhandledPointers Optional host input outside the control hit regions. Reports all
 * pressed unhandled pointers in root coordinates, and an empty list on release or cancellation.
 * Pointers starting outside controls stay with the host until released, even when dragged over
 * controls. Pointers starting on controls never transfer to the host during that gesture.
 * @param content A lambda that receives a [PadKitScope] where you can define the layout of the
 * gamepad by adding control composables.
 */
@Composable
fun PadKit(
    modifier: Modifier = Modifier,
    onInputStateUpdated: ((InputState) -> Unit)? = null,
    onInputEvents: ((List<InputEvent>) -> Unit)? = null,
    hapticFeedbackType: HapticFeedbackType = HapticFeedbackType.PRESS,
    simulatedControlIds: State<Set<Id>> = mutableStateOf(emptySet()),
    simulatedState: State<InputState> = mutableStateOf(InputState()),
    hapticFeedbackSettings: HapticFeedbackSettings = HapticFeedbackSettings(),
    hapticFeedback: HapticFeedback? = null,
    onUnhandledPointers: ((List<UnhandledPointer>) -> Unit)? = null,
    content: @Composable PadKitScope.() -> Unit,
) {
    val scope = remember { PadKitScope() }
    val rootPosition = remember { mutableStateOf(Offset.Zero) }

    val inputEventsGenerator = remember { InputEventsGenerator() }
    val hapticGenerator =
        if (hapticFeedbackType == HapticFeedbackType.NONE || hapticFeedbackSettings.intensity == 0f) {
            null
        } else {
            hapticFeedback ?: rememberHapticFeedback()
        }
    val inputHapticGenerator = remember { InputHapticGenerator(scope.inputState.value) }
    val currentStateCallback = rememberUpdatedState(onInputStateUpdated)
    val currentEventsCallback = rememberUpdatedState(onInputEvents)
    val currentHapticGenerator = rememberUpdatedState(hapticGenerator)
    val currentHapticType = rememberUpdatedState(hapticFeedbackType)
    val currentHapticSettings = rememberUpdatedState(hapticFeedbackSettings)
    val currentUnhandledCallback = rememberUpdatedState(onUnhandledPointers)

    DisposableEffect(scope) {
        scope.setInputStateListener { state ->
            val events = inputEventsGenerator.onInputStateChanged(state)
            currentStateCallback.value?.invoke(state)
            currentEventsCallback.value?.invoke(events)
            inputHapticGenerator.onInputStateChanged(
                state,
                currentHapticGenerator.value,
                currentHapticType.value,
                currentHapticSettings.value,
            )
        }
        onDispose {
            scope.reset()
            scope.setInputStateListener(null)
        }
    }

    // Read both in composition so changing just the selected IDs also updates
    // the override, and the pointer loop never captures old State instances.
    val controlIds = simulatedControlIds.value
    val simulatedInput = simulatedState.value
    SideEffect {
        scope.updateSimulatedInput(controlIds, simulatedInput)
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .onGloballyPositioned { rootPosition.value = it.positionInRoot() }
                .pointerInput(scope) {
                    val router = TouchPointerRouter()
                    try {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val pointers =
                                    event.changes
                                        .filter { it.pressed }
                                        .map { Pointer(it.id.value, it.position + rootPosition.value) }
                                val routed = router.route(pointers, currentUnhandledCallback.value != null, scope::hitsControl)
                                scope.handleInputEvent(routed.controls.asSequence())
                                currentUnhandledCallback.value?.invoke(routed.unhandled)
                            }
                        }
                    } finally {
                        scope.resetTouchInput()
                        currentUnhandledCallback.value?.invoke(emptyList())
                    }
                },
    ) {
        scope.content()
    }
}
