package gg.padkit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import gg.padkit.config.HapticFeedbackType
import gg.padkit.controls.AnalogControlLayout
import gg.padkit.controls.ControlButton
import gg.padkit.controls.ControlFaceButtons
import gg.padkit.ids.Id
import gg.padkit.inputstate.InputState
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 35])
class PadKitGeometryComposeTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun mergedManifestKeepsAndroid7Minimum() {
        assertEquals(24, RuntimeEnvironment.getApplication().applicationInfo.minSdkVersion)
    }

    @Test
    fun emptyFaceClusterDoesNotBlockAnUnderlyingButton() {
        var state = InputState()
        rule.setContent {
            PadKit(onInputStateUpdated = { state = it }, hapticFeedbackType = HapticFeedbackType.NONE) {
                Box(Modifier.size(100.dp)) {
                    ControlButton(Modifier.fillMaxSize(), Id.Key(1))
                    ControlFaceButtons(Modifier.fillMaxSize().testTag("empty"), ids = persistentListOf())
                }
            }
        }
        rule.onNodeWithTag("empty").performTouchInput { down(center) }
        rule.runOnIdle { assertTrue(state.getDigitalKey(Id.Key(1))) }
        rule.onNodeWithTag("empty").performTouchInput { up() }
        rule.runOnIdle { assertFalse(state.getDigitalKey(Id.Key(1))) }
    }

    @Test
    fun thumbPlacementPreservesSizeRangeAndReleaseCenter() {
        val position = mutableStateOf(Offset.Zero)
        val pressed = mutableStateOf(true)
        rule.setContent {
            AnalogControlLayout(
                modifier = Modifier.size(200.dp),
                foregroundSize = 0.66f,
                positionState = position,
                pressedState = pressed,
                background = {},
                foreground = { Box(Modifier.fillMaxSize().testTag("thumb")) },
            )
        }
        val centered = rule.onNodeWithTag("thumb").getUnclippedBoundsInRoot()
        rule.runOnIdle { position.value = Offset(1f, 0f) }
        val right = rule.onNodeWithTag("thumb").getUnclippedBoundsInRoot()
        assertEquals(50f, (right.left - centered.left).value, 0.5f)
        assertEquals(centered.right - centered.left, right.right - right.left)
        assertEquals(centered.bottom - centered.top, right.bottom - right.top)
        rule.runOnIdle { position.value = Offset(0f, 1f) }
        val up = rule.onNodeWithTag("thumb").getUnclippedBoundsInRoot()
        assertEquals(-50f, (up.top - centered.top).value, 0.5f)
        rule.runOnIdle { position.value = Offset.Unspecified }
        assertEquals(centered, rule.onNodeWithTag("thumb").getUnclippedBoundsInRoot())
    }
}
