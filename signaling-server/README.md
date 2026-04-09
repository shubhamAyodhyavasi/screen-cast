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

## Message Protocol

See [`../shared-protocol/src/index.ts`](../shared-protocol/src/index.ts) for full TypeScript type definitions.

### Connection flow

```
Client                      Server
  |                            |
  |---- (TCP connect) -------->|
  |<--- { type:"connected",    |
  |       clientId: "<uuid>" } |
  |                            |
  |---- register ------------->| role + deviceName
  |<--- device-list -----------| all registered peers
  |                            |
  |---- offer / answer ------->| relayed to targetId
  |---- ice-candidate -------->| relayed to targetId
  |                            |
  |---- hangup -------------->| relayed to targetId
  |                            |
  |--(close)------------------>|
  |<--- peer-disconnected -----| broadcast to others
```
