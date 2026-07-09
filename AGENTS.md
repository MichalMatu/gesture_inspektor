# Agent Notes

## Codex Working Style For This Repo

- Keep this file practical and concise. OpenAI Codex guidance recommends using `AGENTS.md` for durable repo rules, build/test commands, conventions, constraints, and verification expectations.
- Add rules here only when they prevent repeated mistakes or capture stable project decisions.
- Prefer small, behavior-preserving changes. Read the surrounding code first and follow existing project structure.
- When changing Kotlin or Android behavior, update or add focused unit tests and run the relevant Gradle checks before handing work back.
- Use `apply_patch` for manual edits and keep unrelated refactors out of task-focused changes.

References checked:

- OpenAI Codex best practices: https://developers.openai.com/codex/learn/best-practices
- OpenAI AGENTS.md guidance: https://developers.openai.com/codex/guides/agents-md
- OpenAI customization guidance: https://developers.openai.com/codex/concepts/customization
- Kotlin coding conventions: https://kotlinlang.org/docs/coding-conventions.html
- Android Kotlin style guide: https://developer.android.com/kotlin/style-guide
- Android coroutine best practices: https://developer.android.com/kotlin/coroutines/coroutines-best-practices

## Kotlin And Android Practices

- Follow the existing Android/XML/ViewBinding style in this repo. Do not introduce Compose or a new UI framework unless explicitly requested.
- Keep neutral gesture/control logic in pure Kotlin where possible so it stays unit-testable without Android instrumentation.
- Use clear package boundaries:
  - `gesture/` for MediaPipe frame parsing, interaction state, smoothing, formatter, and logging reducer.
  - `control/` for actions, bindings, presets, mapping, and controller logic.
  - `demo/dj/` only for optional example adapters, never for default app flow.
- Prefer immutable data models (`data class`, `val`) for snapshots, frames, interactions, actions, and log lines.
- Keep mutable state isolated in small stateful components such as engines, controllers, or reducers; cover that state with unit tests.
- Prefer explicit `private` helpers and `private const val` constants for implementation details.
- Avoid broad compatibility shims and legacy aliases when replacing internal APIs; migrate callers and remove old paths in the same change.
- Preserve Kotlin/Android naming and file organization: file names should match the main type, source should stay UTF-8, and indentation should use spaces, not tabs.
- Avoid `!!` in new code. If using generated ViewBinding lifecycle patterns, keep dereferences scoped to valid fragment lifecycle windows and null the binding in `onDestroyView`.
- Format user-visible numbers with an explicit locale, usually `Locale.US`, when stable decimal output matters.
- Do not block the main thread. Camera and MediaPipe work should stay off the UI thread; UI updates must return to the main thread.
- If adding coroutines later, use lifecycle-aware scopes (`viewModelScope`, lifecycle-aware collection) and inject dispatchers instead of hardcoding them in business/data classes.
- Do not add production dependencies unless they remove real complexity or match an established Android/MediaPipe need in this repo.

## Verification Commands

- After Kotlin core changes, run `./gradlew test`.
- For the full local quality gate, run `./scripts/check-quality.sh` or `./gradlew qualityCheck`.
- Android Lint is part of the quality gate through `./gradlew :app:lintDebug`.
- After Android UI, resource, manifest, or Gradle changes, run `./gradlew :app:assembleDebug`.
- Always run `git diff --check` before considering the work ready.
- If behavior is phone-facing, install and launch on the connected device with `./gradlew :app:installDebug`, grant camera permission, and start `MainActivity`.

## Gesture Inspector Logging

- Do not log full gesture diagnostics every frame by default. Keep logcat useful for day-to-day work.
- The app logs under the `GestureInspector` tag from `CameraFragment`.
- Default log level is `INFO` and should emit only:
  - `STATE` when a meaningful inspector state changes, such as hand count, status, primary gesture, zone, or movement.
  - `ACTION` when a gesture binding fires.
  - `HEARTBEAT` about every 2 seconds so we know the recognizer is alive.
- Full per-frame-style diagnostics are opt-in through Android log properties:

```bash
adb shell setprop log.tag.GestureInspector VERBOSE
adb logcat -s GestureInspector:V
```

- Turn verbose diagnostics back down with:

```bash
adb shell setprop log.tag.GestureInspector INFO
adb logcat -s GestureInspector
```

- Verbose output may include frame timestamp, active preset, top gesture candidates, handedness, raw/smoothed centers, zones, deltas, stable frames, hold duration, moved-during-hold, and matched binding IDs.
- Keep the on-screen inspector compact. The camera overlay should show only essential live feedback: hand count/status/inference, primary gesture/confidence, current or last action, hold, movement, and zone.
- If logging behavior changes, update `GestureInspectorLogReducerTest` and keep `GestureInspectorFormatterTest` aligned with the compact UI versus verbose diagnostics split.
