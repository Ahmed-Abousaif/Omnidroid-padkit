package gg.padkit

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import gg.padkit.anchors.Anchor
import gg.padkit.handlers.AnalogPointerHandler
import gg.padkit.handlers.ButtonPointerHandler
import gg.padkit.handlers.CrossPointerHandler
import gg.padkit.handlers.FaceButtonsPointerHandler
import gg.padkit.handlers.Pointer
import gg.padkit.handlers.PointerHandler
import gg.padkit.ids.Id
import gg.padkit.inputevents.InputEvent
import gg.padkit.inputevents.InputEventsGenerator
import gg.padkit.inputstate.InputState
import kotlinx.collections.immutable.persistentSetOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PadKitScopeTest {
    private val scope = PadKitScope()
    private val events = mutableListOf<InputEvent>()
    private val key = Id.Key(1)
    private val stick = Id.ContinuousDirection(2)
    private val cross = Id.DiscreteDirection(3)

    init {
        val generator = InputEventsGenerator()
        scope.setInputStateListener { state -> events.addAll(generator.onInputStateChanged(state)) }
    }

    private fun register(
        handler: PointerHandler,
        left: Float = 0f,
        data: Any? = null,
    ) {
        scope.registerHandler(handler, data)
        scope.updateHandlerPosition(handler, Rect(left, 0f, left + 100f, 100f))
    }

    private fun touch(vararg pointers: Pointer) = scope.handleInputEvent(pointers.asSequence())

    @Test
    fun removingHeldButtonEmitsReleaseWithoutAnotherTouch() {
        val button = ButtonPointerHandler(key)
        register(button)
        touch(Pointer(1, Offset(50f, 50f)))
        scope.unregisterHandler(button)
        assertEquals<List<InputEvent>>(listOf(InputEvent.Button(1, true), InputEvent.Button(1, false)), events)
        assertEquals(InputState(), scope.inputState.value)
    }

    @Test
    fun removingOneHandlerPreservesOtherFinger() {
        val first = ButtonPointerHandler(key)
        val second = ButtonPointerHandler(Id.Key(2))
        register(first)
        register(second, 200f)
        touch(Pointer(1, Offset(50f, 50f)), Pointer(2, Offset(250f, 50f)))
        scope.unregisterHandler(first)
        assertFalse(scope.inputState.value.getDigitalKey(key))
        assertTrue(scope.inputState.value.getDigitalKey(Id.Key(2)))
        assertFalse(events.contains(InputEvent.Button(2, false)))
    }

    @Test
    fun removingOneOfTwoContributorsDoesNotReleaseSharedKey() {
        val first = ButtonPointerHandler(key)
        val second = ButtonPointerHandler(key)
        register(first)
        register(second, 200f)
        touch(Pointer(1, Offset(50f, 50f)), Pointer(2, Offset(250f, 50f)))
        scope.unregisterHandler(first)
        assertTrue(scope.inputState.value.getDigitalKey(key))
        assertEquals<List<InputEvent>>(listOf(InputEvent.Button(1, true)), events)
        scope.unregisterHandler(second)
        assertEquals(InputEvent.Button(1, false), events.last())
    }

    @Test
    fun capturedStickContinuesOutsideBoundsAndResetsOnCancel() {
        register(AnalogPointerHandler(stick, null), data = AnalogPointerHandler.Data())
        touch(Pointer(1, Offset(50f, 50f)))
        touch(Pointer(1, Offset(200f, 50f)))
        assertEquals(Offset(1f, 0f), scope.inputState.value.getContinuousDirection(stick))
        scope.resetTouchInput()
        assertEquals(InputState(), scope.inputState.value)
        assertEquals(InputEvent.ContinuousDirection(2, Offset.Unspecified), events.last())
    }

    @Test
    fun cancelledStickStartsFreshAndDoesNotCountAsDoubleTap() {
        register(AnalogPointerHandler(stick, key), data = AnalogPointerHandler.Data())
        touch(Pointer(1, Offset(50f, 50f)))
        touch(Pointer(1, Offset(70f, 50f)))
        scope.resetTouchInput()
        touch(Pointer(2, Offset(70f, 50f)))
        assertEquals(Offset.Zero, scope.inputState.value.getContinuousDirection(stick))
        assertFalse(scope.inputState.value.getDigitalKey(key))
    }

    @Test
    fun removingHeldCrossReleasesItsDirection() {
        val handler =
            CrossPointerHandler(
                cross,
                listOf(Anchor(Offset(1f, 0f), persistentSetOf(CrossPointerHandler.Direction.RIGHT), 0.1f)),
            )
        register(handler)
        touch(Pointer(1, Offset(90f, 50f)))
        val direction = scope.inputState.value.getDiscreteDirection(cross)
        assertTrue(direction.x == 1f && direction.y == 0f)
        scope.unregisterHandler(handler)
        assertEquals(InputEvent.DiscreteDirection(3, Offset.Unspecified), events.last())
    }

    @Test
    fun faceClusterCapturesSeveralFingersAndReleasesEachKey() {
        val second = Id.Key(2)
        register(
            FaceButtonsPointerHandler(
                listOf(
                    Anchor(Offset(-1f, 0f), persistentSetOf(key), 0.2f),
                    Anchor(Offset(1f, 0f), persistentSetOf(second), 0.2f),
                ),
                true,
            ),
        )
        touch(Pointer(1, Offset(10f, 50f)), Pointer(2, Offset(90f, 50f)))
        assertTrue(scope.inputState.value.getDigitalKey(key))
        assertTrue(scope.inputState.value.getDigitalKey(second))
        touch(Pointer(1, Offset(-100f, 50f)), Pointer(2, Offset(200f, 50f)))
        assertTrue(scope.inputState.value.getDigitalKey(key))
        assertTrue(scope.inputState.value.getDigitalKey(second))
        touch(Pointer(2, Offset(200f, 50f)))
        assertFalse(scope.inputState.value.getDigitalKey(key))
        assertTrue(scope.inputState.value.getDigitalKey(second))
        scope.resetTouchInput()
        assertEquals(InputState(), scope.inputState.value)
    }

    @Test
    fun changingOnlySimulatedIdsReleasesRemovedOverrides() {
        val simulation = InputState().setDigitalKey(key, true).setContinuousDirection(stick, Offset(1f, 0f))
        scope.updateSimulatedInput(setOf(key), simulation)
        scope.updateSimulatedInput(setOf(stick), simulation)
        assertFalse(scope.inputState.value.getDigitalKey(key))
        assertEquals(Offset(1f, 0f), scope.inputState.value.getContinuousDirection(stick))
        assertTrue(events.contains(InputEvent.Button(1, false)))
    }

    @Test
    fun removingSimulationRestoresTouchStillHeld() {
        register(ButtonPointerHandler(key))
        touch(Pointer(1, Offset(50f, 50f)))
        scope.updateSimulatedInput(setOf(key), InputState())
        assertFalse(scope.inputState.value.getDigitalKey(key))
        scope.updateSimulatedInput(emptySet(), InputState())
        assertTrue(scope.inputState.value.getDigitalKey(key))
        assertEquals<List<InputEvent>>(
            listOf(InputEvent.Button(1, true), InputEvent.Button(1, false), InputEvent.Button(1, true)),
            events,
        )
    }

    @Test
    fun touchReleaseWhileOverriddenDoesNotRestoreStaleTouch() {
        register(ButtonPointerHandler(key))
        touch(Pointer(1, Offset(50f, 50f)))
        scope.updateSimulatedInput(setOf(key), InputState().setDigitalKey(key, true))
        touch()
        scope.updateSimulatedInput(emptySet(), InputState())
        assertEquals(InputState(), scope.inputState.value)
        assertEquals(InputEvent.Button(1, false), events.last())
    }

    @Test
    fun touchCancellationPreservesIndependentSimulationButFullResetReleasesIt() {
        register(ButtonPointerHandler(key))
        touch(Pointer(1, Offset(50f, 50f)))
        scope.updateSimulatedInput(setOf(cross), InputState().setDiscreteDirection(cross, Offset(0f, 1f)))
        scope.resetTouchInput()
        assertFalse(scope.inputState.value.getDigitalKey(key))
        assertEquals(Offset(0f, 1f), scope.inputState.value.getDiscreteDirection(cross))
        scope.reset()
        assertEquals(InputState(), scope.inputState.value)
        assertEquals(InputEvent.DiscreteDirection(3, Offset.Unspecified), events.last())
    }

    @Test
    fun resetIsIdempotentAndDoesNotDuplicateReleases() {
        scope.updateSimulatedInput(setOf(key), InputState().setDigitalKey(key, true))
        scope.reset()
        val afterReset = events.toList()
        scope.reset()
        assertEquals(afterReset, events)
    }

    @Test
    fun quickPressAndReleaseAreDeliveredWithoutASnapshotObserver() {
        register(ButtonPointerHandler(key))
        touch(Pointer(1, Offset(50f, 50f)))
        touch()
        assertEquals<List<InputEvent>>(listOf(InputEvent.Button(1, true), InputEvent.Button(1, false)), events)
    }

    @Test
    fun reentrantResetIsQueuedAfterThePressCallback() {
        val delivered = mutableListOf<InputEvent>()
        val generator = InputEventsGenerator()
        scope.setInputStateListener { state ->
            val changes = generator.onInputStateChanged(state)
            if (state.getDigitalKey(key)) scope.reset()
            delivered.addAll(changes)
        }
        scope.updateSimulatedInput(setOf(key), InputState().setDigitalKey(key, true))
        assertEquals<List<InputEvent>>(listOf(InputEvent.Button(1, true), InputEvent.Button(1, false)), delivered)
    }

    @Test
    fun stateCanChangeWithoutAListenerAndNewListenerReceivesCurrentState() {
        scope.setInputStateListener(null)
        scope.updateSimulatedInput(setOf(key), InputState().setDigitalKey(key, true))
        var observed = InputState()
        scope.setInputStateListener { observed = it }
        assertTrue(observed.getDigitalKey(key))
    }
}
