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

package gg.padkit.controls

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import gg.padkit.PadKitScope
import gg.padkit.handlers.AnalogGestureHandler
import gg.padkit.handlers.AnalogPointerHandler
import gg.padkit.ids.Id
import gg.padkit.ui.DefaultButtonForeground
import gg.padkit.ui.DefaultControlBackground
import gg.padkit.utils.GeometryUtils
import gg.padkit.utils.ifUnspecified
import kotlin.math.roundToInt

/**
 * A composable that provides an analog stick control.
 *
 * This control allows the user to input a continuous direction, which is represented by an [Offset].
 * The value of the analog stick is available in the [InputState] of the [PadKit] composable.
 *
 * @param modifier The modifier to be applied to the control.
 * @param id The [Id.ContinuousDirection] to associate with this control.
 * @param analogPressId The [Id.Key] to associate with the press state of the analog stick.
 * A second touch within 200 ms holds this key until that touch is released.
 * @param foregroundSize The size of the foreground relative to the background.
 * @param background The composable to use as the background of the control.
 * @param foreground The composable to use as the foreground of the control. It receives a [State] that is `true` when the control is being touched.
 */
@Composable
fun PadKitScope.ControlAnalog(
    modifier: Modifier = Modifier,
    id: Id.ContinuousDirection,
    analogPressId: Id.Key? = null,
    foregroundSize: Float = 0.66f,
    background: @Composable () -> Unit = { DefaultControlBackground() },
    foreground: @Composable (State<Boolean>) -> Unit = {
        DefaultButtonForeground(pressedState = it, scale = 1f)
    },
) {
    val handler = remember(id, analogPressId) { AnalogPointerHandler(id, analogPressId) }
    DisposableEffect(handler) {
        registerHandler(handler, AnalogGestureHandler.Data())
        onDispose {
            unregisterHandler(handler)
        }
    }

    val positionState = remember(id) { derivedStateOf { inputState.value.getContinuousDirection(id) } }
    val pressedState = remember(id) { derivedStateOf { inputState.value.getContinuousDirection(id) != Offset.Unspecified } }
    AnalogControlLayout(
        modifier = modifier.onGloballyPositioned { updateHandlerPosition(handler, it.boundsInRoot()) },
        foregroundSize = foregroundSize,
        positionState = positionState,
        pressedState = pressedState,
        background = background,
        foreground = foreground,
    )
}

@Composable
internal fun AnalogControlLayout(
    modifier: Modifier,
    foregroundSize: Float,
    positionState: State<Offset>,
    pressedState: State<Boolean>,
    background: @Composable () -> Unit,
    foreground: @Composable (State<Boolean>) -> Unit,
) {
    BoxWithConstraints(modifier = modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.fillMaxSize()) { background() }
        val horizontalRange = constraints.maxWidth * 0.25f
        val verticalRange = constraints.maxHeight * 0.25f

        Box(
            modifier =
                Modifier
                    .fillMaxSize(foregroundSize)
                    .offset {
                        val position = GeometryUtils.mapSquareToCircle(positionState.value.ifUnspecified { Offset.Zero })
                        IntOffset(
                            (horizontalRange * position.x).roundToInt(),
                            (-verticalRange * position.y).roundToInt(),
                        )
                    },
        ) {
            foreground(pressedState)
        }
    }
}
