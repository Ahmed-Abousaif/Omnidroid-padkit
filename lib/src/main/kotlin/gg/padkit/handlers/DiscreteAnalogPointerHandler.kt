package gg.padkit.handlers

import androidx.compose.ui.geometry.Offset
import gg.padkit.ids.Id
import gg.padkit.inputstate.InputState
import gg.padkit.utils.quantizeDirection

internal class DiscreteAnalogPointerHandler(private val id: Id.DiscreteDirection) : AnalogGestureHandler() {
    var allowDiagonals: Boolean = true
    var deadZone: Float = 0.25f

    fun currentInput(data: Data): InputState = updateInputState(InputState(), data.direction, false)

    override fun updateInputState(
        inputState: InputState,
        direction: Offset,
        pressed: Boolean,
    ): InputState =
        inputState.setDiscreteDirection(
            id,
            if (direction == Offset.Unspecified) direction else quantizeDirection(direction, allowDiagonals, deadZone),
        )
}
