package gg.padkit.handlers

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import gg.padkit.PadKitScope
import gg.padkit.ids.Id
import gg.padkit.inputstate.InputState
import gg.padkit.utils.quantizeDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiscreteAnalogTest {
    @Test
    fun eightWayModePreservesAllDirectionsAndPositiveUp() {
        listOf(
            Offset(1f, 0f),
            Offset(1f, 1f),
            Offset(0f, 1f),
            Offset(-1f, 1f),
            Offset(-1f, 0f),
            Offset(-1f, -1f),
            Offset(0f, -1f),
            Offset(1f, -1f),
        )
            .forEach { assertEquals(it, quantizeDirection(it, true, 0.25f)) }
    }

    @Test
    fun fourWayModeChoosesDominantAxisWithHorizontalTies() {
        assertEquals(Offset(1f, 0f), quantizeDirection(Offset(0.8f, 0.5f), false, 0.25f))
        assertEquals(Offset(-1f, 0f), quantizeDirection(Offset(-0.8f, -0.5f), false, 0.25f))
        assertEquals(Offset(0f, 1f), quantizeDirection(Offset(0.5f, 0.8f), false, 0.25f))
        assertEquals(Offset(0f, -1f), quantizeDirection(Offset(-0.5f, -0.8f), false, 0.25f))
        assertEquals(Offset(1f, 0f), quantizeDirection(Offset(1f, 1f), false, 0.25f))
    }

    @Test
    fun invalidDeadZoneAndBoundaryValuesAreSafe() {
        listOf(
            Offset.Zero,
            Offset(0.1f, 0.1f),
            Offset.Unspecified,
            Offset(Float.POSITIVE_INFINITY, 1f),
            Offset(1f, Float.NaN),
        )
            .forEach { input ->
                assertEquals(Offset.Zero, quantizeDirection(input, true, 0.25f))
                assertEquals(Offset.Zero, quantizeDirection(input, false, 0.25f))
            }
        assertEquals(Offset(1f, 0f), quantizeDirection(Offset(0.25f, 0f), false, 0.25f))
        assertEquals(Offset.Zero, quantizeDirection(Offset.Zero, true, 0f))
    }

    @Test
    fun policyRecomputesHeldOutputWhileRawVisualAndCaptureStaySmooth() {
        val scope = PadKitScope()
        val id = Id.DiscreteDirection(7)
        val handler = DiscreteAnalogPointerHandler(id)
        val data = AnalogGestureHandler.Data()
        scope.registerHandler(handler, data)
        scope.updateHandlerPosition(handler, Rect(0f, 0f, 100f, 100f))
        scope.handleInputEvent(sequenceOf(Pointer(1, Offset(50f, 50f))))
        scope.handleInputEvent(sequenceOf(Pointer(1, Offset(85f, 25f))))
        val raw = data.direction
        assertEquals(Offset(1f, 1f), scope.inputState.value.getDiscreteDirection(id))
        handler.allowDiagonals = false
        scope.updateHandlerInputState(handler, handler.currentInput(data))
        assertEquals(Offset(1f, 0f), scope.inputState.value.getDiscreteDirection(id))
        assertEquals(raw, data.direction)
        assertFalse(raw == Offset(1f, 0f))
        handler.deadZone = 1f
        scope.updateHandlerInputState(handler, handler.currentInput(data))
        assertEquals(Offset.Zero, scope.inputState.value.getDiscreteDirection(id))
        assertTrue(scope.inputState.value.continuousDirections.isEmpty())
        scope.handleInputEvent(sequenceOf(Pointer(1, Offset(150f, 50f))))
        assertEquals(Offset(1f, 0f), scope.inputState.value.getDiscreteDirection(id))
        scope.resetTouchInput()
        assertEquals(Offset.Unspecified, data.direction)
        handler.allowDiagonals = true
        scope.updateHandlerInputState(handler, handler.currentInput(data))
        assertEquals(InputState(), scope.inputState.value)
    }

    @Test
    fun simulationRemovalRestoresRawTouchAndUnregisterDoesNotResurrectIt() {
        val scope = PadKitScope()
        val id = Id.DiscreteDirection(1)
        val handler = DiscreteAnalogPointerHandler(id)
        val data = AnalogGestureHandler.Data()
        scope.registerHandler(handler, data)
        scope.updateHandlerPosition(handler, Rect(0f, 0f, 100f, 100f))
        scope.handleInputEvent(sequenceOf(Pointer(1, Offset(50f, 50f))))
        scope.handleInputEvent(sequenceOf(Pointer(1, Offset(80f, 50f))))
        scope.updateSimulatedInput(setOf(id), InputState().setDiscreteDirection(id, Offset(-1f, 0f)))
        assertEquals(Offset(-1f, 0f), scope.inputState.value.getDiscreteDirection(id))
        scope.updateSimulatedInput(emptySet(), InputState())
        assertEquals(Offset(1f, 0f), scope.inputState.value.getDiscreteDirection(id))
        scope.unregisterHandler(handler)
        assertEquals(InputState(), scope.inputState.value)
        scope.updateHandlerInputState(handler, handler.currentInput(data))
        assertEquals(InputState(), scope.inputState.value)
    }
}
