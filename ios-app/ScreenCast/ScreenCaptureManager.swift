import Foundation
import ReplayKit
import Combine

/// Manages screen capture via `RPScreenRecorder` and feeds the
/// captured frames into the WebRTC pipeline.
///
/// Full WebRTC integration requires linking the `GoogleWebRTC` or
/// `WebRTC.xcframework` SDK; this class shows the integration surface
/// and can be compiled without that dependency in stub mode.
@MainActor
final class ScreenCaptureManager: ObservableObject {

    // MARK: Published state

    @Published var isCasting = false
    @Published var lastError : String?

    // MARK: Private

    private let recorder = RPScreenRecorder.shared()

    // MARK: – Public API

    /// Start capturing the screen and encoding it through WebRTC.
    func startCapture() {
        guard recorder.isAvailable else {
            lastError = "Screen recording is not available on this device."
            return
        }

        recorder.startCapture { sampleBuffer, bufferType, error in
            if let error {
                Task { @MainActor [weak self] in
                    self?.lastError = error.localizedDescription
                    self?.isCasting = false
                }
                return
            }
            // TODO: feed `sampleBuffer` into WebRTCManager video track.
            _ = sampleBuffer
            _ = bufferType
        } completionHandler: { [weak self] error in
            Task { @MainActor [weak self] in
                if let error {
                    self?.lastError = error.localizedDescription
                } else {
                    self?.isCasting = true
                }
            }
        }
    }

    /// Stop capturing and clean up resources.
    func stopCapture() {
        recorder.stopCapture { [weak self] error in
            Task { @MainActor [weak self] in
                if let error {
                    self?.lastError = error.localizedDescription
                }
                self?.isCasting = false
            }
        }
    }
}
