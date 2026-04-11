//
//  ContentView.swift
//  screen-cast
//
//  Created by shubham ayodhyavasi on 11/04/26.
//
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
