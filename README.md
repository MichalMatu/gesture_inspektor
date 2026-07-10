# Gesture Inspector

Gesture Inspector is an Android starter for inspecting, stabilizing, and mapping
hand gestures from MediaPipe Gesture Recognizer. It is designed as a neutral,
testable base for gesture-driven applications rather than as a finished product
or a domain-specific controller.

The current runtime:

- processes camera pixels on-device and requests only camera as a runtime
  permission,
- replaces MediaPipe Tasks' published remote stats factory with its no-op
  logger and excludes Google DataTransport,
- recognizes up to two hands,
- cross-checks `Pointing_Up`, `Victory`, and `ILoveYou` against 3D finger
  geometry to reject ambiguous transitions,
- keeps a stable tracking ID when MediaPipe reorders detections,
- rejects stale frames, incomplete landmarks, invalid scores, and unsafe preset
  configurations,
- separates recognition, temporal interaction state, action mapping, and UI,
- limits asynchronous inference to one in-flight frame to bound memory use,
- resets gesture holds across lifecycle pauses, missing hands, timestamp gaps,
  and recognizer reconfiguration.

This is a development starter. Before shipping a product, rename the sample
namespace/application ID, add signing and product-specific security controls,
and complete the device test matrix in [Architecture and reliability](docs/ARCHITECTURE.md).

## Requirements

- JDK 17
- Android SDK 36
- Android 7.0 / API 24 or newer device
- a front-facing camera
- network access for the first build, unless all Gradle artifacts and the model
  are already cached

