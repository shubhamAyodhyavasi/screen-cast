import SwiftUI

/// Root view – shows the device-discovery list and lets the user
/// start / stop a screen-cast session.
struct ContentView: View {

    @StateObject private var signalingClient = SignalingClient()
    @StateObject private var screenCapture   = ScreenCaptureManager()

    var body: some View {
        NavigationStack {
            Group {
                if signalingClient.connectedDevices.isEmpty {
                    emptyStateView
                } else {
                    deviceListView
                }
            }
            .navigationTitle("Screen Cast")
            .toolbar { connectionStatusToolbarItem }
            .onAppear {
                signalingClient.connect()
            }
            .onDisappear {
                signalingClient.disconnect()
            }
        }
    }

    // MARK: – Subviews

    private var emptyStateView: some View {
        VStack(spacing: 16) {
            Image(systemName: "tv.slash")
                .font(.system(size: 64))
                .foregroundColor(.secondary)
            Text("No Android TV found")
                .font(.title3)
                .foregroundColor(.secondary)
            Text("Make sure the Android TV app is running on the same network.")
                .multilineTextAlignment(.center)
                .foregroundColor(.secondary)
                .padding(.horizontal)
        }
    }

    private var deviceListView: some View {
        List(signalingClient.connectedDevices) { device in
            DeviceRow(device: device) {
                handleTap(device: device)
            }
        }
    }

    private var connectionStatusToolbarItem: some ToolbarContent {
        ToolbarItem(placement: .navigationBarTrailing) {
            HStack(spacing: 4) {
                Circle()
                    .frame(width: 8, height: 8)
                    .foregroundColor(signalingClient.isConnected ? .green : .red)
                Text(signalingClient.isConnected ? "Connected" : "Disconnected")
                    .font(.caption)
            }
        }
    }

    // MARK: – Actions

    private func handleTap(device: DeviceInfo) {
        if screenCapture.isCasting {
            screenCapture.stopCapture()
            signalingClient.sendHangup(to: device.clientId)
        } else {
            signalingClient.initiateCall(to: device.clientId)
        }
    }
}

// MARK: – DeviceRow

struct DeviceRow: View {
    let device: DeviceInfo
    let onTap: () -> Void

    var body: some View {
        HStack {
            Image(systemName: "appletv.fill")
                .foregroundColor(.accentColor)
            VStack(alignment: .leading) {
                Text(device.deviceName)
                    .font(.headline)
                Text(device.clientId)
                    .font(.caption2)
                    .foregroundColor(.secondary)
            }
            Spacer()
            Image(systemName: "dot.radiowaves.right")
                .foregroundColor(.accentColor)
        }
        .contentShape(Rectangle())
        .onTapGesture(perform: onTap)
    }
}

#Preview {
    ContentView()
}
