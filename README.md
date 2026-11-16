# Omnidroid PadKit

Omnidroid PadKit is an **Android-only Jetpack Compose touch-control library** for Omnidroid. It is forked from [Swordfish90/PadKit](https://github.com/Swordfish90/PadKit), which continues Jam.gg's JamPadCompose. The fork preserves the existing controls and customization APIs, adds Omnidroid's input and feedback features, and reduces allocation in frequent touch updates.

**Minimum Android: 7.0 / API 24.** The Kotlin package remains `gg.padkit`. Omnidroid builds the recorded submodule sources through its `omnidroid-padkit` Android wrapper.

## Changes from the original PadKit

### Android-only build and repository

- Replaced Kotlin Multiplatform targets with an Android library using AGP's built-in Kotlin support. Production sources and the manifest live in `lib/src/main`; local Android unit tests live in `lib/src/test`.
- Removed desktop, iOS, and browser/Wasm implementations and samples. The remaining device demo is a single optional Android `:sample` app using Material 3 and `ComponentActivity`.
- Aligned the build with Omnidroid: AGP **9.1.1**, Gradle **9.3.1**, Kotlin/Compose compiler **2.2.10**, Compose BOM **2026.08.00**, compile SDK **37**, and Java **17** bytecode. The demo and Omnidroid still target SDK **36**; minimum SDK remains **24**.
- Regenerated and checksum-pinned the Gradle wrappers, enabled AAR metadata validation, and exported the Compose BOM with public Compose dependencies so consuming projects resolve compatible versions.
- Removed unused publication/signing/versioning setup, wiki deployment, hook/run-configuration machinery, and the 68.8 MB checked-in formatter executable. Formatting uses the cached Gradle ktlint dependency.
- Narrowed the library's icon dependency to `material-icons-core`. Material 3, immutable collections, stock visuals, and all five control primitives remain because the supported API uses them.
- Replaced platform-suffixed haptics source names with ordinary Android source names while retaining the existing Android JVM factory/helper facade for compiled callers.

### Input ownership, lifecycle, and simulation

- Keep one immutable input contribution per registered handler. Removing one control or finger cannot release a key still held by another contribution.
- Release owned input on pointer cancellation, control removal, mapping replacement, invalid/collapsed bounds, and pad disposal. Reset capture and double-tap gesture data when cancelled.
- Deliver quick transitions, reentrant callback changes, and disposal releases synchronously and in order. Press events precede releases within a key-change batch; state, event, and haptic delivery retain their established order.
- Apply changes to simulated control IDs as well as simulated state immediately. Removing an override restores held touch input; disabling tilt releases its simulated contribution. Mutable caller-owned ID selections are copied.
- Recreate handlers and derived visual state when mappings change, releasing the old mapping and updating the new pressed/direction visuals.
- Use typed internal gesture data instead of `Any?` casts, while keeping mutation on the Compose/UI thread and published snapshots immutable.

### Controls, geometry, and Android haptics

- Added `ControlDirectionalAnalog`: a thumbstick with smooth unquantized visuals and discrete four/eight-way output. Dead-zone or diagonal-policy changes recompute a held direction without losing capture or snapping the thumb.
- Added typed `buildGridAnchors` for regular, staggered, slanted, rotated, and ragged button rows. Console-specific spacing and IDs remain the host's choice.
- Added live `HapticFeedbackSettings`: normalized strength, discrete activation/change policy, continuous-direction feedback, independent/coalesced effects, and excluded control IDs. Muted transitions advance the tracker without replaying presses when feedback resumes.
- Added an injectable `HapticFeedback` output adapter and `createAndroidHapticFeedback(context)` for host-owned actions. Classification finishes before adapter callbacks so a callback cannot change the policy of an in-flight feedback batch.
- Android feedback uses 30 ms press / 15 ms release vibration. API 24/25 use duration-only vibration; API 26+ checks amplitude capability and uses the hardware default when amplitude control is unavailable. Newer platform APIs are guarded. Zero strength and `NONE` avoid initializing the default vibration adapter.
- Fixed overlapping controls so an ineligible rectangular corner does not block another control's valid circular hit. Eligible overlaps retain registration priority.
- Empty face layouts are passive. Geometry rejects non-finite positions/rotations and negative sizes, while retaining valid zero-sized anchors/dials and unrestricted finite custom anchor positions.

### Touch-path and Compose performance

- Index captured-pointer ownership instead of repeatedly searching every handler. Reuse per-handler pointer buffers, cache usable bounds, skip unchanged handlers, and merge touch contributions only when their logical state changes.
- Reuse empty/no-op immutable state and skip unchanged simulation work. Shared key/direction diff iteration avoids temporary difference/union collections; haptic classification reuses its pending-effects buffer.
- Defer analog thumb state reads to the offset placement lambda. Movement no longer needs recomposition solely to reposition the thumb; thumb size, travel, mapping, and release center are preserved.
- Reuse face-button pressed-key scratch storage and cache each face control's immutable contribution while fingers move within the same logical buttons. Arbitrary incoming contributions retain their existing union behavior.
- Reuse unchanged singleton capture sets in face controls and D-pads. Capture ownership updates compare ID values so a fresh equivalent set does not trigger redundant map writes.
- Remember combined face anchors and flattened primary keys. Simplify layout measurement and rectangle accumulation to avoid temporary zip/map lists while preserving child-count handling, placement, and rounding.
- Removed redundant event forwarding helpers, unused imports, an unused internal default argument, and unnecessary reified type information. Public configuration Booleans, required interface callbacks, dependencies, and resource contents remain supported.

## Supported controls

- `ControlButton`: uncaptured digital touch input, including sliding between controls.
- `ControlFaceButtons`: radial IDs or explicit anchors, multiple fingers, optional capture, and adjacent-button composite regions.
- `ControlCross`: captured four/eight-way D-pad input.
- `ControlAnalog`: relative analog stick with captured drag-out and optional double-tap stick click.
- `ControlDirectionalAnalog`: smooth thumb visuals with discrete four/eight-way output and a configurable dead zone.
- `LayoutRadial`, radial position/scale modifiers, and `buildGridAnchors`: reusable control placement. Foreground/background slots and stock visuals remain customizable.

`PadKit` exposes immutable `InputState` snapshots and ordered `InputEvent` changes. Neutral directions and untouched/released directions remain distinct (`Offset.Zero` versus `Offset.Unspecified`). Typed simulation supports tilt and other programmatic input.

Product skins, preferences, console placement, sensors, host actions, physical controllers, emulator routing, touchscreen/stylus forwarding, and emulator rumble belong in Omnidroid.

## Performance results

Measured locally on **16 November 2026**. The results below describe separate optimization stages with different baselines and workloads; their percentages must not be added or treated as a direct comparison with today's upstream repository.

### Initial routing and state optimization

A host replay through 12 controls compared the fork before and after its initial routing/state changes. Allocated bytes per event fell:

- Two pointers: **4,152 → 1,160** (**72.1% fewer**).
- Five pointers: **7,176 → 2,016** (**71.9% fewer**).
- Ten pointers: **11,344 → 3,840** (**66.1% fewer**).
- Repeated unchanged simulation: **536 → below 0.01 measured bytes/update**, including allocation-counter overhead.

This earlier JBR 21 replay warmed up for 20,000 events and measured nine batches of 10,000 events. Median batch-mean time decreased by 18.8%, 38.6%, and 33.7% in the respective pointer scenarios, and 76.6% for unchanged simulation. These historical host timings do not establish Android frame-time or touch-latency improvements.

### Face-button and capture optimization

The later replay compares the already optimized Android-only fork immediately before and after its face-button/capture changes. Median allocated bytes per event fell:

- One finger moving within a held face button: **528 → 184** (**65.2% fewer**).
- Three fingers moving within held face buttons: **1,816 → 584** (**67.8% fewer**).
- Three fingers changing button selections: **1,760 → 1,112** (**36.8% fewer**).
- Mixed routing through 12 controls with four pointers: **1,288 → 920** (**28.6% fewer**).

Method: JBR 21, 50,000 warmup events, nine batches of 20,000 events, and the median of per-batch allocated bytes/event on the replay thread. Prebuilt touch inputs exclude touch-event construction from these totals. The measurement probe ran outside the module; there is no desktop/HotSpot benchmark target or shipping benchmark dependency. [Recorded allocation evidence](docs/performance-results.json) includes the scenario definitions and measurement limits. Timing comparisons from this later pass are not used because the final measurements overlapped build work.

Reusable buffers and cached immutable state retain a small amount of per-control memory. In this later pass the unshrunk fork release AAR increased from **230,954 to 231,844 bytes** (**+890 bytes / 0.39%**). These are host allocation results, not measurements of Android FPS, touch-to-display latency, battery life, GC pauses, or whole-app memory. No whole-app APK size reduction is claimed.

## Build and test

Install JDK 17 or 21, Android SDK platform `platforms;android-37.0`, and `build-tools;36.0.0`. Set `ANDROID_HOME` or `sdk.dir` in an untracked `local.properties`.

```sh
./gradlew :lib:verifyAndroid7Compatibility :lib:ktlintCheck
./gradlew -PincludeSample=true assembleDebug test lint ktlintCheck
```

`verifyAndroid7Compatibility` builds a release AAR, runs release lint including unsupported Android API checks, and executes the library tests. The second command also builds the optional demo and runs full debug lint, including test sources. Windows users can replace `./gradlew` with `.\gradlew.bat`.

Validation after the latest performance pass:

- **104 library tests**, zero failures/errors/skips, plus **five Omnidroid tilt tests** through the host integration.
- **22 explicit API 24 cases**. Compose cases also run on API 35; haptic tests cover API 24/25/26/28/35.
- Coverage includes capture, drag-out, cancellation, remapping, disposal, simulation restoration, immutable delivered snapshots, event/feedback order, geometry, and seeded multi-finger models. Face-button tests check 4,000 frames across capture modes and incoming contributions.
- Debug/demo builds, release AAR packaging, ktlint, Omnidroid app compilation, and Omnidroid release R8 processing pass. All **160 checked public members across 30 entry-point classes** remain intact in the latest performance comparison.
- Lint reports zero errors, with 15 existing library warnings and five demo warnings about dependency/tool versions, catalog style, target SDK, and launcher icons. No new warnings were introduced by the performance pass.

Tests and Android CI run from this repository without an Omnidroid checkout, emulator sources, signing material, or publishing credentials. Omnidroid's shipping release enables R8 and resource shrinking; the library AAR stays unshrunk for consumer optimization. The optional demo's release minification is currently disabled.

From the Omnidroid checkout:

```sh
./gradlew :omnidroid-padkit:verifyAndroid7Compatibility :omnidroid-padkit:ktlintCheck :omnidroid-touchinput:testDebugUnitTest
./gradlew :omnidroid-app:compileFreeDynamicDebugKotlin
```

The fork is consumed as a pinned source dependency. Publish dependency changes before updating the parent pin. The upstream Maven artifact `io.github.swordfish90:padkit` is a separate upstream publication.

## Optional Android device demo

```sh
./gradlew -PincludeSample=true :sample:assembleDebug
```

The `gg.padkit.sample` / “PadKit controls” app exercises all five control types with the same minimum SDK and library sources. Check multi-touch, drag-out, hide/background while held, and physical vibration on devices. Local unit tests cannot measure vibration feel or touch-to-display latency.

Formatting uses Gradle ktlint; compiler reports are opt-in with `-PcomposeReports=true`. The demo uses standard `sample/src/main` sources and resources, including the launcher drawable without a redundant API 24 qualifier.

## License and attribution

Apache License 2.0. Preserve the upstream notices and both license files.

Copyright (c) Jam.gg 2024. Copyright (c) Filippo Scognamiglio 2025.
