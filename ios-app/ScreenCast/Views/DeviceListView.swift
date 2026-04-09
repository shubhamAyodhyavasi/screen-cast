import SwiftUI

// MARK: - DeviceListView

/// Primary screen that shows discovered Android TV devices and lets the
/// user initiate or cancel a connection.
struct DeviceListView: View {

    @ObservedObject var viewModel: DeviceListViewModel

    var body: some View {
        NavigationStack {
            Group {
                switch viewModel.pairingState {
                case .connected(let targetId):
                    connectedView(targetId: targetId)
                default:
                    discoveryView
                }
            }
            .navigationTitle("Screen Cast")
            .toolbar { serverStatusBadge }
            .onAppear  { viewModel.start() }
            .onDisappear { viewModel.stop() }
        }
    }

    // MARK: – Discovery state

    private var discoveryView: some View {
        Group {
            if viewModel.availableDevices.isEmpty {
                emptyStateView
            } else {
                deviceList
            }
        }
    }

    private var emptyStateView: some View {
        VStack(spacing: 16) {
            Image(systemName: "tv.slash")
                .font(.system(size: 64))
                .foregroundStyle(.secondary)
            Text("No Android TV found")
                .font(.title3)
                .foregroundStyle(.secondary)
            Text("Make sure the Android TV app is running on the same network.")
                .multilineTextAlignment(.center)
                .foregroundStyle(.secondary)
                .padding(.horizontal)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var deviceList: some View {
        List(viewModel.availableDevices) { device in
            DeviceRow(
                device: device,
                isPending: {
                    if case .requestSent(let id) = viewModel.pairingState {
                        return id == device.clientId
                    }
                    return false
                }()
            ) {
                viewModel.connectToDevice(device)
            }
        }
    }

    // MARK: – Connected state

    private func connectedView(targetId: String) -> some View {
        let deviceName = viewModel.availableDevices
            .first { $0.clientId == targetId }?.name ?? targetId

        return VStack(spacing: 24) {
            Image(systemName: "appletv.fill")
                .font(.system(size: 64))
                .foregroundStyle(.green)

            Text("Connected to")
                .font(.title3)
                .foregroundStyle(.secondary)
            Text(deviceName)
                .font(.title2.bold())

            Button(role: .destructive) {
                viewModel.disconnectFromCurrentDevice()
            } label: {
                Label("Disconnect", systemImage: "xmark.circle")
                    .padding(.horizontal, 20)
                    .padding(.vertical, 10)
            }
            .buttonStyle(.borderedProminent)
            .tint(.red)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    // MARK: – Toolbar

    private var serverStatusBadge: some ToolbarContent {
        ToolbarItem(placement: .navigationBarTrailing) {
            HStack(spacing: 4) {
                Circle()
                    .frame(width: 8, height: 8)
                    .foregroundStyle(viewModel.isServerConnected ? Color.green : Color.red)
                Text(viewModel.isServerConnected ? "Server connected" : "Disconnected")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }
}

// MARK: - DeviceRow

private struct DeviceRow: View {
    let device: Device
    let isPending: Bool
    let onTap: () -> Void

    var body: some View {
        HStack {
            Image(systemName: "appletv.fill")
                .foregroundStyle(.accent)
            VStack(alignment: .leading) {
                Text(device.name)
                    .font(.headline)
                Text(device.clientId)
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            if isPending {
                ProgressView()
                    .controlSize(.small)
            } else {
                Image(systemName: "dot.radiowaves.right")
                    .foregroundStyle(.accent)
            }
        }
        .contentShape(Rectangle())
        .onTapGesture(perform: onTap)
        .disabled(isPending)
    }
}

#Preview {
    let vm = DeviceListViewModel()
    return DeviceListView(viewModel: vm)
}