The app targets Android 16 / API 36, uses standard back navigation, and applies
system-bar insets for its edge-to-edge window. API 35 is the current minimum
target for new apps and updates submitted to Google Play; see the
[official target API requirements](https://developer.android.com/google/play/requirements/target-sdk).

## Build and run

```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug
adb shell pm grant com.google.mediapipe.examples.gesturerecognizer android.permission.CAMERA
adb shell am start -n com.google.mediapipe.examples.gesturerecognizer/.MainActivity
```

The first Gradle build downloads the MediaPipe task model. Camera permission can
also be granted through the in-app flow; permanent denial offers a shortcut to
application settings.

## Model asset

The build downloads this pinned model:

```text
https://storage.googleapis.com/mediapipe-models/gesture_recognizer/gesture_recognizer/float16/1/gesture_recognizer.task
size:   8,373,440 bytes
SHA-256: 97952348cf6a6a4915c2ea1496b4b37ebabc50cbbf80571435643c455f2b0482
```

Gradle downloads through a temporary file, retries transient failures, and
verifies SHA-256 before every build. The generated asset is intentionally
ignored by Git. On a machine whose Gradle wrapper, plugins, and dependencies are
already cached, an offline build can use a verified model placed at
`app/src/main/assets/gesture_recognizer.task`. A fresh checkout still needs all
of those Gradle artifacts before it can build offline. If verification reports
a corrupt model, delete it and run the build again.

See the official
[MediaPipe Gesture Recognizer overview](https://developers.google.com/edge/mediapipe/solutions/vision/gesture_recognizer)
and
[Android configuration guide](https://developers.google.com/edge/mediapipe/solutions/vision/gesture_recognizer/android).

## Recognition scope

The bundled canned classifier reports:

```text
Closed_Fist
Open_Palm
Pointing_Up
Thumb_Down
Thumb_Up
Victory
ILoveYou
None
```

These are static, per-hand gesture categories. Left/right/up/down movement,
hold duration, stability, zones, and repeat behavior are computed by this
application from hand landmarks; they are not extra MediaPipe model classes.

The bundled classifier exposes only its winning class. Because transitions
between one-finger, two-finger, and `ILoveYou` poses can briefly rank as
`Victory`, the app independently checks joint angles, finger linearity, and
reach in the 3D world landmarks. A correction is applied only when the geometry
has a high score and a clear margin; raw model output remains in verbose logs.

The canned model is not a sign-language recognizer, identity system, safety
monitor, or general two-hand gesture classifier. Validate accuracy, lighting,
occlusion, skin-tone coverage, camera placement, latency, and accidental
activation for the actual product environment. Train or supply a custom model
when the canned gesture set is insufficient.

## Architecture

```text
GestureRecognizerResult
  -> GestureFrameSet                 validated per-frame observations
  -> MultiHandGestureInteractionEngine
                                      stable tracks, smoothing, holds, motion
  -> GestureActionMapper             preset conditions and trigger semantics
  -> GestureInspectorSnapshot        read-only UI/integration output
  -> GestureInspectorFormatter       compact UI and verbose diagnostics
```

Packages:

```text
gesture/       parsing, identity tracking, temporal state, formatting, logging
control/       actions, bindings, presets, mapping, controller
demo/dj/       optional example adapter; never part of the default app flow
fragment/      camera, permission, lifecycle, and inspector UI
```

Important invariants and tuning guidance are documented in
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Default Inspector Demo preset

```text
Open_Palm still/no movement      -> action.open_palm_still
Open_Palm move left              -> action.open_palm_left
Open_Palm move right             -> action.open_palm_right
Closed_Fist hold                 -> action.fist_hold
Thumb_Up hold                    -> action.thumb_up_hold
Thumb_Down hold                  -> action.thumb_down_hold
Pointing_Up move up              -> action.pointing_up_up
Pointing_Up move down            -> action.pointing_up_down
Victory still/no movement        -> action.victory_still
Victory move up                  -> action.victory_up
Victory move down                -> action.victory_down
ILoveYou top/middle short hold   -> action.iloveyou_short
ILoveYou bottom long hold        -> action.iloveyou_bottom_long
```

Victory actions require at least 70% confidence to avoid firing on the common
transition from `Victory` to `Pointing_Up` or `ILoveYou`.

The `ILoveYou` bindings share an exclusive group. The highest-priority matching
binding wins that hold; a repeating winner may continue, while peers cannot
take over during its cooldown.

## Add a preset or integration

Create a `GesturePreset` containing `GestureBinding` entries. Bindings can match
gesture confidence, hold range, handedness confidence, zones, movement,
no-movement-during-hold, priority, cooldown/repeat timing, and exclusive group.
Invalid ranges, duplicate binding IDs, reserved `None`, and incomplete
`SetValue` actions fail immediately during configuration.

Call `GestureController.handle(frameSet)` and consume
`snapshot.actionEvents`. Translate each neutral `GestureActionEvent` in an
adapter outside the gesture core—for example MIDI, smart-home, robot, game,
presentation, or audio control. The DJ package demonstrates this adapter shape.

## Inspector and logcat

The overlay shows hand count/status/inference time, primary gesture/confidence,
matched or last action, hold, movement, and zone.

The default log is intentionally quiet:

```bash
adb logcat -s GestureInspector
```

It emits `STATE` on meaningful changes, `ACTION` when a binding fires, and a
heartbeat about every two seconds. Enable throttled full diagnostics with:

```bash
adb shell setprop log.tag.GestureInspector VERBOSE
adb logcat -s GestureInspector:V
```

Restore normal logging with:

```bash
adb shell setprop log.tag.GestureInspector INFO
```

Verbose lines include frame time, preset, stable track and detection IDs,
handedness, candidates, raw/smoothed palm center, zones, movement, stable frame
count, hold time, landmark reliability, geometric finger-extension scores,
classification source, and matched binding. The compact UI labels a remembered
event as `Last action`, separately from the current gesture.

## Verification

Run the complete local gate:

```bash
./scripts/check-quality.sh
```

The script first runs `git diff --check`, then the shared Gradle gate:

```bash
./gradlew qualityCheck
```

The Gradle task runs Spotless/ktlint, detekt, Kover coverage verification, unit
tests, Android Lint, debug assembly, a final merged-manifest permission
allowlist, and verification that the MediaPipe dummy logger is installed with
no DataTransport dependency. GitHub Actions runs the same Gradle task. For a
faster whitespace-only check, use `./scripts/check-fast.sh`.

Other useful commands:

```bash
./gradlew :app:koverHtmlReportDebug
./gradlew dependencyUpdates -Drevision=release
```

`dependencyUpdates` is informational. Review its report manually; version
schemes and tooling/transitive artifacts can produce misleading suggestions.
It never applies updates. In particular, Google Maven currently orders the old
MediaPipe build `0.20230731` above the pinned `0.10.35`; do not treat that report
entry as an upgrade.

After an intentional plugin or dependency change, regenerate artifact checksums
with:

```bash
./gradlew --write-verification-metadata sha256 qualityCheck
```

Review the `gradle/verification-metadata.xml` diff before accepting it. Never
regenerate the file merely to make an unexplained checksum mismatch disappear.

After camera, lifecycle, model, overlay, SDK, or MediaPipe changes, also test a
physical device:

1. grant, deny, permanently deny, and restore camera permission;
2. verify one- and two-hand tracking, including crossed hands and reordered
   detections;
3. background/resume and rotate while a hold is in progress—no action should
   fire from time spent off-screen;
4. switch CPU/GPU delegates and thresholds repeatedly;
5. run a 15–30 minute CPU/GPU soak while watching memory, temperature, latency,
   dropped frames, and overlay alignment.

## Privacy and security

Camera pixels are processed on-device and are not intentionally stored. The
[upstream MediaPipe privacy notice](https://github.com/google-ai-edge/mediapipe#privacy-notice)
says the standard Tasks binaries send performance/utilization metrics, though
not input images. This project patches the pinned MediaPipe core at build time
to use MediaPipe's no-op stats logger, excludes Google DataTransport, and
enforces a merged-manifest allowlist containing `CAMERA` plus AndroidX's
app-signature guard for non-exported dynamic receivers. The resulting app has
no runtime network permission or telemetry path. Re-audit this patch on every
MediaPipe upgrade. Application backup is disabled. See
[SECURITY.md](SECURITY.md).

## License

This project contains sample-derived code carrying TensorFlow Authors notices
and project modifications. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
