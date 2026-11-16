package gg.padkit

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import gg.padkit.anchors.Anchor
import gg.padkit.handlers.AnalogGestureHandler
import gg.padkit.handlers.AnalogPointerHandler
import gg.padkit.handlers.ButtonPointerHandler
import gg.padkit.handlers.FaceButtonsPointerHandler
import gg.padkit.handlers.Pointer
import gg.padkit.ids.Id
import gg.padkit.inputstate.InputState
import kotlinx.collections.immutable.persistentSetOf
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PadKitRoutingRegressionTest {
    private val scope = PadKitScope()

    private fun button(
        id: Int,
        rect: Rect,
    ): ButtonPointerHandler {
        val handler = ButtonPointerHandler(Id.Key(id))
        scope.registerHandler(handler)
        scope.updateHandlerPosition(handler, rect)
        return handler
    }

    @Test
    fun rectangularCornerFallsThroughToAnotherEligibleControl() {
        button(1, Rect(0f, 0f, 100f, 100f))
        button(2, Rect(0f, 0f, 50f, 50f))
        scope.handleInputEvent(sequenceOf(Pointer(1, Offset(25f, 5f))))
        assertFalse(scope.inputState.value.getDigitalKey(Id.Key(1)))
        assertTrue(scope.inputState.value.getDigitalKey(Id.Key(2)))
    }

    @Test
    fun overlappingEligibleControlsKeepRegistrationPriority() {
        button(1, Rect(0f, 0f, 100f, 100f))
        button(2, Rect(0f, 0f, 100f, 100f))
        scope.handleInputEvent(sequenceOf(Pointer(1, Offset(50f, 50f))))
        assertTrue(scope.inputState.value.getDigitalKey(Id.Key(1)))
        assertFalse(scope.inputState.value.getDigitalKey(Id.Key(2)))
    }

    @Test
    fun emptyFaceAnchorsAreInertAndDoNotCapture() {
        val result =
            FaceButtonsPointerHandler(emptyList(), true).handle(
                listOf(Pointer(1, Offset.Zero)),
                InputState(),
                emptySet(),
                Unit,
            )
        assertEquals(InputState(), result.inputState)
        assertTrue(result.trackedIds.isEmpty())
    }

    @Test
    fun collapsingCapturedBoundsReleasesImmediatelyAndResetsDoubleTap() {
        val stick = Id.ContinuousDirection(1)
        val click = Id.Key(2)
        val handler = AnalogPointerHandler(stick, click)
        scope.registerHandler(handler, AnalogGestureHandler.Data())
        val bounds = Rect(0f, 0f, 100f, 100f)
        scope.updateHandlerPosition(handler, bounds)
        scope.handleInputEvent(sequenceOf(Pointer(1, Offset(50f, 50f))))
        scope.handleInputEvent(sequenceOf(Pointer(1, Offset(80f, 50f))))
        scope.updateHandlerPosition(handler, Rect.Zero)
        assertEquals(InputState(), scope.inputState.value)
        scope.updateHandlerPosition(handler, bounds)
        scope.handleInputEvent(sequenceOf(Pointer(2, Offset(80f, 50f))))
        assertEquals(Offset.Zero, scope.inputState.value.getContinuousDirection(stick))
        assertFalse(scope.inputState.value.getDigitalKey(click))
    }

    @Test
    fun removingACapturingHandlerDoesNotLeaveStaleOwnership() {
        val handler = AnalogPointerHandler(Id.ContinuousDirection(1), null)
        scope.registerHandler(handler, AnalogGestureHandler.Data())
        scope.updateHandlerPosition(handler, Rect(0f, 0f, 100f, 100f))
        button(2, Rect(200f, 0f, 300f, 100f))
        scope.handleInputEvent(sequenceOf(Pointer(7, Offset(50f, 50f))))
        scope.unregisterHandler(handler)
        scope.handleInputEvent(sequenceOf(Pointer(7, Offset(250f, 50f))))
        assertTrue(scope.inputState.value.getDigitalKey(Id.Key(2)))
        assertEquals(Offset.Unspecified, scope.inputState.value.getContinuousDirection(Id.ContinuousDirection(1)))
    }

    @Test
    fun equalCaptureSetsSurviveReorderingDragOutAndIndependentReleases() {
        val face =
            FaceButtonsPointerHandler(
                listOf(Anchor(Offset.Zero, persistentSetOf(Id.Key(1)), 0.25f)),
                true,
            )
        scope.registerHandler(face)
        scope.updateHandlerPosition(face, Rect(0f, 0f, 100f, 100f))
        button(2, Rect(200f, 0f, 300f, 100f))
        scope.handleInputEvent(sequenceOf(Pointer(1, Offset(40f, 50f)), Pointer(2, Offset(60f, 50f))))
        scope.handleInputEvent(sequenceOf(Pointer(2, Offset(250f, 50f)), Pointer(1, Offset(250f, 50f))))
        assertTrue(scope.inputState.value.getDigitalKey(Id.Key(1)))
        assertFalse(scope.inputState.value.getDigitalKey(Id.Key(2)))
        scope.handleInputEvent(sequenceOf(Pointer(1, Offset(250f, 50f)), Pointer(3, Offset(250f, 50f))))
        assertTrue(scope.inputState.value.getDigitalKey(Id.Key(1)))
        assertTrue(scope.inputState.value.getDigitalKey(Id.Key(2)))
        scope.handleInputEvent(sequenceOf(Pointer(3, Offset(250f, 50f))))
        assertFalse(scope.inputState.value.getDigitalKey(Id.Key(1)))
        assertTrue(scope.inputState.value.getDigitalKey(Id.Key(2)))
        scope.resetTouchInput()
        assertEquals(InputState(), scope.inputState.value)
    }

    @Test
    fun previouslyDeliveredSnapshotsRemainImmutableWhenBuffersAreReused() {
        button(1, Rect(0f, 0f, 100f, 100f))
        val snapshots = mutableListOf<InputState>()
        scope.setInputStateListener { snapshots += it }
        scope.handleInputEvent(sequenceOf(Pointer(1, Offset(50f, 50f))))
        val held = snapshots.last()
        repeat(20) {
            scope.handleInputEvent(sequenceOf(Pointer(1, Offset(55f + it, 50f))))
        }
        scope.handleInputEvent(emptySequence())
        scope.reset()
        assertEquals(3, snapshots.size)
        assertTrue(held.getDigitalKey(Id.Key(1)))
        assertEquals(InputState(), snapshots.first())
        assertEquals(InputState(), snapshots.last())
    }

    @Test
    fun simulationOwnsACopyOfMutableIdSelections() {
        val selection = mutableSetOf<Id>(Id.Key(1))
        val state = InputState().setDigitalKey(Id.Key(1), true)
        scope.updateSimulatedInput(selection, state)
        selection.clear()
        assertTrue(scope.isSimulated(Id.Key(1)))
        scope.updateSimulatedInput(selection, state)
        assertFalse(scope.isSimulated(Id.Key(1)))
        assertEquals(InputState(), scope.inputState.value)
    }

    @Test
    fun seededMultifingerReplayMatchesIndependentKeyUnionAndOverrideModel() {
        repeat(10) { index -> button(index % 3, Rect(index * 120f, 0f, index * 120f + 100f, 100f)) }
        val random = Random(10724)
        repeat(2_000) { frame ->
            val positions = List(random.nextInt(11)) { random.nextInt(10) }
            val pointers = positions.mapIndexed { index, position -> Pointer(index.toLong(), Offset(position * 120f + 50f, 50f)) }
            val override = frame % 3 == 0
            val simulated = InputState().setDigitalKey(Id.Key(1), frame % 2 == 0)
            scope.updateSimulatedInput(if (override) setOf(Id.Key(1)) else emptySet(), simulated)
            scope.handleInputEvent(pointers.asSequence())
            val expected = positions.map { it % 3 }.toMutableSet()
            if (override) {
                if (frame % 2 == 0) expected.add(1) else expected.remove(1)
            }
            for (id in 0..2) assertEquals(id in expected, scope.inputState.value.getDigitalKey(Id.Key(id)), "frame=$frame, key=$id")
        }
        scope.reset()
        assertEquals(InputState(), scope.inputState.value)
    }
}
