/**
 * screen-cast Signaling Server – WebSocket server bootstrap
 *
 * Wires together the ConnectionManager and MessageHandler modules,
 * then starts listening for incoming WebSocket connections.
 *
 * Message protocol uses UPPERCASE type strings:
 *   REGISTER, DEVICE_LIST, CONNECT_REQUEST, CONNECT_ACCEPT,
 *   OFFER, ANSWER, ICE_CANDIDATE, DISCONNECT
 *
 * Roles: "ios" (sender) | "android_tv" (receiver)
 */

"use strict";

const { WebSocketServer } = require("ws");
const { v4: uuidv4 }      = require("uuid");

const { ConnectionManager } = require("./connectionManager");
const { MessageHandler, sendJSON } = require("./messageHandler");

// ---------------------------------------------------------------------------
// Configuration
// ---------------------------------------------------------------------------

const PORT = parseInt(process.env.PORT || "8080", 10);
const HOST = process.env.HOST || "0.0.0.0";

// ---------------------------------------------------------------------------
// Bootstrap
// ---------------------------------------------------------------------------

const connectionManager = new ConnectionManager();
const messageHandler    = new MessageHandler(connectionManager);

const wss = new WebSocketServer({ host: HOST, port: PORT });

wss.on("listening", () => {
  console.log(`[signaling-server] Listening on ws://${HOST}:${PORT}`);
});

wss.on("connection", (socket, req) => {
  const clientId   = uuidv4();
  const remoteAddr = req.socket.remoteAddress;

  console.log(`[+] New connection  id=${clientId}  remote=${remoteAddr}`);

  // Register the raw socket in the connection manager.
  connectionManager.add(clientId, socket);

  // Tell the client what ID the server assigned to it.
  sendJSON(socket, { type: "CONNECTED", clientId });

  // -------------------------------------------------------------------------
  // Inbound message dispatch
  // -------------------------------------------------------------------------

  socket.on("message", (raw) => {
    let message;
    try {
      message = JSON.parse(raw.toString());
    } catch {
      sendJSON(socket, {
        type: "ERROR",
        code: "INVALID_JSON",
        message: "Message could not be parsed as JSON.",
      });
      return;
    }

    console.log(`[→] id=${clientId}  type=${message.type ?? "(none)"}`);
    messageHandler.handle(clientId, message);
  });

  // -------------------------------------------------------------------------
  // Disconnect handling
  // -------------------------------------------------------------------------

  socket.on("close", () => {
    console.log(`[-] Disconnected  id=${clientId}`);
    messageHandler.onClientDisconnect(clientId);
  });

  socket.on("error", (err) => {
    console.error(`[!] Socket error  id=${clientId}:`, err.message);
  });
});

wss.on("error", (err) => {
  console.error("[!] Server error:", err);
});
