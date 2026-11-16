package gg.padkit.config

import gg.padkit.ids.Id

/** How a discrete direction produces feedback while it remains held. */
enum class DirectionHapticFeedback {
    /** Feedback for direction changes as well as activation/release. */
    CHANGE,

    /** Feedback only when entering or leaving a non-neutral direction. */
    ACTIVATION,
}

/**
 * Portable feedback preferences. Intensity is normalized, with zero explicitly silent.
 * Devices without amplitude control use their default amplitude for nonzero requests.
 * Feedback follows logical input, including simulated input. Excluded IDs never request feedback.
 */
data class HapticFeedbackSettings(
    val intensity: Float = 1f,
    val discreteDirections: DirectionHapticFeedback = DirectionHapticFeedback.CHANGE,
    val continuousDirections: Boolean = true,
    val coalesceEffects: Boolean = true,
    val excludedControlIds: Set<Id> = emptySet(),
) {
    init {
        require(intensity.isFinite() && intensity in 0f..1f) { "Haptic intensity must be finite and between 0 and 1" }
    }
}
