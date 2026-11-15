# Omnidroid-padkit

Omnidroid's fork of [Swordfish90/PadKit](https://github.com/Swordfish90/PadKit), maintained here for enhancements to Omnidroid's virtual gamepads and touch controls.

The documentation below describes the upstream API and published artifact.

## PadKit

PadKit is a Compose Multiplatform library that allows developers to create custom virtual gamepads for games and applications.
This project is a continuation of the original [JamPadCompose](https://github.com/piepacker/JamPadCompose) by Jam.gg.

### Controls

The following controls are currently supported:
* ControlAnalog
* ControlButton
* ControlCross
* ControlFaceButtons

### Layouts

Controls can be arranged with any composable components, but PadKit also includes the `LayoutRadial` which places a primary dial in the center, and arranges secondary dials in a circle around it.

### Usage

Include the library in your project. Check latest tag for version:

```
implementation("io.github.swordfish90:padkit:x.y.z")
```

Here's a how you can use PadKit to create a very simple gamepad layout.

```kotlin
@Composable
fun SampleGamePad() {
    PadKit(
        hapticFeedbackType = HapticFeedbackType.PRESS,
        modifier = Modifier.fillMaxSize(),
        onInputStateUpdated = {},
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            LayoutRadial(
                modifier = Modifier.padding(8.dp).weight(1f, fill = false),
                primaryDial = {
                    ControlCross(
                        modifier = Modifier.fillMaxSize(),
                        id = Id.DiscreteDirection(0),
                    )
                },
                secondaryDials = {
                    ControlButton(
                        modifier = Modifier.radialPosition(-60f),
                        id = Id.Key(0)
                    )
                },
            )
            LayoutRadial(
                modifier = Modifier.padding(8.dp).weight(1f, fill = false),
                primaryDial = {
                    ControlFaceButtons(
                        modifier = Modifier.fillMaxSize(),
                        ids =
                            listOf(1, 2)
                                .map { Id.Key(it) }
                                .toPersistentList(),
                    )
                },
                secondaryDials = {
                    ControlButton(
                        modifier = Modifier.radialPosition(-120f),
                        id = Id.Key(3)
                    )
                },
            )
        }
    }
}
```

### Input lifecycle

Each control owns its touch contribution. Removing or remapping a held control releases its old input immediately while preserving other held controls, including controls sharing the same key. Cancelling pointer input clears touch capture and analog gesture state. Removing the entire `PadKit` composition also releases simulated input.

Simulated control IDs override the current touch state for those IDs. Changing the ID set takes effect even when the simulated state is unchanged. Removing an override restores the current touch contribution, including a finger that is still held.

Input callbacks are delivered synchronously in transition order, including releases during disposal. Keep callbacks short. Reentrant state changes are queued after the transition currently being delivered.

Android haptics submit effects directly to the platform vibrator service and own no background thread or coroutine scope. `HapticFeedbackType.NONE` does not initialize a platform haptics generator.

Engine regression tests live in `lib/src/commonTest/kotlin`. Omnidroid's source wrapper runs these together with Android Compose lifecycle tests under Robolectric.

### Configurable control feedback

Pass `hapticFeedbackSettings = HapticFeedbackSettings(intensity = 0.5f)` to request normalized strength. Zero and `HapticFeedbackType.NONE` are silent and avoid platform adapter initialization. Android checks amplitude control on API 26+, uses adjustable 30 ms press / 15 ms release effects, and retains a duration-only fallback on older devices. This replaces the default predefined Android click/tick effects; hardware without amplitude control uses its default strength. iOS scales its existing strengths; desktop/browser remain silent.

`HapticFeedbackSettings` also selects `DirectionHapticFeedback.CHANGE` or `ACTIVATION`, analog activation feedback, effect coalescing, and excluded typed IDs. Feedback follows logical input, including simulations. Mode/intensity/adapter changes affect subsequent transitions without replaying held inputs. Muted transitions still update the tracker.

Supply `hapticFeedback = HapticFeedback { effect, intensity -> ... }` for custom device routing. `rememberHapticFeedback()` provides the platform adapter to composable callers; Android hosts can use `createAndroidHapticFeedback(context)` for their own actions. Keep preference persistence, action success, and rumble/output coordination in the host.

### Discrete thumbstick output

`ControlDirectionalAnalog(id = Id.DiscreteDirection(1), allowDiagonals = false)` renders a smooth analog-style thumbstick and emits only discrete-direction events. `deadZone` defaults to 0.25 and accepts finite values from 0 to 1. Four-way mode selects the dominant axis, with horizontal ties. Changing either policy while held recomputes output immediately while preserving pointer capture and smooth touch visuals. Simulated discrete input overrides output/visuals; removing it restores held touch. Cancellation, remapping, and disposal release input.

### Staggered layouts

`buildGridAnchors` accepts typed rows of IDs, button size, origin, row/column step vectors, and clockwise rotation in degrees. Vectors need not be perpendicular, so the same helper supports staggered/slanted console layouts without platform IDs or console-specific settings. Ragged rows preserve their row index.

### License

```
Copyright (c) Jam.gg 2024.
Copyright (c) Filippo Scognamiglio 2025.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
