# DJ Gesture Deck

Android prototype for controlling DJ actions with phone camera hand gestures.

The app is based on MediaPipe Gesture Recognizer. Camera frames are analyzed on
device, mapped into DJ commands, and shown in the live `DJ Gesture Control`
status panel.

## Current Gesture Map

```text
Open_Palm   -> Deck A Play/Pause
Closed_Fist -> Deck A Cue
Thumb_Up    -> Deck A Volume +
Thumb_Down  -> Deck A Volume -
Pointing_Up -> Deck A Filter +
Victory     -> Crossfader Center
ILoveYou    -> Deck A FX toggle
```

## Project Structure

```text
app/src/main/java/.../dj/       gesture-to-DJ mapping and controller state
app/src/main/java/.../fragment/ camera/gallery UI
app/download_tasks.gradle       MediaPipe model download
```

## Build

```bash
./gradlew :app:assembleDebug
```

## Install on a connected phone

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant com.google.mediapipe.examples.gesturerecognizer android.permission.CAMERA
adb shell am start -n com.google.mediapipe.examples.gesturerecognizer/.MainActivity
```

## Test on a connected phone

```bash
./gradlew :app:connectedDebugAndroidTest
```
