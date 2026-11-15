package gg.padkit.anchors

import androidx.compose.ui.geometry.Offset
import gg.padkit.ids.Id
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GridAnchorsTest {
    @Test
    fun raggedStaggeredRowsPreserveIdsOrderSizeAndSpacing() {
        val ids = listOf(listOf(Id.Key(1), Id.Key(2)), emptyList(), listOf(Id.Key(3)))
        val anchors = buildGridAnchors(ids, 0.3f, Offset(-1f, -1f), Offset(0.8f, 0.1f), Offset(-0.2f, 0.7f))
        assertEquals(listOf(Id.Key(1), Id.Key(2), Id.Key(3)), anchors.map { it.buttons.single() })
        assertEquals(Offset(-1f, -1f), anchors[0].position)
        assertEquals(Offset(-0.2f, -0.9f).x, anchors[1].position.x, 0.0001f)
        assertEquals(-1.4f, anchors[2].position.x, 0.0001f)
        assertEquals(0.4f, anchors[2].position.y, 0.0001f)
        anchors.forEach { assertEquals(0.3f, it.size) }
    }

    @Test
    fun clockwiseRotationIsAroundControlOrigin() {
        val anchor = buildGridAnchors(listOf(listOf("A")), 0.2f, origin = Offset(2f, 0f), rotationDegrees = 90f).single()
        assertEquals(0f, anchor.position.x, 0.0001f)
        assertEquals(2f, anchor.position.y, 0.0001f)
    }

    @Test
    fun invalidGeometryIsRejectedAndEmptyInputIsEmpty() {
        assertEquals(emptyList(), buildGridAnchors<Int>(emptyList(), 1f))
        assertFailsWith<IllegalArgumentException> { buildGridAnchors<Int>(emptyList(), Float.NaN) }
        assertFailsWith<IllegalArgumentException> { buildGridAnchors<Int>(emptyList(), 1f, rotationDegrees = Float.POSITIVE_INFINITY) }
        assertFailsWith<IllegalArgumentException> { buildGridAnchors<Int>(emptyList(), 1f, rowStep = Offset.Unspecified) }
    }
}
