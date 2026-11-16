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

// Keep the existing Android JVM entry points compatible with compiled callers.
@file:JvmName("HapticGenerator_androidKt")

package gg.padkit.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlin.math.roundToInt

/** Create an Android vibration adapter for Omnidroid actions without constructing a Compose control. */
fun createAndroidHapticFeedback(context: Context): HapticFeedback = AndroidHapticGenerator(context.applicationContext)

internal class AndroidHapticGenerator(applicationContext: Context) : HapticFeedback {
    private val vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            applicationContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            applicationContext.getSystemService(Vibrator::class.java)
        }

    @Suppress("DEPRECATION")
    override fun generate(
        type: HapticEffect,
        intensity: Float,
    ) {
        if (!intensity.isFinite() || intensity <= 0f) return
        val output = vibrator ?: return
        if (!output.hasVibrator()) return
        val strength = intensity.coerceAtMost(1f)
        val duration = if (type == HapticEffect.PRESS) 30L else 15L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val amplitude =
                if (output.hasAmplitudeControl()) {
                    (strength * if (type == HapticEffect.PRESS) 255 else 180).roundToInt().coerceIn(1, 255)
                } else {
                    VibrationEffect.DEFAULT_AMPLITUDE
                }
            output.vibrate(VibrationEffect.createOneShot(duration, amplitude))
        } else {
            output.vibrate(duration)
        }
    }
}

@Composable
fun rememberHapticFeedback(): HapticFeedback {
    val context = LocalContext.current.applicationContext
    return remember(context) { createAndroidHapticFeedback(context) }
}
