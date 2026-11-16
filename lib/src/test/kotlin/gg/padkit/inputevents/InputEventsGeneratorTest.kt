package gg.padkit.inputevents

import androidx.compose.ui.geometry.Offset
import gg.padkit.ids.Id
import gg.padkit.inputstate.InputState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InputEventsGeneratorTest {
    @Test
    fun mixedBatchPreservesPressReleaseThenPreviousAndNewDirectionOrder() {
        val generator = InputEventsGenerator()
        generator.onInputStateChanged(
            InputState().setDigitalKey(Id.Key(1), true)
                .setContinuousDirection(Id.ContinuousDirection(7), Offset.Zero)
                .setContinuousDirection(Id.ContinuousDirection(9), Offset.Zero),
        )
        assertEquals(
            listOf(
                InputEvent.Button(2, true),
                InputEvent.Button(1, false),
                InputEvent.ContinuousDirection(7, Offset.Unspecified),
                InputEvent.ContinuousDirection(9, Offset(1f, 0f)),
                InputEvent.ContinuousDirection(3, Offset.Zero),
                InputEvent.DiscreteDirection(4, Offset(0f, 1f)),
            ),
            generator.onInputStateChanged(
                InputState().setDigitalKey(Id.Key(2), true)
                    .setContinuousDirection(Id.ContinuousDirection(9), Offset(1f, 0f))
                    .setContinuousDirection(Id.ContinuousDirection(3), Offset.Zero)
                    .setDiscreteDirection(Id.DiscreteDirection(4), Offset(0f, 1f)),
            ),
        )
    }

    @Test
    fun neutralAndUnspecifiedAreDifferentAndRemovalEmitsRelease() {
        val generator = InputEventsGenerator()
        val id = Id.ContinuousDirection(7)
        val held = InputState().setContinuousDirection(id, Offset.Zero)
        assertEquals(listOf(InputEvent.ContinuousDirection(7, Offset.Zero)), generator.onInputStateChanged(held))
        assertTrue(generator.onInputStateChanged(held).isEmpty())
        assertEquals(
            listOf(InputEvent.ContinuousDirection(7, Offset.Unspecified)),
            generator.onInputStateChanged(InputState()),
        )
    }

    @Test
    fun releasingAllSourcesReportsEveryKeyAndDirectionExactlyOnce() {
        val generator = InputEventsGenerator()
        generator.onInputStateChanged(
            InputState()
                .setDigitalKey(Id.Key(1), true)
                .setDigitalKey(Id.Key(2), true)
                .setContinuousDirection(Id.ContinuousDirection(3), Offset(1f, -1f))
                .setDiscreteDirection(Id.DiscreteDirection(4), Offset(-1f, 0f)),
        )
        assertEquals(
            setOf(
                InputEvent.Button(1, false),
                InputEvent.Button(2, false),
                InputEvent.ContinuousDirection(3, Offset.Unspecified),
                InputEvent.DiscreteDirection(4, Offset.Unspecified),
            ),
            generator.onInputStateChanged(InputState()).toSet(),
        )
        assertTrue(generator.onInputStateChanged(InputState()).isEmpty())
    }
}
