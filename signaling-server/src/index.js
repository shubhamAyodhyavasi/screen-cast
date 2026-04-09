/**
 * screen-cast Signaling Server
 *
 * Responsibilities:
 *  1. Accept WebSocket connections from iOS senders and Android TV receivers.
 *  2. Assign each client a unique ID and broadcast an updated device list.
 *  3. Route WebRTC signaling messages (offer / answer / ICE candidate) between peers.
 *  4. Notify remaining peers when a client disconnects.
 */

"use strict";

const { WebSocketServer } = require("ws");
const { v4: uuidv4 } = require("uuid");

// ---------------------------------------------------------------------------
// Configuration
// ---------------------------------------------------------------------------

const PORT = parseInt(process.env.PORT || "8080", 10);
const HOST = process.env.HOST || "0.0.0.0";

// ---------------------------------------------------------------------------
// In-memory client registry
// ---------------------------------------------------------------------------

/**
 * @typedef {Object} ClientRecord
 * @property {string} clientId
 * @property {import('ws').WebSocket} socket
 * @property {'sender'|'receiver'} role
 * @property {string} deviceName
 * @property {boolean} registered
 */

/** @type {Map<string, ClientRecord>} */
const clients = new Map();

// ---------------------------------------------------------------------------
// Helper utilities
// ---------------------------------------------------------------------------

/**
 * Send a JSON payload to a single WebSocket client.
 * @param {import('ws').WebSocket} socket
 * @param {object} payload
 */
function sendJSON(socket, payload) {
  if (socket.readyState === socket.OPEN) {
    socket.send(JSON.stringify(payload));
  }
}

/**
 * Broadcast the current list of registered devices to all connected clients.
 */
function broadcastDeviceList() {
  const devices = [];
  for (const record of clients.values()) {
    if (record.registered) {
      devices.push({
        clientId: record.clientId,
        deviceName: record.deviceName,
        role: record.role,
      });
    }
  }

  const message = { type: "device-list", devices };

  for (const record of clients.values()) {
    if (record.registered) {
      sendJSON(record.socket, message);
    }
  }
}

/**
 * Route a point-to-point signaling message to its intended target.
 * @param {ClientRecord} sender
 * @param {object} message  – must contain a `targetId` field
 */
function routeToTarget(sender, message) {
  const target = clients.get(message.targetId);
  if (!target) {
    sendJSON(sender.socket, {
      type: "error",
      code: "TARGET_NOT_FOUND",
      message: `Client '${message.targetId}' is not connected.`,
    });
    return;
  }
  // Stamp the actual sender ID so the receiver knows who it came from.
  sendJSON(target.socket, { ...message, clientId: sender.clientId });
}

// ---------------------------------------------------------------------------
// WebSocket server
// ---------------------------------------------------------------------------

const wss = new WebSocketServer({ host: HOST, port: PORT });

wss.on("listening", () => {
  console.log(`[signaling-server] Listening on ws://${HOST}:${PORT}`);
});

wss.on("connection", (socket, req) => {
  const clientId = uuidv4();
  const remoteAddr = req.socket.remoteAddress;

  console.log(`[+] New connection  id=${clientId}  remote=${remoteAddr}`);

  /** @type {ClientRecord} */
  const record = {
    clientId,
    socket,
    role: "sender",
    deviceName: "Unknown",
    registered: false,
  };

  clients.set(clientId, record);

  // Immediately tell the client what its assigned ID is.
  sendJSON(socket, { type: "connected", clientId });

  // -------------------------------------------------------------------------
  // Message handling
  // -------------------------------------------------------------------------

  socket.on("message", (raw) => {
    let message;
    try {
      message = JSON.parse(raw.toString());
    } catch {
      sendJSON(socket, {
        type: "error",
        code: "INVALID_JSON",
        message: "Message could not be parsed as JSON.",
      });
      return;
    }

    console.log(
      `[→] id=${clientId}  type=${message.type ?? "(none)"}`,
    );

    switch (message.type) {
      // -----------------------------------------------------------------------
      case "register": {
        const { role, deviceName } = message;
        if (!role || !deviceName) {
          sendJSON(socket, {
            type: "error",
            code: "MISSING_FIELDS",
            message: "'role' and 'deviceName' are required for registration.",
          });
          return;
        }
        record.role = role;
        record.deviceName = deviceName;
        record.registered = true;
        console.log(
          `[✓] Registered  id=${clientId}  role=${role}  name="${deviceName}"`,
        );
        broadcastDeviceList();
        break;
      }

      // -----------------------------------------------------------------------
      case "offer":
      case "answer":
      case "ice-candidate":
      case "hangup": {
        if (!record.registered) {
          sendJSON(socket, {
            type: "error",
            code: "NOT_REGISTERED",
            message: "You must register before sending signaling messages.",
          });
          return;
        }
        routeToTarget(record, message);
        break;
      }

      // -----------------------------------------------------------------------
      default: {
        sendJSON(socket, {
          type: "error",
          code: "UNKNOWN_MESSAGE_TYPE",
          message: `Unknown message type: '${message.type}'.`,
        });
      }
    }
  });

  // -------------------------------------------------------------------------
  // Disconnect handling
  // -------------------------------------------------------------------------

  socket.on("close", () => {
    console.log(`[-] Disconnected  id=${clientId}`);
    clients.delete(clientId);

    // Notify remaining clients that this peer left.
    if (record.registered) {
      for (const other of clients.values()) {
        if (other.registered) {
          sendJSON(other.socket, {
            type: "peer-disconnected",
            clientId,
          });
        }
      }
      broadcastDeviceList();
    }
  });

  socket.on("error", (err) => {
    console.error(`[!] Socket error  id=${clientId}:`, err.message);
  });
});

wss.on("error", (err) => {
  console.error("[!] Server error:", err);
});
