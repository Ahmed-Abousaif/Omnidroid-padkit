package gg.padkit.controls

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import gg.padkit.PadKitScope
import gg.padkit.handlers.AnalogGestureHandler
import gg.padkit.handlers.DiscreteAnalogPointerHandler
import gg.padkit.ids.Id
import gg.padkit.ui.DefaultButtonForeground
import gg.padkit.ui.DefaultControlBackground

/**
 * A thumbstick with smooth touch visuals and discrete, positive-up directional output.
 *
 * Only [Id.DiscreteDirection] events are emitted. Changing the dead zone or diagonal policy
 * recomputes a held direction immediately. Simulated directions override touch and visuals.
 * Unspecified means released; a touch inside the dead zone emits a neutral [Offset.Zero].
 */
@Composable
fun PadKitScope.ControlDirectionalAnalog(
    modifier: Modifier = Modifier,
    id: Id.DiscreteDirection,
    allowDiagonals: Boolean = true,
    deadZone: Float = 0.25f,
    foregroundSize: Float = 0.66f,
    background: @Composable () -> Unit = { DefaultControlBackground() },
    foreground: @Composable (State<Boolean>) -> Unit = {
        DefaultButtonForeground(pressedState = it, scale = 1f)
    },
) {
    require(deadZone.isFinite() && deadZone in 0f..1f) { "Dead zone must be finite and between 0 and 1" }
    val handler = remember(id) { DiscreteAnalogPointerHandler(id) }
    val data = remember(handler) { AnalogGestureHandler.Data() }
    DisposableEffect(handler) {
        registerHandler(handler, data)
        onDispose { unregisterHandler(handler) }
    }
    SideEffect {
        if (handler.allowDiagonals != allowDiagonals || handler.deadZone != deadZone) {
            handler.allowDiagonals = allowDiagonals
            handler.deadZone = deadZone
            updateHandlerInputState(handler, handler.currentInput(data))
        }
    }
    val position =
        remember(id, data) {
            derivedStateOf {
                if (isSimulated(id)) inputState.value.getDiscreteDirection(id) else data.direction
            }
        }
    val pressed = remember(position) { derivedStateOf { position.value != Offset.Unspecified } }
    AnalogControlLayout(
        modifier = modifier.onGloballyPositioned { updateHandlerPosition(handler, it.boundsInRoot()) },
        foregroundSize = foregroundSize,
        positionState = position,
        pressedState = pressed,
        background = background,
        foreground = foreground,
    )
}
