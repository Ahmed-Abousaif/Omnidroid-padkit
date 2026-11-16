package gg.padkit.anchors

import androidx.compose.ui.geometry.Offset
import gg.padkit.utils.GeometryUtils.toRadians
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentList
import kotlin.math.cos
import kotlin.math.sin

/**
 * Build a grid, including staggered or slanted rows, in control-local coordinates.
 *
 * [origin] locates the first item. [columnStep] and [rowStep] are vectors between adjacent
 * items; they need not be perpendicular. Rotation is clockwise in screen coordinates,
 * around the control origin. Ragged/empty rows are allowed and preserve their row index.
 * IDs and layout dimensions have no platform or console-specific meaning.
 */
fun <T> buildGridAnchors(
    rows: List<List<T>>,
    buttonSize: Float,
    origin: Offset = Offset.Zero,
    columnStep: Offset = Offset(1f, 0f),
    rowStep: Offset = Offset(0.25f, 1f),
    rotationDegrees: Float = 0f,
): PersistentList<Anchor<T>> {
    require(buttonSize.isFinite() && buttonSize > 0f) { "Button size must be finite and positive" }
    require(rotationDegrees.isFinite()) { "Rotation must be finite" }
    require(listOf(origin, columnStep, rowStep).all { it.x.isFinite() && it.y.isFinite() }) {
        "Grid vectors must be finite"
    }
    val angle = rotationDegrees.toRadians()
    val cosine = cos(angle)
    val sine = sin(angle)
    return rows.flatMapIndexed { row, ids ->
        ids.mapIndexed { column, id ->
            val position = origin + columnStep * column.toFloat() + rowStep * row.toFloat()
            val rotated = Offset(position.x * cosine - position.y * sine, position.x * sine + position.y * cosine)
            Anchor(rotated, persistentSetOf(id), buttonSize)
        }
    }.toPersistentList()
}
