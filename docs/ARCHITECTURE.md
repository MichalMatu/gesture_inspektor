# Architecture and reliability

This document defines the runtime contracts that keep Gesture Inspector safe to
extend. Changes to these contracts should include focused unit tests.

## Thread and lifecycle model

- CameraX analysis, bitmap conversion, MediaPipe setup, inference submission,
  and recognizer shutdown run on one background executor.
- At most one MediaPipe frame is in flight. Later CameraX frames are closed
  immediately, before allocation and conversion.
- Every `ImageProxy` is closed by the analyzer path. Every callback `MPImage` is
  closed after its dimensions and result have been copied into `ResultBundle`.
- A recognizer generation is incremented on clear/reconfigure. Results from an
  older generation are never allowed to mutate the controller or emit actions.
- Results are accepted only while the camera view lifecycle is `RESUMED`.
- Pause, missing permission, reconfiguration, and view destruction stop action
  processing and reset temporal gesture state.
- Camera use cases are bound to `viewLifecycleOwner` and explicitly unbound when
  the view is destroyed. Destruction never waits indefinitely on the main
  thread.

## Telemetry-disabled MediaPipe runtime

Google's published MediaPipe Tasks Android AAR normally constructs a remote
stats logger and brings Google DataTransport into the app. The build extracts
the pinned `tasks-core` AAR, replaces only `TasksStatsLoggerFactory` with a
factory for MediaPipe's bundled `TasksStatsDummyLogger`, and then packages that
patched core instead of the published core dependency. DataTransport is not on
the runtime classpath.

The quality gate verifies the factory bytecode marker, dependency graph, and
merged manifest. The only platform permission allowed is `CAMERA`; AndroidX
also contributes an app-signature permission protecting non-exported dynamic
receivers. Treat the patch as version-specific: inspect the upstream logger and
rerun physical-device tests before changing the MediaPipe version.

## Frame parsing contract

`GestureFrameSet.fromResult` treats MediaPipe landmarks as the authoritative
hand list. Gesture or handedness entries without a corresponding landmark list
cannot create an actionable “ghost hand”.

The parser:

- rejects blank category names and non-finite/out-of-range scores;
- requires finite x/y/z values before producing a center;
- uses the wrist and MCP landmarks `0, 5, 9, 13, 17` for a palm center when all
  21 landmarks are present;
- clamps the final normalized center to `[0, 1]` because landmarks can sit just
  outside the image at its edge;
- preserves detection index only as per-frame diagnostic data.

For `Pointing_Up`, `Victory`, and `ILoveYou`, a pure-Kotlin geometry classifier
also evaluates world-landmark joint angles, finger linearity, and reach in a
palm-relative 3D coordinate system. It is invariant to translation, scale,
rotation, and mirroring. It overrides the canned model only for a complete,
non-degenerate 21-point hand with a conservative template score and margin;
otherwise the original MediaPipe result remains authoritative. Raw candidates
and per-finger scores stay available in verbose diagnostics.

## Stable hand identity

`MultiHandGestureInteractionEngine` assigns an internal `trackingId`; it never
uses MediaPipe list position as identity. Candidate assignments maximize the
number of retained tracks first and minimize total center distance second.
High-confidence handedness prevents cross-hand matches; low-confidence label
flips do not override a nearby positional match.

Interactions are sorted by tracking ID so event order is deterministic even
when detection order changes. A missing frame preserves identity briefly but
resets its hold. Old tracks expire after 500 ms.

The shipped recognizer is configured for at most two hands. If that limit is
raised, benchmark the exhaustive assignment step and MediaPipe latency before
shipping.

## Temporal interaction contract

An interaction is actionable only when:

- the gesture score is at least the engine threshold;
- both center coordinates exist;
- at least 21 landmarks are present;
- the timestamp sequence is fresh and monotonic;
- the binding's required stable-frame count and conditions are met.

Invalid landmarks, low confidence, gesture change, or a gap over 500 ms restart
the hold. The controller drops duplicate/regressed frames before they reach the
engine and returns no new action events; direct engine callers get a restarted
hold for duplicate/regressed timestamps.

The engine applies exponential center smoothing. Movement is measured against a
rolling anchor rather than only the previous frame, so slow drift eventually
counts as movement. Crossing a movement threshold advances the anchor. Zone
hysteresis prevents left/center/right or top/middle/bottom oscillation at a
boundary.

Default normalized tuning values:

```text
minimum tracked gesture score     0.60
movement dead zone                0.035
smoothing alpha                   0.25
zone boundaries                   0.33 / 0.66
zone hysteresis                   0.02
maximum frame/track gap           500 ms
stable frames before mapping      3
identity match distance           0.35
identity handedness confidence    0.75
```

Treat these as product calibration parameters, not universal constants. Camera
distance, field of view, desired gesture scale, inference FPS, and accidental
activation cost all matter.

## Action mapping contract

For each track and frame, matching bindings are ordered by descending priority
and then ID. Only the highest-ranked match may attempt to emit; a lower-priority
binding cannot fire merely because the winner is in cooldown.

- `OncePerHold` emits once until the gesture/track hold resets, with optional
  cross-hold cooldown.
- `RepeatWhileHeld` emits at `repeatIntervalMs` while all conditions continue to
  match.
- An exclusive group stores its winning binding. That winner may repeat; peers
  remain blocked for the hold.
- Hand-specific bindings require both the requested handedness label and the
  configured handedness confidence.
- Missing/unreliable interactions reset once-per-hold and exclusive state.

Binding, action, preset, frame, candidate, and engine constructors validate
their invariants. Do not catch these `IllegalArgumentException`s around static
presets; fix the invalid configuration.

The Inspector and DJ demo presets require 70% confidence for `Victory` actions,
above the generic 60% floor, because transient one-finger and `ILoveYou` poses
were observed to pass through weak `Victory` predictions.

## Test layers

Pure JVM tests cover parsing, tracking identity, assignment cardinality,
confidence and landmark failure, movement accumulation, zone hysteresis,
timestamp gaps, controller resets, priority, cooldowns, exclusive groups,
formatting, and reduced logging.

The full Gradle quality gate also enforces formatting, detekt, core coverage,
Android Lint, and APK assembly. JVM tests cannot prove camera transforms,
driver/GPU behavior, memory stability, or lifecycle races on a real device;
follow the physical-device checklist in the README.

## Known productization work

- rename the inherited namespace/application ID;
- add release signing, shrinker rules, and a release pipeline;
- add instrumentation tests for permission, delayed camera callbacks, rotation,
  and CPU/GPU fallback;
- preserve and re-audit the telemetry-disabled MediaPipe patch on dependency
  upgrades, or consciously replace it with a separately reviewed policy;
- calibrate overlay transforms on target aspect ratios and rotations;
- choose whether action cooldown scope is per track or global for the product;
- perform long-running memory, thermal, and false-activation testing;
- train and evaluate a custom model if canned gestures are not sufficient.
