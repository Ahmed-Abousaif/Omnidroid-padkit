# Contributing to Omnidroid PadKit

Reusable gamepad capture, input state/events, simulation, controls, and haptic behavior.

## Issues and discussions

Search existing reports before opening a new one. Use the bug/feature forms and fill in the required reproduction, revision, and environment fields. Questions and design proposals belong in [Discussions](https://github.com/Ahmed-Abousaif/Omnidroid-padkit/discussions). Treat contributors respectfully and provide actionable feedback.

App installation, library UI, or general Omnidroid behavior can be reported in [Omnidroid](https://github.com/Ahmed-Abousaif/Omnidroid/issues). Keep shared-library or native-runtime reports here when the problem is isolated to this project.

## Development and validation

Use Omnidroid's source wrapper for integration checks: run :omnidroid-padkit:testDebugUnitTest and :omnidroid-touchinput:testDebugUnitTest from the Omnidroid root. Run ./gradlew :lib:ktlintCheck with the standalone repository's JDK/toolchain. Check touch/cancellation and physical haptics on a device when affected.

Read the project's [README](../README.md) and Omnidroid's [contributor setup](https://github.com/Ahmed-Abousaif/Omnidroid#-for-contributors--developers). Preserve the existing license notices, upstream build instructions, and recorded nested dependency revisions. Keep user preferences, product skins, and emulator-specific routing in the frontend unless this repository owns that behavior.

## Pull requests

Create a topic branch from current main, commit focused changes, and open a PR against main. Describe the concrete problem, resulting behavior, compatibility impact, and exact validation results. Link related issues and include visual evidence when relevant.

The default branch requires at least one approving review and code-owner review; force pushes and branch deletion are disabled, matching Omnidroid's baseline. Existing CI must be considered during review. The PR template is guidance, not an automatic body validator. Maintainers retain the same administrative override as Omnidroid.

Publish dependency changes before proposing a parent-project pin. Do not commit private credentials, signing material, game files, firmware, BIOS, or console keys.
