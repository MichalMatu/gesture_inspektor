# Gesture Inspector

Android starter for inspecting and mapping hand gestures from MediaPipe Gesture
Recognizer.

Gesture Inspector is a neutral base for camera-driven gesture control. It shows
what MediaPipe sees, how stable a gesture is, how the hand moves, and which
configured action would match. Use it as a starting point for DJ/audio, MIDI,
smart home, robot/IoT, presentation remote, UI control, games, or custom action
adapters.

## What It Is

- Android + MediaPipe Gesture Recognizer starter.
- A gesture oscilloscope for confidence, candidates, hands, zones, movement,
  hold time, stable frames, and matched actions.
- A neutral mapping layer built around presets and action adapters.
- A simple XML/ViewBinding app focused on the live CameraX inspector flow.

## What It Is Not

- Not a real audio engine, MIDI bridge, smart home integration, robot control,
  or marketplace preset system.
- Not a custom ML training project.
- Not a Compose rewrite.

DJ support is kept only as a demo preset/adapter under `demo/dj`. The default
app mode is the neutral Inspector Demo.

TODO before publication: the namespace/applicationId still comes from the
MediaPipe sample and should be renamed when this starter becomes a product app.

## Architecture

```text
gesture/       MediaPipe result model, multi-hand interaction state, smoothing
control/       GestureAction, GestureBinding, mapper, controller, presets
demo/dj/       Optional example adapter built on the neutral control layer
fragment/      Camera-only UI and permission flow
```

Core flow:

```text
GestureRecognizerResult
  -> GestureFrameSet
  -> MultiHandGestureInteractionEngine
  -> GestureActionMapper
  -> GestureInspectorSnapshot
  -> GestureInspectorFormatter
```

## Inspector Mode

The camera screen shows:

- hand count, frame status, and inference time,
- best gesture and confidence,
- matched action,
- hold time, movement, zone, and last action.

Full diagnostics are logged to ADB/logcat:

```bash
adb logcat -s GestureInspector
```

The log includes frame timestamp, active preset, per-hand handedness, top
candidates, raw/smoothed center, zones, deltas, stable frames, hold duration,
movement flags, and matched binding IDs.

## Supported MediaPipe Gestures

The bundled MediaPipe model can report:

```text
Open_Palm
Closed_Fist
Pointing_Up
Thumb_Up
Thumb_Down
Victory
ILoveYou
```

## Default Inspector Demo Preset

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

The `ILoveYou` short and long bindings share an exclusive group so one hold
cannot fire multiple actions from that group.

## Build

```bash
./gradlew :app:assembleDebug
```

## Unit Tests

```bash
./gradlew test
```

## Git Hooks

Enable the repository hooks once per checkout:

```bash
./scripts/install-git-hooks.sh
```

The pre-commit hook runs `git diff --check --cached`. The pre-push hook runs
`./gradlew test` and `./gradlew :app:assembleDebug`.

## Install on a connected phone

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant com.google.mediapipe.examples.gesturerecognizer android.permission.CAMERA
adb shell am start -n com.google.mediapipe.examples.gesturerecognizer/.MainActivity
```

## Add a Custom Preset

Create a `GesturePreset` with `GestureBinding` entries. A binding connects a
MediaPipe gesture plus conditions to a neutral `GestureAction`.

Useful binding conditions include:

- confidence threshold,
- min/max hold time,
- hand preference,
- zones,
- movement direction,
- no-movement-during-hold requirement,
- priority,
- repeat/cooldown timing,
- exclusive group.

## Add a Custom Integration

Keep integrations outside the core. Subscribe to `GestureActionEvent` from the
controller and translate neutral actions into a domain-specific sink, for
example:

- DJ/audio,
- MIDI,
- smart home,
- robot/IoT,
- presentation remote,
- UI control,
- game input.

## Next Steps

- Presets JSON.
- Gesture arm/clutch mode.
- Two-hand control beyond index-based tracking.
- Optional domain branches for DJ/audio, MIDI, IoT, or games.
- Rename namespace/applicationId before publishing.
