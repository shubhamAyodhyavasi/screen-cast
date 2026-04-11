import Foundation

// MARK: - Callbacks

/// Closure types used by WebSocketService to surface events to callers.
typealias DeviceListHandler     = ([Device]) -> Void
typealias ConnectAcceptHandler  = (String) -> Void       // fromId
typealias OfferHandler          = ([String: Any], String) -> Void
typealias AnswerHandler         = ([String: Any], String) -> Void
typealias IceCandidateHandler   = ([String: Any], String) -> Void
typealias DisconnectHandler     = (String) -> Void       // fromId

// MARK: - WebSocketService

/// Low-level WebSocket transport to the signaling server.
///
/// Responsibilities:
/// - Open / close the WebSocket connection
/// - Register the iOS device after the server acknowledges the connection
/// - Parse inbound JSON messages and invoke the appropriate callback
/// - Provide typed `send*` methods for outbound signaling messages
///
/// All callbacks are dispatched on the **main actor**.
@MainActor
final class WebSocketService: NSObject, ObservableObject {

    // MARK: Published state

    @Published var isConnected = false
    @Published var lastError: String?

    // MARK: Inbound event callbacks

    var onDeviceList:    DeviceListHandler?
    var onConnectAccept: ConnectAcceptHandler?
    var onOffer:         OfferHandler?
    var onAnswer:        AnswerHandler?
    var onIceCandidate:  IceCandidateHandler?
    var onDisconnect:    DisconnectHandler?
    var onPeerLeft:      DisconnectHandler?

    // MARK: Private

    private var webSocket: URLSessionWebSocketTask?
    private var session:   URLSession?
    private var clientId:  String?
    private let serverURL: URL

    /// - Parameter serverURL: WebSocket URL of the signaling server.
    ///   Override via `ws://<server-ip>:8080` for physical devices.
    init(serverURL: URL = URL(string: "ws://localhost:8080")!) {
        self.serverURL = serverURL
        super.init()
    }

    // MARK: - Connection management

    func connect() {
        session   = URLSession(configuration: .default, delegate: self, delegateQueue: nil)
        webSocket = session?.webSocketTask(with: serverURL)
        webSocket?.resume()
        receiveNext()
    }

    func disconnect() {
        webSocket?.cancel(with: .goingAway, reason: nil)
        webSocket   = nil
        isConnected = false
    }

    // MARK: - Outbound messages

    /// Register this device with the signaling server as an iOS sender.
    /// Called automatically after the server sends CONNECTED.
    private func sendRegister(deviceName: String) {
        send([
            "type": "REGISTER",
            "role": "ios",
            "name": deviceName,
        ])
    }

    /// Ask a specific Android TV device to accept a connection.
    func sendConnectRequest(to targetId: String) {
        send([
            "type":     "CONNECT_REQUEST",
            "targetId": targetId,
        ])
    }

    /// Send a WebRTC SDP offer to the target device.
    func sendOffer(sdp: [String: Any], to targetId: String) {
        send([
            "type":     "OFFER",
            "targetId": targetId,
            "sdp":      sdp,
        ])
    }

    /// Send a WebRTC SDP answer to the target device.
    func sendAnswer(sdp: [String: Any], to targetId: String) {
        send([
            "type":     "ANSWER",
            "targetId": targetId,
            "sdp":      sdp,
        ])
    }

    /// Send a WebRTC ICE candidate to the target device.
    func sendIceCandidate(_ candidate: [String: Any], to targetId: String) {
        send([
            "type":      "ICE_CANDIDATE",
            "targetId":  targetId,
            "candidate": candidate,
        ])
    }

    /// Notify the target that this side is disconnecting.
    func sendDisconnect(to targetId: String) {
        send([
            "type":     "DISCONNECT",
            "targetId": targetId,
        ])
    }

    // MARK: - Private helpers

    private func send(_ dict: [String: Any]) {
        guard let data = try? JSONSerialization.data(withJSONObject: dict),
              let text = String(data: data, encoding: .utf8)
        else { return }

        webSocket?.send(.string(text)) { [weak self] error in
            if let error {
                Task { @MainActor [weak self] in
                    self?.lastError = error.localizedDescription
                }
            }
        }
    }

    private func receiveNext() {
        webSocket?.receive { [weak self] result in
            guard let self else { return }
            Task { @MainActor in
                switch result {
                case .success(let msg):
                    self.handleRawMessage(msg)
                    self.receiveNext()
                case .failure(let error):
                    self.lastError  = error.localizedDescription
                    self.isConnected = false
                }
            }
        }
    }

    // MARK: - Inbound message dispatch

    private func handleRawMessage(_ message: URLSessionWebSocketTask.Message) {
        let text: String
        switch message {
        case .string(let s): text = s
        case .data(let d):   text = String(data: d, encoding: .utf8) ?? ""
        @unknown default:    return
        }

        guard let data = text.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let type_ = json["type"] as? String
        else { return }

        switch type_ {

        case "CONNECTED":
            // Server assigned us a clientId – register immediately.
            clientId    = json["clientId"] as? String
            isConnected = true
            sendRegister(deviceName: deviceName())

        case "DEVICE_LIST":
            // Decode the list of Android TV devices and surface to ViewModel.
            if let rawDevices = json["devices"] as? [[String: Any]],
               let devicesData = try? JSONSerialization.data(withJSONObject: rawDevices),
               let decoded = try? JSONDecoder().decode([Device].self, from: devicesData) {
                onDeviceList?(decoded.filter { $0.role == "android_tv" })
            }

        case "CONNECT_ACCEPT":
            if let from = json["fromId"] as? String {
                onConnectAccept?(from)
            }

        case "OFFER":
            if let sdp  = json["sdp"] as? [String: Any],
               let from = json["fromId"] as? String {
                onOffer?(sdp, from)
            }

        case "ANSWER":
            if let sdp  = json["sdp"] as? [String: Any],
               let from = json["fromId"] as? String {
                onAnswer?(sdp, from)
            }

        case "ICE_CANDIDATE":
            if let candidate = json["candidate"] as? [String: Any],
               let from      = json["fromId"] as? String {
                onIceCandidate?(candidate, from)
            }

        case "DISCONNECT":
            if let from = json["fromId"] as? String {
                onDisconnect?(from)
            }

        case "PEER_DISCONNECTED":
            if let id = json["clientId"] as? String {
                onPeerLeft?(id)
            }

        case "ERROR":
            lastError = json["message"] as? String

        default:
            break
        }
    }

    // MARK: - Helpers

    private func deviceName() -> String {
        #if canImport(UIKit)
        return UIDevice.current.name
        #else
        return Host.current().localizedName ?? "iOS Device"
        #endif
    }
}

// MARK: - URLSessionWebSocketDelegate

extension WebSocketService: URLSessionWebSocketDelegate {

    nonisolated func urlSession(
        _ session: URLSession,
        webSocketTask: URLSessionWebSocketTask,
        didOpenWithProtocol protocol: String?
    ) {
        Task { @MainActor [weak self] in
            self?.isConnected = true
        }
    }

    nonisolated func urlSession(
        _ session: URLSession,
        webSocketTask: URLSessionWebSocketTask,
        didCloseWith closeCode: URLSessionWebSocketTask.CloseCode,
        reason: Data?
    ) {
        Task { @MainActor [weak self] in
            self?.isConnected = false
        }
    }
}
