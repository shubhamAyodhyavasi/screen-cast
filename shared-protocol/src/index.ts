/**
 * Shared protocol types for iOS ↔ Signaling Server ↔ Android TV communication.
 * Both the signaling server and any TypeScript-aware tooling should import from here.
 *
 * Message type strings are UPPERCASE (e.g. "REGISTER", "DEVICE_LIST").
 * Roles are lowercase snake_case: "ios" | "android_tv".
 */

// ---------------------------------------------------------------------------
// Device roles
// ---------------------------------------------------------------------------

export type DeviceRole = "ios" | "android_tv";

// ---------------------------------------------------------------------------
// Base message envelope
// ---------------------------------------------------------------------------

export interface BaseMessage {
  /** Unique client ID assigned by the server on connection */
  clientId: string;
  /** ISO-8601 timestamp set by the sender */
  timestamp?: string;
}

// ---------------------------------------------------------------------------
// Server → client greeting
// ---------------------------------------------------------------------------

export interface ConnectedMessage {
  type: "CONNECTED";
  clientId: string;
}

// ---------------------------------------------------------------------------
// Registration (first message sent by every client after CONNECTED)
// ---------------------------------------------------------------------------

export interface RegisterMessage {
  type: "REGISTER";
  role: DeviceRole;
  /** Human-readable device name shown in discovery UI */
  name: string;
}

// ---------------------------------------------------------------------------
// Discovery
// ---------------------------------------------------------------------------

export interface DeviceInfo {
  clientId: string;
  name: string;
  role: DeviceRole;
}

/** Server → client: filtered list of connected peers */
export interface DeviceListMessage {
  type: "DEVICE_LIST";
  devices: DeviceInfo[];
}

// ---------------------------------------------------------------------------
// Pairing handshake
// ---------------------------------------------------------------------------

/** iOS → server → Android TV: request to establish a session */
export interface ConnectRequestMessage {
  type: "CONNECT_REQUEST";
  /** clientId of the Android TV to connect to */
  targetId: string;
}

/** Android TV → server → iOS: accept the connection request */
export interface ConnectAcceptMessage {
  type: "CONNECT_ACCEPT";
  /** clientId of the iOS device that sent CONNECT_REQUEST */
  targetId: string;
}

// ---------------------------------------------------------------------------
// WebRTC signaling
// ---------------------------------------------------------------------------

export interface OfferMessage {
  type: "OFFER";
  /** clientId of the intended receiver */
  targetId: string;
  sdp: RTCSessionDescriptionInit;
}

export interface AnswerMessage {
  type: "ANSWER";
  /** clientId of the offer originator */
  targetId: string;
  sdp: RTCSessionDescriptionInit;
}

export interface IceCandidateMessage {
  type: "ICE_CANDIDATE";
  targetId: string;
  candidate: RTCIceCandidateInit;
}

// ---------------------------------------------------------------------------
// Session control
// ---------------------------------------------------------------------------

export interface DisconnectMessage {
  type: "DISCONNECT";
  targetId: string;
}

export interface PeerDisconnectedMessage {
  type: "PEER_DISCONNECTED";
  clientId: string;
}

export interface ErrorMessage {
  type: "ERROR";
  code: string;
  message: string;
}

// ---------------------------------------------------------------------------
// Union type – covers every message that can travel over the WebSocket
// ---------------------------------------------------------------------------

export type SignalingMessage =
  | ConnectedMessage
  | RegisterMessage
  | DeviceListMessage
  | ConnectRequestMessage
  | ConnectAcceptMessage
  | OfferMessage
  | AnswerMessage
  | IceCandidateMessage
  | DisconnectMessage
  | PeerDisconnectedMessage
  | ErrorMessage;
