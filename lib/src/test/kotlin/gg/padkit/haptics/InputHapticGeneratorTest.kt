package gg.padkit.haptics

import androidx.compose.ui.geometry.Offset
import gg.padkit.config.DirectionHapticFeedback
import gg.padkit.config.HapticFeedbackSettings
import gg.padkit.config.HapticFeedbackType
import gg.padkit.ids.Id
import gg.padkit.inputstate.InputState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InputHapticGeneratorTest {
    @Test
    fun adapterChangesCannotAlterAlreadyClassifiedEffects() {
        val exclusions = mutableSetOf<Id>()
        val output = mutableListOf<HapticEffect>()
        val feedback =
            HapticFeedback { effect, _ ->
                output += effect
                exclusions += Id.Key(2)
            }
        InputHapticGenerator(InputState()).onInputStateChanged(
            InputState().setDigitalKey(Id.Key(1), true).setDigitalKey(Id.Key(2), true),
            feedback,
            HapticFeedbackType.PRESS_RELEASE,
            HapticFeedbackSettings(excludedControlIds = exclusions, coalesceEffects = false),
        )
        assertEquals(listOf(HapticEffect.PRESS, HapticEffect.PRESS), output)
    }

    private val effects = mutableListOf<Pair<HapticEffect, Float>>()
    private val output = HapticFeedback { effect, intensity -> effects += effect to intensity }
    private val generator = InputHapticGenerator(InputState())
    private val hostPolicy =
        HapticFeedbackSettings(
            intensity = 0.5f,
            discreteDirections = DirectionHapticFeedback.ACTIVATION,
            continuousDirections = false,
            coalesceEffects = false,
            excludedControlIds = setOf(Id.Key(99)),
        )

    private fun update(
        state: InputState,
        settings: HapticFeedbackSettings = hostPolicy,
        type: HapticFeedbackType = HapticFeedbackType.PRESS_RELEASE,
    ) {
        generator.onInputStateChanged(state, output, type, settings)
    }

    @Test
    fun intensityMustBeNormalizedAndFinite() {
        listOf(-0.1f, 1.1f, Float.NaN, Float.POSITIVE_INFINITY).forEach { value ->
            assertFailsWith<IllegalArgumentException> { HapticFeedbackSettings(intensity = value) }
        }
    }

    @Test
    fun pressReleaseUsesLatestStrengthAndIgnoresExcludedKeys() {
        update(InputState().setDigitalKey(Id.Key(1), true).setDigitalKey(Id.Key(99), true))
        update(InputState(), hostPolicy.copy(intensity = 0.2f))
        assertEquals(listOf(HapticEffect.PRESS to 0.5f, HapticEffect.RELEASE to 0.2f), effects)
    }

    @Test
    fun activationPolicyDoesNotPulseOnDirectionChangesOrOrdinarySticks() {
        val id = Id.DiscreteDirection(1)
        update(InputState().setDiscreteDirection(id, Offset.Zero))
        update(InputState().setDiscreteDirection(id, Offset(1f, 0f)))
        update(InputState().setDiscreteDirection(id, Offset(1f, 1f)))
        update(InputState().setDiscreteDirection(id, Offset(-1f, 0f)))
        update(InputState().setContinuousDirection(Id.ContinuousDirection(2), Offset.Zero))
        update(InputState())
        assertEquals(listOf(HapticEffect.PRESS to 0.5f, HapticEffect.RELEASE to 0.5f), effects)
    }

    @Test
    fun simultaneousControlsCanRequestIndependentEffectsOrCoalesce() {
        val state =
            InputState().setDigitalKey(Id.Key(1), true).setDigitalKey(Id.Key(2), true)
                .setDiscreteDirection(Id.DiscreteDirection(3), Offset(0f, 1f))
        update(state)
        assertEquals(3, effects.size)
        effects.clear()
        update(InputState(), hostPolicy.copy(coalesceEffects = true))
        assertEquals(listOf(HapticEffect.RELEASE to 0.5f), effects)
    }

    @Test
    fun replacingButtonsPreservesInputEventPressThenReleaseOrder() {
        update(InputState().setDigitalKey(Id.Key(1), true))
        effects.clear()
        update(InputState().setDigitalKey(Id.Key(2), true))
        assertEquals(listOf(HapticEffect.PRESS to 0.5f, HapticEffect.RELEASE to 0.5f), effects)
    }

    @Test
    fun modeAndZeroMuteStillTrackStateWithoutReplayingPresses() {
        val held = InputState().setDigitalKey(Id.Key(1), true)
        update(held, type = HapticFeedbackType.NONE)
        update(held)
        update(InputState(), hostPolicy.copy(intensity = 0f))
        update(InputState())
        assertEquals(emptyList(), effects)
        update(held, type = HapticFeedbackType.PRESS)
        update(InputState(), type = HapticFeedbackType.PRESS)
        assertEquals(listOf(HapticEffect.PRESS to 0.5f), effects)
    }

    @Test
    fun defaultPolicyIncludesAnalogActivationAndDiscreteChanges() {
        val settings = HapticFeedbackSettings()
        update(InputState().setContinuousDirection(Id.ContinuousDirection(1), Offset.Zero), settings)
        update(InputState(), settings)
        update(InputState().setDiscreteDirection(Id.DiscreteDirection(2), Offset(1f, 0f)), settings)
        update(InputState().setDiscreteDirection(Id.DiscreteDirection(2), Offset(0f, 1f)), settings)
        assertEquals(listOf(HapticEffect.PRESS, HapticEffect.RELEASE, HapticEffect.PRESS, HapticEffect.PRESS), effects.map { it.first })
    }

    @Test
    fun logicalSimulationIsClassifiedLikeOtherInputAndCanBeExcluded() {
        val id = Id.DiscreteDirection(1)
        val simulated = InputState().setDiscreteDirection(id, Offset(1f, 0f))
        update(simulated, hostPolicy.copy(excludedControlIds = setOf(id)))
        update(simulated)
        update(InputState())
        assertEquals(listOf(HapticEffect.RELEASE to 0.5f), effects)
    }

    @Test
    fun pressModeDoesNotLetSimultaneousReleaseHideAnotherPress() {
        update(InputState().setDigitalKey(Id.Key(1), true))
        effects.clear()
        update(
            InputState().setDiscreteDirection(Id.DiscreteDirection(2), Offset(1f, 0f)),
            hostPolicy.copy(coalesceEffects = true),
            HapticFeedbackType.PRESS,
        )
        assertEquals(listOf(HapticEffect.PRESS to 0.5f), effects)
    }
}
