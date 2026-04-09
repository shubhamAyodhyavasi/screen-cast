# Android TV App – Screen Cast Receiver

Android TV app that receives a screen stream from a paired iOS device via WebRTC.

## Requirements

- Android Studio Hedgehog (2023.1) or newer
- Android SDK 34
- A Google WebRTC library (integration point documented in `WebRTCManager.kt`)
- The signaling server running (see `../signaling-server`)

## Project Structure

```
app/src/main/
├── AndroidManifest.xml
├── java/com/screencast/androidtv/
│   ├── MainActivity.kt          – Activity that hosts the fragment
│   ├── MainFragment.kt          – Leanback fragment + UI bindings
│   ├── MainViewModel.kt         – ViewModel (connection state, lifecycle)
│   ├── SignalingClient.kt       – WebSocket ↔ signaling server (OkHttp)
│   └── WebRTCManager.kt         – WebRTC peer connection (stub; see notes)
└── res/
    ├── layout/activity_main.xml
    ├── layout/fragment_main.xml
    └── values/strings.xml
```

## Setup

1. Open the `android-tv-app/` folder in Android Studio.
2. Sync Gradle and let dependencies download.
3. Add a WebRTC Android library to `app/build.gradle.kts` – for example:
   ```kotlin
   implementation("io.github.webrtc-sdk:android:114.5735.02")
   ```
4. Implement the `TODO` sections in `WebRTCManager.kt`.
5. Update the signaling server address in `MainViewModel.kt`:
   ```kotlin
   serverUrl = "ws://<your-server-ip>:8080"
   ```
6. Deploy to an Android TV device or emulator with the TV system image.

## Architecture

```
Android TV App
  └─ MainFragment         (UI – SurfaceView for video)
       └─ MainViewModel   (ViewModel)
            ├─ SignalingClient  (OkHttp WebSocket → signaling server)
            └─ WebRTCManager   (PeerConnection → decoded video → SurfaceView)
```
