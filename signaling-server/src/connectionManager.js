"use strict";

/**
 * ConnectionManager
 *
 * Maintains an in-memory registry of all connected WebSocket clients.
 * Each record tracks the socket, assigned role, device name, and
 * registration state.
 */

/**
 * @typedef {Object} ClientRecord
 * @property {string}               clientId
 * @property {import('ws').WebSocket} socket
 * @property {'ios'|'android_tv'}   role
 * @property {string}               name       – human-readable device name
 * @property {boolean}              registered – true after REGISTER received
 */

class ConnectionManager {
  constructor() {
    /** @type {Map<string, ClientRecord>} */
    this._clients = new Map();
  }

  // ---------------------------------------------------------------------------
  // Lifecycle
  // ---------------------------------------------------------------------------

  /**
   * Add a newly-connected (not yet registered) client.
   * @param {string}                clientId
   * @param {import('ws').WebSocket} socket
   */
  add(clientId, socket) {
    this._clients.set(clientId, {
      clientId,
      socket,
      role: "ios",
      name: "Unknown",
      registered: false,
    });
  }

  /**
   * Remove a client (called on socket close).
   * @param {string} clientId
   */
  remove(clientId) {
    this._clients.delete(clientId);
  }

  // ---------------------------------------------------------------------------
  // Registration
  // ---------------------------------------------------------------------------

  /**
   * Mark a client as registered and set its role + display name.
   * @param {string}              clientId
   * @param {'ios'|'android_tv'}  role
   * @param {string}              name
   * @returns {ClientRecord|null} the updated record, or null if not found
   */
  register(clientId, role, name) {
    const record = this._clients.get(clientId);
    if (!record) return null;
    record.role       = role;
    record.name       = name;
    record.registered = true;
    return record;
  }

  // ---------------------------------------------------------------------------
  // Queries
  // ---------------------------------------------------------------------------

  /**
   * Return the record for a single client, or undefined.
   * @param {string} clientId
   * @returns {ClientRecord|undefined}
   */
  get(clientId) {
    return this._clients.get(clientId);
  }

  /**
   * Return all registered clients.
   * @returns {ClientRecord[]}
   */
  getRegistered() {
    return [...this._clients.values()].filter((r) => r.registered);
  }

  /**
   * Return all registered clients with the given role.
   * @param {'ios'|'android_tv'} role
   * @returns {ClientRecord[]}
   */
  getByRole(role) {
    return this.getRegistered().filter((r) => r.role === role);
  }

  /**
   * Serialize the registered device list for wire transmission.
   * @returns {{ clientId: string, name: string, role: string }[]}
   */
  toDeviceList() {
    return this.getRegistered().map(({ clientId, name, role }) => ({
      clientId,
      name,
      role,
    }));
  }
}

module.exports = { ConnectionManager };
