package gg.padkit.anchors

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import gg.padkit.layouts.radial.secondarydials.LayoutRadialSecondaryDialsScope
import kotlinx.collections.immutable.persistentSetOf
import kotlin.test.Test
import kotlin.test.assertFailsWith

class AnchorValidationTest {
    @Test
    fun rejectsNonFiniteCoordinatesAndInvalidSizes() {
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { Anchor(Offset(invalid, 0f), persistentSetOf(1), 0.2f) }
            assertFailsWith<IllegalArgumentException> { Anchor(Offset.Zero, persistentSetOf(1), invalid) }
        }
        assertFailsWith<IllegalArgumentException> { Anchor(Offset.Zero, persistentSetOf(1), -1f) }
    }

    @Test
    fun zeroSizedAnchorsAndDialsRemainValidWhileNonFiniteModifiersAreRejected() {
        Anchor(Offset.Zero, persistentSetOf(1), 0f)
        with(LayoutRadialSecondaryDialsScope()) {
            Modifier.radialScale(0f)
            assertFailsWith<IllegalArgumentException> { Modifier.radialScale(-1f) }
            assertFailsWith<IllegalArgumentException> { Modifier.radialScale(Float.NaN) }
            assertFailsWith<IllegalArgumentException> { Modifier.radialPosition(Float.POSITIVE_INFINITY) }
        }
    }
}
