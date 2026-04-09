import Foundation
import Combine

// MARK: – Domain model

/// Mirrors the `DeviceInfo` type defined in shared-protocol.
struct DeviceInfo: Identifiable, Decodable {
    var id: String { clientId }
    let clientId: String
    let deviceName: String
    let role: String
}

// MARK: – SignalingClient

/// Manages the WebSocket connection to the signaling server and exposes
/// a Combine-friendly interface to the rest of the app.
@MainActor
final class SignalingClient: NSObject, ObservableObject {

    // MARK: Published state

    @Published var isConnected       = false
    @Published var connectedDevices  : [DeviceInfo] = []
    @Published var lastError         : String?

    // MARK: WebRTC callbacks (set by ScreenCaptureManager / WebRTCManager)

    var onOffer         : (([String: Any], String) -> Void)?
    var onAnswer        : (([String: Any], String) -> Void)?
    var onIceCandidate  : (([String: Any], String) -> Void)?
    var onHangup        : ((String) -> Void)?

    // MARK: Private

    private var webSocket : URLSessionWebSocketTask?
    private var session   : URLSession?
    private var clientId  : String?

    private let serverURL: URL

    init(serverURL: URL = URL(string: "ws://localhost:8080")!) {
        self.serverURL = serverURL
        super.init()
    }

    // MARK: – Connection management

    func connect() {
        session  = URLSession(configuration: .default, delegate: self, delegateQueue: nil)
        webSocket = session?.webSocketTask(with: serverURL)
        webSocket?.resume()
        receiveNext()
    }

    func disconnect() {
        webSocket?.cancel(with: .goingAway, reason: nil)
        webSocket  = nil
        isConnected = false
    }

    // MARK: – Outbound messages

    func register(deviceName: String = UIDevice.current.name) {
        guard let clientId else { return }
        send([
            "type":       "register",
            "clientId":   clientId,
            "timestamp":  iso8601Now(),
            "role":       "sender",
            "deviceName": deviceName,
        ])
    }

    func initiateCall(to targetId: String) {
        // WebRTCManager will call sendOffer after creating the local description.
    }

    func sendOffer(sdp: [String: Any], to targetId: String) {
        guard let clientId else { return }
        send([
            "type":      "offer",
            "clientId":  clientId,
            "timestamp": iso8601Now(),
            "targetId":  targetId,
            "sdp":       sdp,
        ])
    }

    func sendAnswer(sdp: [String: Any], to targetId: String) {
        guard let clientId else { return }
        send([
            "type":      "answer",
            "clientId":  clientId,
            "timestamp": iso8601Now(),
            "targetId":  targetId,
            "sdp":       sdp,
        ])
    }

    func sendIceCandidate(_ candidate: [String: Any], to targetId: String) {
        guard let clientId else { return }
        send([
            "type":        "ice-candidate",
            "clientId":    clientId,
            "timestamp":   iso8601Now(),
            "targetId":    targetId,
            "candidate":   candidate,
        ])
    }

    func sendHangup(to targetId: String) {
        guard let clientId else { return }
        send([
            "type":      "hangup",
            "clientId":  clientId,
            "timestamp": iso8601Now(),
            "targetId":  targetId,
        ])
    }

    // MARK: – Private helpers

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
                case .success(let message):
                    self.handleMessage(message)
                    self.receiveNext()
                case .failure(let error):
                    self.lastError = error.localizedDescription
                    self.isConnected = false
                }
            }
        }
    }

    private func handleMessage(_ message: URLSessionWebSocketTask.Message) {
        let text: String
        switch message {
        case .string(let s): text = s
        case .data(let d):   text = String(data: d, encoding: .utf8) ?? ""; break
        @unknown default:    return
        }

        guard let data = text.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let type_ = json["type"] as? String
        else { return }

        switch type_ {
        case "connected":
            clientId    = json["clientId"] as? String
            isConnected = true
            register()

        case "device-list":
            if let devicesJSON = json["devices"] as? [[String: Any]],
               let devicesData = try? JSONSerialization.data(withJSONObject: devicesJSON),
               let decoded = try? JSONDecoder().decode([DeviceInfo].self, from: devicesData) {
                connectedDevices = decoded.filter { $0.role == "receiver" }
            }

        case "offer":
            if let sdp = json["sdp"] as? [String: Any],
               let from = json["clientId"] as? String {
                onOffer?(sdp, from)
            }

        case "answer":
            if let sdp = json["sdp"] as? [String: Any],
               let from = json["clientId"] as? String {
                onAnswer?(sdp, from)
            }

        case "ice-candidate":
            if let candidate = json["candidate"] as? [String: Any],
               let from = json["clientId"] as? String {
                onIceCandidate?(candidate, from)
            }

        case "hangup":
            if let from = json["clientId"] as? String {
                onHangup?(from)
            }

        case "peer-disconnected":
            if let id = json["clientId"] as? String {
                connectedDevices.removeAll { $0.clientId == id }
            }

        case "error":
            lastError = json["message"] as? String

        default:
            break
        }
    }

    private func iso8601Now() -> String {
        ISO8601DateFormatter().string(from: Date())
    }
}

// MARK: – URLSessionWebSocketDelegate

extension SignalingClient: URLSessionWebSocketDelegate {
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
