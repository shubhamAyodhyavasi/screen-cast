//
//  Device.swift
//  screen-cast
//
//  Created by shubham ayodhyavasi on 11/04/26.
//

import Foundation

/// Represents an Android TV device discovered via the signaling server.
///
/// `clientId` is assigned by the server and is stable for the lifetime of
/// the connection. `name` is the human-readable label set by the TV app.
struct Device: Identifiable, Codable, Equatable {
    /// Server-assigned unique identifier for this connection.
    let clientId: String
    /// Human-readable display name (e.g. "Living Room TV").
    let name: String
    /// Device role – always "android_tv" for entries shown in the picker.
    let role: String

    // Identifiable conformance
    var id: String { clientId }
}
