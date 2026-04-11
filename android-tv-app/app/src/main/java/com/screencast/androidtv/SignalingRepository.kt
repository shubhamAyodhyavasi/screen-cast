package com.screencast.androidtv

import kotlinx.coroutines.flow.StateFlow

/**
 * SignalingRepository
 *
 * Repository layer between [MainViewModel] and [SignalingClient].
 *
 * Responsibilities:
 * - Own the [SignalingClient] instance and expose its connection state
 *   as a [StateFlow] that ViewModels can collect.
 * - Provide typed, named methods for every outbound signaling action
 *   so that the ViewModel never talks directly to the WebSocket layer.
 * - Accept optional callbacks for WebRTC signaling events (offer /
 *   answer / ICE candidate) that are wired in once a peer connection
 *   is being set up.
 */
class SignalingRepository(serverUrl: String, deviceName: String) {

    // -------------------------------------------------------------------------
    // State (delegated to SignalingClient)
    // -------------------------------------------------------------------------

    /** Observable connection / registration state. */
    val connectionState: StateFlow<ConnectionState>
        get() = signalingClient.connectionState

    // -------------------------------------------------------------------------
    // Private
    // -------------------------------------------------------------------------

    private val signalingClient = SignalingClient(
        serverUrl  = serverUrl,
        deviceName = deviceName,
    )

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /** Connect to the signaling server and register as an Android TV. */
    fun connect() = signalingClient.connect()

    /** Gracefully close the WebSocket connection. */
    fun disconnect() = signalingClient.disconnect()

    // -------------------------------------------------------------------------
    // Outbound signaling actions
    // -------------------------------------------------------------------------

    /**
     * Accept an incoming connection request from an iOS device.
     * @param targetId clientId of the iOS device that sent CONNECT_REQUEST.
     */
    fun acceptConnection(targetId: String) =
        signalingClient.sendConnectAccept(targetId)

    /**
     * Send a WebRTC SDP answer to the iOS peer.
     * @param sdp      JSON object containing the SDP answer fields.
     * @param targetId clientId of the iOS peer (offer originator).
     */
    fun sendAnswer(sdp: org.json.JSONObject, targetId: String) =
        signalingClient.sendAnswer(sdp, targetId)

    /**
     * Send a WebRTC ICE candidate to the iOS peer.
     * @param candidate JSON object with the ICE candidate fields.
     * @param targetId  clientId of the iOS peer.
     */
    fun sendIceCandidate(candidate: org.json.JSONObject, targetId: String) =
        signalingClient.sendIceCandidate(candidate, targetId)

    // -------------------------------------------------------------------------
    // Inbound signaling callbacks (wired by ViewModel / WebRTCManager)
    // -------------------------------------------------------------------------

    /**
     * Called when the iOS peer sends an SDP offer.
     * Set this before calling [connect] if you need to handle offers.
     */
    var onOffer: ((sdp: org.json.JSONObject, fromId: String) -> Unit)?
        get()  = signalingClient.onOffer
        set(v) { signalingClient.onOffer = v }

    /**
     * Called when the iOS peer sends an ICE candidate.
     */
    var onIceCandidate: ((candidate: org.json.JSONObject, fromId: String) -> Unit)?
        get()  = signalingClient.onIceCandidate
        set(v) { signalingClient.onIceCandidate = v }

    /**
     * Called when the iOS peer sends DISCONNECT or leaves the server.
     */
    var onDisconnect: (() -> Unit)?
        get()  = signalingClient.onDisconnect
        set(v) { signalingClient.onDisconnect = v }
}
