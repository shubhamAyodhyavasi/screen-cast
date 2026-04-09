# screen-cast – iOS to Android TV Screen Sharing

A monorepo that lets an **iOS device** discover a nearby **Android TV** and stream its screen (or any media) to it in real-time over a local network using WebRTC.

```
screen-cast/
├── ios-app/           Swift / SwiftUI – screen capture sender
├── android-tv-app/    Kotlin – WebRTC receiver displayed on TV
├── signaling-server/  Node.js WebSocket – relays SDP & ICE messages
└── shared-protocol/   TypeScript – canonical message type definitions
```

---

## Architecture overview

```
┌──────────────────────────────────────────────────────────────────────┐
│                       Local Network / Internet                        │
│                                                                        │
│   ┌─────────────────────┐     WebSocket     ┌──────────────────────┐ │
│   │      iOS App        │ ◄────────────────► │  Signaling Server    │ │
│   │  (SwiftUI sender)   │                    │  (Node.js / ws)      │ │
│   └────────┬────────────┘                    └──────────┬───────────┘ │
│            │  WebRTC (DTLS/ICE)                         │ WebSocket   │
│            │  Screen stream                             │             │
│   ┌────────▼────────────┐                    ┌──────────▼───────────┐ │
│   │   Android TV App    │ ◄────────────────► │  Signaling Server    │ │
│   │  (Kotlin receiver)  │   WebRTC (DTLS/ICE)│  (same instance)     │ │
│   └─────────────────────┘                    └──────────────────────┘ │
└──────────────────────────────────────────────────────────────────────┘
```

> The signaling server is only needed for the initial handshake (SDP offer/answer
> and ICE candidate exchange). After the WebRTC peer connection is established,
> media flows **directly** between the two devices.

---

## Quick Start

### 1. Start the signaling server

```bash
cd signaling-server
npm install
npm start
# Listening on ws://0.0.0.0:8080
```

### 2. Build the shared-protocol types (optional – only needed for TS tooling)

```bash
cd shared-protocol
npm install
npm run build
```

### 3. iOS App

1. Open Xcode (15+), create a new SwiftUI project targeting iOS 17+.
2. Copy the files from `ios-app/ScreenCast/` into your project.
3. Add a WebRTC SDK (e.g. via Swift Package Manager – `stasel/WebRTC`).
4. Edit `SignalingClient.swift` and set your server IP:
   ```swift
   init(serverURL: URL = URL(string: "ws://YOUR_SERVER_IP:8080")!)
   ```
5. Build and run on a **physical** iOS device (screen recording requires hardware).

See [`ios-app/README.md`](ios-app/README.md) for full instructions.

### 4. Android TV App

1. Open the `android-tv-app/` folder in Android Studio.
2. Sync Gradle, add a WebRTC Android library to `app/build.gradle.kts`.
3. Update the server address in `MainViewModel.kt`.
4. Deploy to an Android TV device or emulator.

See [`android-tv-app/README.md`](android-tv-app/README.md) for full instructions.

---

## Message Protocol

All messages between clients and the signaling server are JSON.
The canonical TypeScript definitions live in `shared-protocol/src/index.ts`.

| Message type       | Direction                     | Description                           |
|--------------------|-------------------------------|---------------------------------------|
| `register`         | client → server               | Announce role (`sender`/`receiver`) and device name |
| `device-list`      | server → all clients          | Current list of connected devices     |
| `offer`            | sender → server → receiver    | WebRTC SDP offer                      |
| `answer`           | receiver → server → sender    | WebRTC SDP answer                     |
| `ice-candidate`    | either → server → other       | ICE candidate                         |
| `hangup`           | either → server → other       | End the session                       |
| `peer-disconnected`| server → all clients          | A peer has left                       |

---

## Repository structure

| Path | Technology | Purpose |
|------|-----------|---------|
| `ios-app/` | Swift 5.9 · SwiftUI · ReplayKit | Capture iOS screen, encode via WebRTC, send stream |
| `android-tv-app/` | Kotlin · Leanback · OkHttp | Receive WebRTC stream, render on TV |
| `signaling-server/` | Node.js 18 · `ws` | WebRTC signaling relay |
| `shared-protocol/` | TypeScript 5 | Shared message type definitions |

---

## Prerequisites

| Component | Requirement |
|-----------|------------|
| iOS App | Xcode 15+, iOS 17+, physical device |
| Android TV App | Android Studio Hedgehog+, Android SDK 34 |
| Signaling Server | Node.js 18+ |
| shared-protocol | Node.js 18+, TypeScript 5 |

---

## License

MIT