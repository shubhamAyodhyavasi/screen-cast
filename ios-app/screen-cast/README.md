# iOS App – Screen Cast Sender

SwiftUI app that discovers Android TV devices via the signaling server and initiates a WebRTC session to stream the iOS screen.

## Requirements

- Xcode 15+
- iOS 17+ deployment target
- A WebRTC SDK (e.g. [GoogleWebRTC](https://cocoapods.org/pods/GoogleWebRTC) or [WebRTC-SDK](https://github.com/stasel/WebRTC))
- The signaling server running (see `../signaling-server`)

## Project Structure

```
ScreenCast/
├── ScreenCastApp.swift              – App entry point
├── ContentView.swift                – Root view (hosts DeviceListView)
├── SignalingClient.swift            – Migration note (replaced by Services/)
├── ScreenCaptureManager.swift       – RPScreenRecorder integration
├── Models/
│   └── Device.swift                 – Android TV device model
├── Services/
│   └── WebSocketService.swift       – WebSocket transport + message dispatch
├── ViewModels/
│   └── DeviceListViewModel.swift    – MVVM ViewModel for device discovery
└── Views/
    └── DeviceListView.swift         – Device list + connection status UI
```

## Setup

1. Open the project in Xcode (create a new SwiftUI project and add the `.swift` files, or use an existing Xcode project file).
2. Add the WebRTC SDK via Swift Package Manager or CocoaPods.
3. In **Signing & Capabilities**, add the **Broadcast Upload Extension** capability.
4. Set `NSCameraUsageDescription` and `NSMicrophoneUsageDescription` in `Info.plist` if you need audio.
5. Update the server URL in `Services/WebSocketService.swift`:
   ```swift
   init(serverURL: URL = URL(string: "ws://<your-server-ip>:8080")!)
   ```
6. Build and run on a physical device (screen recording requires a real device).

## Architecture

```
ContentView
  └─ DeviceListViewModel   (ObservableObject)
       └─ WebSocketService  (WebSocket transport, UPPERCASE message protocol)
            • role: "ios"
            • Sends:  REGISTER, CONNECT_REQUEST, OFFER, ANSWER, ICE_CANDIDATE, DISCONNECT
            • Handles: CONNECTED, DEVICE_LIST, CONNECT_ACCEPT, OFFER, ANSWER, ICE_CANDIDATE,
                       DISCONNECT, PEER_DISCONNECTED
  └─ ScreenCaptureManager  (RPScreenRecorder → raw video frames → WebRTC)
```

## Protocol roles

| Role         | This app  | Android TV app |
|-------------|-----------|----------------|
| `ios`        | ✓ sender  |                |
| `android_tv` |           | ✓ receiver     |

