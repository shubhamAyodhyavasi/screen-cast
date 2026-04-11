//
//  DeviceListViewModel.swift
//  screen-cast
//
//  Created by shubham ayodhyavasi on 11/04/26.
//

import Foundation
import Combine

// MARK: - Connection state

/// Represents the current pairing state with an Android TV device.
enum PairingState: Equatable {
    case idle
    case requestSent(targetId: String)
    case connected(targetId: String)
}

// MARK: - DeviceListViewModel

/// ViewModel for the device-discovery screen.
///
/// Owns the `WebSocketService` and translates raw signaling events into
/// published state that the SwiftUI views can observe.
@MainActor
final class DeviceListViewModel: ObservableObject {

    // MARK: Published state

    /// Android TV devices currently online and available to connect to.
    @Published var availableDevices: [Device] = []

    /// Whether the WebSocket link to the signaling server is open.
    @Published var isServerConnected = false

    /// Current pairing state with an Android TV.
    @Published var pairingState: PairingState = .idle

    /// Latest error description, or nil when there is no error.
    @Published var errorMessage: String?

    // MARK: Private

    private let service = WebSocketService()
    private var cancellables = Set<AnyCancellable>()

    // MARK: - Initialization

    init() {
        bindService()
    }

    // MARK: - Public API

    /// Open the WebSocket connection to the signaling server and start
    /// receiving device-list updates.
    func start() {
        service.connect()
    }

    /// Close the WebSocket connection.
    func stop() {
        if case .connected(let targetId) = pairingState {
            service.sendDisconnect(to: targetId)
        }
        service.disconnect()
        pairingState = .idle
    }

    /// Send a CONNECT_REQUEST to the chosen Android TV device.
    /// The UI should update when CONNECT_ACCEPT arrives.
    func connectToDevice(_ device: Device) {
        guard pairingState == .idle else { return }
        service.sendConnectRequest(to: device.clientId)
        pairingState = .requestSent(targetId: device.clientId)
    }

    /// Disconnect from the currently paired Android TV.
    func disconnectFromCurrentDevice() {
        if case .connected(let targetId) = pairingState {
            service.sendDisconnect(to: targetId)
        }
        pairingState = .idle
    }

    // MARK: - Service wiring

    private func bindService() {
        // Mirror the service's isConnected flag.
        service.$isConnected
            .receive(on: RunLoop.main)
            .assign(to: &$isServerConnected)

        // Surface errors.
        service.$lastError
            .receive(on: RunLoop.main)
            .assign(to: &$errorMessage)

        // Receive updated Android TV device lists.
        service.onDeviceList = { [weak self] devices in
            self?.availableDevices = devices
        }

        // Android TV accepted our CONNECT_REQUEST → move to connected state.
        service.onConnectAccept = { [weak self] fromId in
            guard let self else { return }
            if case .requestSent(let targetId) = self.pairingState,
               targetId == fromId {
                self.pairingState = .connected(targetId: fromId)
            }
        }

        // Remote peer sent DISCONNECT.
        service.onDisconnect = { [weak self] _ in
            self?.pairingState = .idle
        }

        // Remote peer dropped off the signaling server.
        service.onPeerLeft = { [weak self] departedId in
            guard let self else { return }
            // Remove from the device list.
            self.availableDevices.removeAll { $0.clientId == departedId }
            // If we were paired with this device, reset state.
            if case .connected(let targetId) = self.pairingState, targetId == departedId {
                self.pairingState = .idle
            }
            if case .requestSent(let targetId) = self.pairingState, targetId == departedId {
                self.pairingState = .idle
            }
        }

        // WebRTC signaling callbacks are wired externally by WebRTCManager
        // once the pairing is established. Left as nil here.
    }
}
