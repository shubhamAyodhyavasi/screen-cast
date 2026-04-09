/**
 * Shared protocol types for iOS ↔ Signaling Server ↔ Android TV communication.
 * Both the signaling server and any TypeScript-aware tooling should import from here.
 */

// ---------------------------------------------------------------------------
// Device roles
// ---------------------------------------------------------------------------

export type DeviceRole = "sender" | "receiver";

// ---------------------------------------------------------------------------
// Base message envelope
// ---------------------------------------------------------------------------

export interface BaseMessage {
  /** Unique client ID assigned by the server on connection */
  clientId: string;
  /** ISO-8601 timestamp set by the sender */
  timestamp: string;
}

// ---------------------------------------------------------------------------
// Registration (first message sent by every client)
// ---------------------------------------------------------------------------

export interface RegisterMessage extends BaseMessage {
  type: "register";
  role: DeviceRole;
  /** Human-readable device name shown in discovery UI */
  deviceName: string;
}

// ---------------------------------------------------------------------------
// Discovery
// ---------------------------------------------------------------------------

export interface DeviceInfo {
  clientId: string;
  deviceName: string;
  role: DeviceRole;
}

/** Server → client: current list of connected peers */
export interface DeviceListMessage {
  type: "device-list";
  devices: DeviceInfo[];
}

// ---------------------------------------------------------------------------
// WebRTC signaling
// ---------------------------------------------------------------------------

export interface OfferMessage extends BaseMessage {
  type: "offer";
  /** clientId of the intended receiver */
  targetId: string;
  sdp: RTCSessionDescriptionInit;
}

export interface AnswerMessage extends BaseMessage {
  type: "answer";
  /** clientId of the original sender (offer originator) */
  targetId: string;
  sdp: RTCSessionDescriptionInit;
}

export interface IceCandidateMessage extends BaseMessage {
  type: "ice-candidate";
  targetId: string;
  candidate: RTCIceCandidateInit;
}

// ---------------------------------------------------------------------------
// Session control
// ---------------------------------------------------------------------------

export interface HangupMessage extends BaseMessage {
  type: "hangup";
  targetId: string;
}

export interface ErrorMessage {
  type: "error";
  code: string;
  message: string;
}

// ---------------------------------------------------------------------------
// Union type – covers every message that can travel over the WebSocket
// ---------------------------------------------------------------------------

export type SignalingMessage =
  | RegisterMessage
  | DeviceListMessage
  | OfferMessage
  | AnswerMessage
  | IceCandidateMessage
  | HangupMessage
  | ErrorMessage;
