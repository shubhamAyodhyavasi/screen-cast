# iOS App – Screen Cast Sender

SwiftUI app that captures the iOS screen and streams it to a paired Android TV via WebRTC.

## Requirements

- Xcode 15+
- iOS 17+ deployment target
- A WebRTC SDK (e.g. [GoogleWebRTC](https://cocoapods.org/pods/GoogleWebRTC) or [WebRTC-SDK](https://github.com/stasel/WebRTC))
- The signaling server running (see `../signaling-server`)

## Project Structure

```
ScreenCast/
├── ScreenCastApp.swift          – App entry point
├── ContentView.swift            – Root SwiftUI view + device list
├── SignalingClient.swift        – WebSocket ↔ signaling server
└── ScreenCaptureManager.swift   – RPScreenRecorder integration
```

## Setup

1. Open the project in Xcode (create a new SwiftUI project and add the `.swift` files, or use an existing Xcode project file).
2. Add the WebRTC SDK via Swift Package Manager or CocoaPods.
3. In **Signing & Capabilities**, add the **Broadcast Upload Extension** capability.
4. Set `NSCameraUsageDescription` and `NSMicrophoneUsageDescription` in `Info.plist` if you need audio.
5. Update `SignalingClient.swift` to point to your signaling server:
   ```swift
   init(serverURL: URL = URL(string: "ws://<your-server-ip>:8080")!)
   ```
6. Build and run on a physical device (screen recording requires a real device).

## Architecture

```
iOS App
  └─ ScreenCaptureManager  (RPScreenRecorder → raw video frames)
       └─ WebRTCManager     (encode → RTP → ICE/DTLS)
  └─ SignalingClient        (WebSocket → signaling server)
```
