# Signaling Server

A lightweight Node.js WebSocket server that relays WebRTC signaling messages between the iOS sender and the Android TV receiver.

## Requirements

- Node.js 18+
- npm

## Setup

```bash
cd signaling-server
npm install
npm start
```

The server listens on `ws://0.0.0.0:8080` by default.

## Environment Variables

| Variable | Default     | Description                  |
|----------|-------------|------------------------------|
| `PORT`   | `8080`      | WebSocket server port        |
| `HOST`   | `0.0.0.0`   | Interface to bind to         |

```bash
PORT=9000 npm start
```

## Project Structure

```
signaling-server/
├── server.js              – Entry point (requires src/index.js)
└── src/
    ├── index.js           – WebSocket server bootstrap
    ├── connectionManager.js – In-memory client registry
    └── messageHandler.js  – Message routing and dispatch
```

## Message Protocol

All messages are JSON. Type strings are **UPPERCASE**.
Device roles are `"ios"` (sender) and `"android_tv"` (receiver).

See [`../shared-protocol/src/index.ts`](../shared-protocol/src/index.ts) for full TypeScript type definitions.

### Connection flow

```
Client (iOS / Android TV)           Server
  |                                    |
  |──── (TCP connect) ────────────────►|
  |◄─── { type:"CONNECTED",            |
  |       clientId: "<uuid>" }         |
  |                                    |
  |──── REGISTER ──────────────────────► role + name
  |◄─── DEVICE_LIST ───────────────────  filtered list of peers
  |                                    |
  | iOS only:                          |
  |──── CONNECT_REQUEST ───────────────► targetId = android_tv clientId
  |   (relayed to Android TV)          |
  |◄─── CONNECT_ACCEPT ────────────────  relayed from Android TV
  |                                    |
  |──── OFFER / ANSWER ────────────────► relayed to targetId
  |──── ICE_CANDIDATE ─────────────────► relayed to targetId
  |                                    |
  |──── DISCONNECT ────────────────────► relayed to targetId
  |                                    |
  |──── (close) ───────────────────────►
  |◄─── PEER_DISCONNECTED ─────────────  broadcast to all
```

### Supported message types

| Type               | Direction                          | Description                                   |
|--------------------|------------------------------------|-----------------------------------------------|
| `CONNECTED`        | server → client                    | Server greeting with assigned `clientId`      |
| `REGISTER`         | client → server                    | Announce `role` (`ios`/`android_tv`) and `name` |
| `DEVICE_LIST`      | server → client                    | Filtered list of available peers              |
| `CONNECT_REQUEST`  | iOS → server → Android TV          | Request to pair                               |
| `CONNECT_ACCEPT`   | Android TV → server → iOS          | Accept the pairing request                    |
| `OFFER`            | iOS → server → Android TV          | WebRTC SDP offer                              |
| `ANSWER`           | Android TV → server → iOS          | WebRTC SDP answer                             |
| `ICE_CANDIDATE`    | either → server → other            | WebRTC ICE candidate                          |
| `DISCONNECT`       | either → server → other            | End the session                               |
| `PEER_DISCONNECTED`| server → all clients               | A peer has left                               |
| `ERROR`            | server → client                    | Error response                                |

