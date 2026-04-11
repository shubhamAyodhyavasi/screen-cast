//
//  SignalingClient.swift
//  screen-cast
//
//  Created by shubham ayodhyavasi on 11/04/26.
//

// SignalingClient.swift
//
// This file is retained for reference only.
// The active signaling implementation has been moved to:
//
//   Services/WebSocketService.swift   – WebSocket transport layer
//   ViewModels/DeviceListViewModel.swift – MVVM ViewModel
//
// WebSocketService uses the UPPERCASE message protocol defined in
// the shared-protocol package:
//
//   CONNECTED, REGISTER, DEVICE_LIST, CONNECT_REQUEST,
//   CONNECT_ACCEPT, OFFER, ANSWER, ICE_CANDIDATE, DISCONNECT
//
// Roles: "ios" (this app) | "android_tv" (receiver)

