"use strict";

/**
 * MessageHandler
 *
 * Routes incoming signaling messages between clients.
 * All message type strings follow the UPPERCASE convention defined in
 * shared-protocol (REGISTER, DEVICE_LIST, CONNECT_REQUEST, …).
 */

const { ConnectionManager } = require("./connectionManager");

/**
 * Send a JSON payload to a single socket (no-op if socket is not open).
 * @param {import('ws').WebSocket} socket
 * @param {object} payload
 */
function sendJSON(socket, payload) {
  if (socket.readyState === socket.OPEN) {
    socket.send(JSON.stringify(payload));
  }
}

class MessageHandler {
  /**
   * @param {ConnectionManager} connectionManager
   */
  constructor(connectionManager) {
    this._cm = connectionManager;
  }

  // ---------------------------------------------------------------------------
  // Entry point – called for every inbound message
  // ---------------------------------------------------------------------------

  /**
   * Dispatch an incoming message to the appropriate handler.
   * @param {string} clientId  – sender's assigned ID
   * @param {object} message   – parsed JSON payload
   */
  handle(clientId, message) {
    const record = this._cm.get(clientId);
    if (!record) return; // should not happen

    switch (message.type) {
      case "REGISTER":
        this._handleRegister(record, message);
        break;

      case "CONNECT_REQUEST":
        this._handleConnectRequest(record, message);
        break;

      case "CONNECT_ACCEPT":
        this._handleConnectAccept(record, message);
        break;

      case "OFFER":
        this._relay(record, message);
        break;

      case "ANSWER":
        this._relay(record, message);
        break;

      case "ICE_CANDIDATE":
        this._relay(record, message);
        break;

      case "DISCONNECT":
        this._handleDisconnect(record, message);
        break;

      default:
        sendJSON(record.socket, {
          type: "ERROR",
          code: "UNKNOWN_MESSAGE_TYPE",
          message: `Unknown message type: '${message.type}'.`,
        });
    }
  }

  // ---------------------------------------------------------------------------
  // Called by the server when a client disconnects
  // ---------------------------------------------------------------------------

  /**
   * Notify remaining peers that a client has left and refresh device lists.
   * @param {string} clientId
   */
  onClientDisconnect(clientId) {
    const record = this._cm.get(clientId);
    const wasRegistered = record?.registered ?? false;

    this._cm.remove(clientId);

    if (wasRegistered) {
      // Tell everyone still connected that this peer left.
      for (const other of this._cm.getRegistered()) {
        sendJSON(other.socket, { type: "PEER_DISCONNECTED", clientId });
      }
      // Refresh device lists.
      this._broadcastDeviceList();
    }
  }

  // ---------------------------------------------------------------------------
  // Private handlers
  // ---------------------------------------------------------------------------

  /**
   * REGISTER – client announces its role and display name.
   * iOS receives the current list of Android TV devices.
   * Android TV registration triggers a DEVICE_LIST update to all iOS clients.
   */
  _handleRegister(record, message) {
    const { role, name } = message;

    if (!role || !name) {
      sendJSON(record.socket, {
        type: "ERROR",
        code: "MISSING_FIELDS",
        message: "'role' and 'name' are required for REGISTER.",
      });
      return;
    }

    if (role !== "ios" && role !== "android_tv") {
      sendJSON(record.socket, {
        type: "ERROR",
        code: "INVALID_ROLE",
        message: "Role must be 'ios' or 'android_tv'.",
      });
      return;
    }

    this._cm.register(record.clientId, role, name);
    console.log(
      `[✓] REGISTER  id=${record.clientId}  role=${role}  name="${name}"`,
    );

    if (role === "ios") {
      // Send this iOS client the current list of Android TV devices.
      this._sendDeviceListTo(record, "android_tv");
    } else {
      // Notify every connected iOS client that a new TV is available.
      for (const iosClient of this._cm.getByRole("ios")) {
        this._sendDeviceListTo(iosClient, "android_tv");
      }
    }
  }

  /**
   * CONNECT_REQUEST – iOS asks to connect to a specific Android TV.
   * The server relays the request to the target TV.
   */
  _handleConnectRequest(record, message) {
    if (!this._assertRegistered(record)) return;

    const target = this._cm.get(message.targetId);
    if (!target) {
      sendJSON(record.socket, {
        type: "ERROR",
        code: "TARGET_NOT_FOUND",
        message: `Device '${message.targetId}' is not connected.`,
      });
      return;
    }

    sendJSON(target.socket, {
      type: "CONNECT_REQUEST",
      fromId: record.clientId,
      fromName: record.name,
    });
  }

  /**
   * CONNECT_ACCEPT – Android TV accepts the incoming connection request.
   * Relayed back to the iOS client that initiated the request.
   */
  _handleConnectAccept(record, message) {
    if (!this._assertRegistered(record)) return;
    this._relay(record, message);
  }

  /**
   * DISCONNECT – explicit teardown signal; relay to the target peer.
   */
  _handleDisconnect(record, message) {
    if (!this._assertRegistered(record)) return;
    this._relay(record, message);
  }

  // ---------------------------------------------------------------------------
  // Utility
  // ---------------------------------------------------------------------------

  /**
   * Forward a point-to-point message to its `targetId` recipient,
   * stamping the real sender's clientId onto the envelope.
   */
  _relay(senderRecord, message) {
    if (!this._assertRegistered(senderRecord)) return;

    const target = this._cm.get(message.targetId);
    if (!target) {
      sendJSON(senderRecord.socket, {
        type: "ERROR",
        code: "TARGET_NOT_FOUND",
        message: `Device '${message.targetId}' is not connected.`,
      });
      return;
    }

    // Stamp the real sender so the receiver knows who it came from.
    sendJSON(target.socket, { ...message, fromId: senderRecord.clientId });
  }

  /**
   * Broadcast DEVICE_LIST to all registered clients (used after any
   * state change that affects the device roster).
   */
  _broadcastDeviceList() {
    const devices = this._cm.toDeviceList();
    const message = { type: "DEVICE_LIST", devices };
    for (const c of this._cm.getRegistered()) {
      sendJSON(c.socket, message);
    }
  }

  /**
   * Send a filtered DEVICE_LIST to one specific client.
   * @param {import('./connectionManager').ClientRecord} targetRecord – recipient
   * @param {'ios'|'android_tv'} filterRole – only include devices of this role
   */
  _sendDeviceListTo(targetRecord, filterRole) {
    const devices = this._cm
      .getByRole(filterRole)
      .map(({ clientId, name, role }) => ({ clientId, name, role }));
    sendJSON(targetRecord.socket, { type: "DEVICE_LIST", devices });
  }

  /**
   * Reject the message if the sender has not registered yet.
   * @returns {boolean} true if registered
   */
  _assertRegistered(record) {
    if (!record.registered) {
      sendJSON(record.socket, {
        type: "ERROR",
        code: "NOT_REGISTERED",
        message: "You must send REGISTER before other messages.",
      });
      return false;
    }
    return true;
  }
}

module.exports = { MessageHandler, sendJSON };
