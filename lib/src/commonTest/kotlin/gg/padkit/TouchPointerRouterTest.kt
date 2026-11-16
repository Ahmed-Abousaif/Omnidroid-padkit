package gg.padkit

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import gg.padkit.handlers.ButtonPointerHandler
import gg.padkit.handlers.Pointer
import gg.padkit.ids.Id
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TouchPointerRouterTest {
    private val scope = PadKitScope()
    private val router = TouchPointerRouter()
    private val key = Id.Key(1)

    init {
        val handler = ButtonPointerHandler(key)
        scope.registerHandler(handler)
        scope.updateHandlerPosition(handler, Rect(0f, 100f, 100f, 200f))
    }

    private fun route(vararg pointers: Pointer, enabled: Boolean = true): TouchPointerRouter.Result {
        val result = router.route(pointers.toList(), enabled, scope::hitsControl)
        scope.handleInputEvent(result.controls.asSequence())
        return result
    }

    @Test
    fun buttonAndStylusRemainIndependentInEitherPressOrder() {
        assertTrue(route(Pointer(1, Offset(50f, 150f))).unhandled.isEmpty())
        val stylus = Pointer(2, Offset(50f, 20f))
        assertEquals(listOf(UnhandledPointer(2, stylus.position, isNew = true)), route(Pointer(1, Offset(50f, 150f)), stylus).unhandled)
        assertTrue(scope.inputState.value.getDigitalKey(key))
        route(stylus)
        assertFalse(scope.inputState.value.getDigitalKey(key))
        route(stylus, Pointer(3, Offset(50f, 150f)))
        assertTrue(scope.inputState.value.getDigitalKey(key))
        assertEquals(emptyList(), route().unhandled)
    }

    @Test
    fun stylusCanCrossAControlWithoutPressingIt() {
        route(Pointer(1, Offset(50f, 20f)))
        assertEquals(listOf(UnhandledPointer(1, Offset(50f, 150f))), route(Pointer(1, Offset(50f, 150f))).unhandled)
        assertFalse(scope.inputState.value.getDigitalKey(key))
    }

    @Test
    fun buttonCannotBecomeStylusByMovingOutsideItsBounds() {
        route(Pointer(1, Offset(50f, 150f)))
        assertTrue(route(Pointer(1, Offset(50f, 20f))).unhandled.isEmpty())
        route()
        assertEquals(1, route(Pointer(1, Offset(50f, 20f))).unhandled.size)
    }

    @Test
    fun unobstructedCornerOfControlBoundsRemainsTouchable() {
        assertEquals(1, route(Pointer(1, Offset(1f, 101f))).unhandled.size)
        assertFalse(scope.inputState.value.getDigitalKey(key))
    }

    @Test
    fun defaultRoutingPreservesSlideIntoControls() {
        route(Pointer(1, Offset(50f, 20f)), enabled = false)
        route(Pointer(1, Offset(50f, 150f)), enabled = false)
        assertTrue(scope.inputState.value.getDigitalKey(key))
    }
}
