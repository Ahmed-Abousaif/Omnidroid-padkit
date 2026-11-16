package gg.padkit.handlers

import androidx.compose.ui.geometry.Offset
import gg.padkit.anchors.Anchor
import gg.padkit.ids.Id
import gg.padkit.inputstate.InputState
import kotlinx.collections.immutable.persistentSetOf
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FaceButtonsPointerHandlerTest {
    private val left = Id.Key(1)
    private val right = Id.Key(2)
    private val anchors =
        listOf(
            Anchor(Offset(-0.7f, 0f), persistentSetOf(left), 0.25f),
            Anchor(Offset(0.7f, 0f), persistentSetOf(right), 0.25f),
            Anchor(Offset.Zero, persistentSetOf(left, right), 0.1f),
        )

    @Test
    fun seededFramesPreserveKeyUnionCaptureAndIncomingContributions() {
        for (capture in listOf(false, true)) {
            val handler = FaceButtonsPointerHandler(anchors, capture)
            val random = Random(724)
            var tracked = emptySet<Long>()
            repeat(2_000) { frame ->
                val pointers =
                    List(random.nextInt(8)) {
                        Pointer(random.nextLong(4), Offset(random.nextFloat() * 4f - 2f, random.nextFloat() * 4f - 2f))
                    }
                val incoming =
                    if (frame % 3 == 0) {
                        InputState().setDigitalKey(left, true).setDigitalKey(Id.Key(9), true)
                            .setContinuousDirection(Id.ContinuousDirection(3), Offset(0.2f, -0.5f))
                    } else {
                        InputState.Empty
                    }
                // This oracle unions incoming keys and current touches without retaining previous frames.
                val expected = incoming.digitalKeys.toMutableSet()
                for (pointer in pointers) {
                    val point = Offset(pointer.position.x.coerceIn(-1f, 1f), pointer.position.y.coerceIn(-1f, 1f))
                    val closest = anchors.minBy { maxOf((point - it.position).getDistance() - it.size, 0f) }
                    expected.addAll(closest.buttons.map { it.value })
                }
                val result = handler.handle(pointers, incoming, tracked, Unit)
                assertEquals<Set<Int>>(expected, result.inputState.digitalKeys, "frame=$frame, capture=$capture")
                assertEquals(incoming.continuousDirections, result.inputState.continuousDirections)
                assertEquals(if (capture) pointers.map { it.pointerId }.toSet() else emptySet(), result.trackedIds)
                tracked = result.trackedIds
            }
        }
    }

    @Test
    fun movingAndReleasingTouchesNeverMutatesEarlierSnapshots() {
        val handler = FaceButtonsPointerHandler(anchors, true)
        val first = handler.handle(listOf(Pointer(1, Offset(-0.7f, 0f))), InputState.Empty, emptySet(), Unit)
        val second = handler.handle(listOf(Pointer(1, Offset(0.7f, 0f))), InputState.Empty, first.trackedIds, Unit)
        handler.reset(Unit)
        val released = handler.handle(emptyList(), InputState.Empty, emptySet(), Unit)
        assertTrue(first.inputState.getDigitalKey(left))
        assertFalse(first.inputState.getDigitalKey(right))
        assertFalse(second.inputState.getDigitalKey(left))
        assertTrue(second.inputState.getDigitalKey(right))
        assertEquals(setOf(1L), first.trackedIds)
        assertEquals(InputState.Empty, released.inputState)
        assertTrue(released.trackedIds.isEmpty())
    }

    @Test
    fun tiedAnchorsKeepFirstRegistrationPriority() {
        val handler =
            FaceButtonsPointerHandler(
                listOf(
                    Anchor(Offset(-0.5f, 0f), persistentSetOf(left), 0.1f),
                    Anchor(Offset(0.5f, 0f), persistentSetOf(right), 0.1f),
                ),
                false,
            )
        val result = handler.handle(listOf(Pointer(1, Offset.Zero)), InputState.Empty, emptySet(), Unit)
        assertTrue(result.inputState.getDigitalKey(left))
        assertFalse(result.inputState.getDigitalKey(right))
    }
}
