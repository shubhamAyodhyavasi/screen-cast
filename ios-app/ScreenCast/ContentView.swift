import SwiftUI

/// Root view – creates the shared ViewModel and hosts `DeviceListView`.
struct ContentView: View {

    @StateObject private var viewModel = DeviceListViewModel()

    var body: some View {
        DeviceListView(viewModel: viewModel)
    }
}

#Preview {
    ContentView()
}
