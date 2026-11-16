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
import gg.padkit.inputstate.forEachDirectionChange
import gg.padkit.inputstate.forEachKeyChange
import kotlin.math.abs

/** UI-thread transition tracker. PadKit queues reentrant input until the current batch is delivered. */
internal class InputHapticGenerator(private var previousInputState: InputState) {
    private val pendingEffects = ArrayList<HapticEffect>()

    fun onInputStateChanged(
        current: InputState,
        feedback: HapticFeedback?,
        type: HapticFeedbackType,
        settings: HapticFeedbackSettings,
    ) {
        val previous = previousInputState
        // Muted updates still advance the tracker; changing preferences must not replay a press.
        previousInputState = current
        if (feedback == null || type == HapticFeedbackType.NONE || settings.intensity == 0f) return
        pendingEffects.clear()

        var presses = 0
        var releases = 0
        forEachKeyChange(previous.digitalKeys, current.digitalKeys) { id, pressed ->
            if (Id.Key(id) !in settings.excludedControlIds) {
                if (settings.coalesceEffects) {
                    if (pressed) presses++ else releases++
                } else {
                    record(if (pressed) HapticEffect.PRESS else HapticEffect.RELEASE, type, false)
                }
            }
        }
        if (settings.coalesceEffects && presses + releases > 0) {
            record(if (presses >= releases) HapticEffect.PRESS else HapticEffect.RELEASE, type, true)
        }

        forEachDirectionChange(previous.discreteDirections, current.discreteDirections) { id, before, after ->
            if (Id.DiscreteDirection(id) !in settings.excludedControlIds) {
                val effect =
                    if (settings.discreteDirections == DirectionHapticFeedback.ACTIVATION) {
                        transition(before.isActive(), after.isActive())
                    } else {
                        val oldCount = before.axisCount()
                        val newCount = after.axisCount()
                        when {
                            oldCount == 0 && newCount == 0 -> null
                            newCount >= oldCount -> HapticEffect.PRESS
                            else -> HapticEffect.RELEASE
                        }
                    }
                record(effect, type, settings.coalesceEffects)
            }
        }
        if (settings.continuousDirections) {
            forEachDirectionChange(previous.continuousDirections, current.continuousDirections) { id, before, after ->
                if (Id.ContinuousDirection(id) !in settings.excludedControlIds) {
                    record(transition(before.isFiniteDirection(), after.isFiniteDirection()), type, settings.coalesceEffects)
                }
            }
        }

        // Finish classification before invoking an adapter: callbacks cannot change this batch's policy.
        var index = 0
        while (index < pendingEffects.size) feedback.generate(pendingEffects[index++], settings.intensity)
    }

    private fun record(
        effect: HapticEffect?,
        type: HapticFeedbackType,
        coalesce: Boolean,
    ) {
        if (effect == null || effect == HapticEffect.RELEASE && type != HapticFeedbackType.PRESS_RELEASE) return
        if (!coalesce || pendingEffects.isEmpty()) pendingEffects.add(effect)
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
