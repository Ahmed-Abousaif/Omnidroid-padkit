package gg.padkit.utils

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sign

/** Positive-up input/output. The horizontal axis wins exact ties in four-way mode. */
internal fun quantizeDirection(
    direction: Offset,
    allowDiagonals: Boolean,
    deadZone: Float,
): Offset {
    if (!direction.isSpecified || !direction.x.isFinite() || !direction.y.isFinite()) return Offset.Zero
    val (x, y) = direction
    if (direction == Offset.Zero || hypot(x, y) < deadZone) return Offset.Zero
    if (!allowDiagonals) {
        return if (abs(x) >= abs(y)) Offset(sign(x), 0f) else Offset(0f, sign(y))
    }
    val angle = atan2(y, x) * 180f / Constants.PI
    return when {
        angle in -22.5f..22.5f -> Offset(1f, 0f)
        angle in 22.5f..67.5f -> Offset(1f, 1f)
        angle in 67.5f..112.5f -> Offset(0f, 1f)
        angle in 112.5f..157.5f -> Offset(-1f, 1f)
        angle in -67.5f..-22.5f -> Offset(1f, -1f)
        angle in -112.5f..-67.5f -> Offset(0f, -1f)
        angle in -157.5f..-112.5f -> Offset(-1f, -1f)
        else -> Offset(-1f, 0f)
    }
}
