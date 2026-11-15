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

package gg.padkit.haptics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import gg.padkit.config.DirectionHapticFeedback
import gg.padkit.config.HapticFeedbackSettings
import gg.padkit.config.HapticFeedbackType
import gg.padkit.ids.Id
import gg.padkit.inputstate.InputState
import kotlin.math.abs

/** Tracks transitions even while muted or excluded, so preference changes cannot replay a press. */
internal class InputHapticGenerator(private var previousInputState: InputState) {
    fun onInputStateChanged(
        current: InputState,
        feedback: HapticFeedback?,
        type: HapticFeedbackType,
        settings: HapticFeedbackSettings,
    ) {
        val previous = previousInputState
        previousInputState = current
        if (feedback == null || type == HapticFeedbackType.NONE || settings.intensity == 0f) return

        val effects = mutableListOf<HapticEffect>()
        val keyEffects =
            (previous.digitalKeys + current.digitalKeys)
                .filter { Id.Key(it) !in settings.excludedControlIds }
                .mapNotNull { id ->
                    transition(id in previous.digitalKeys, id in current.digitalKeys)
                }
        if (settings.coalesceEffects) {
            if (keyEffects.isNotEmpty()) {
                effects +=
                    if (current.digitalKeys.count { Id.Key(it) !in settings.excludedControlIds } >=
                        previous.digitalKeys.count { Id.Key(it) !in settings.excludedControlIds }
                    ) {
                        HapticEffect.PRESS
                    } else {
                        HapticEffect.RELEASE
                    }
            }
        } else {
            effects += keyEffects
        }

        val discreteEffects =
            (previous.discreteDirections.keys + current.discreteDirections.keys)
                .filter { Id.DiscreteDirection(it) !in settings.excludedControlIds }
                .mapNotNull { id ->
                    val before = previous.discreteDirections[id] ?: Offset.Unspecified
                    val after = current.discreteDirections[id] ?: Offset.Unspecified
                    if (settings.discreteDirections == DirectionHapticFeedback.ACTIVATION) {
                        transition(before.isActive(), after.isActive())
                    } else {
                        val oldCount = before.axisCount()
                        val newCount = after.axisCount()
                        when {
                            before == after || oldCount == 0 && newCount == 0 -> null
                            newCount >= oldCount -> HapticEffect.PRESS
                            else -> HapticEffect.RELEASE
                        }
                    }
                }
        effects += discreteEffects

        if (settings.continuousDirections) {
            effects +=
                (previous.continuousDirections.keys + current.continuousDirections.keys)
                    .filter { Id.ContinuousDirection(it) !in settings.excludedControlIds }
                    .mapNotNull { id ->
                        transition(
                            previous.continuousDirections[id]?.isFiniteDirection() == true,
                            current.continuousDirections[id]?.isFiniteDirection() == true,
                        )
                    }
        }

        val permitted = effects.filter { it == HapticEffect.PRESS || type == HapticFeedbackType.PRESS_RELEASE }
        // Coalescing uses category priority: buttons, discrete controls, then analogs.
        // A disallowed release must not hide a simultaneous permitted press.
        (if (settings.coalesceEffects) permitted.take(1) else permitted)
            .forEach { feedback.generate(it, settings.intensity) }
    }

    private fun transition(
        before: Boolean,
        after: Boolean,
    ): HapticEffect? =
        when {
            !before && after -> HapticEffect.PRESS
            before && !after -> HapticEffect.RELEASE
            else -> null
        }

    private fun Offset.isFiniteDirection() = isSpecified && x.isFinite() && y.isFinite()

    private fun Offset.isActive() = isFiniteDirection() && (abs(x) > 0.01f || abs(y) > 0.01f)

    private fun Offset.axisCount(): Int = if (!isFiniteDirection()) 0 else (if (abs(x) > 0.5f) 1 else 0) + (if (abs(y) > 0.5f) 1 else 0)
}
