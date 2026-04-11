package com.screencast.androidtv

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

/**
 * SignalingClient
 *
 * Manages the WebSocket connection to the signaling server on behalf of
 * this Android TV device.
 *
 * Protocol (UPPERCASE message types):
 *   CONNECTED        ← server greeting with assigned clientId
 *   REGISTER         → announce role "android_tv" and device name
 *   DEVICE_LIST      ← current list of connected peers (informational)
 *   CONNECT_REQUEST  ← iOS device wants to pair
 *   CONNECT_ACCEPT   → accept the pairing request
 *   OFFER            ← WebRTC SDP offer from iOS (forwarded to onOffer)
 *   ANSWER           → WebRTC SDP answer (sent via sendAnswer)
 *   ICE_CANDIDATE    ↔ relayed via server
 *   DISCONNECT       ← iOS side terminated the session
 *   PEER_DISCONNECTED← server notifies us a peer left
 */
class SignalingClient(
    private val serverUrl:  String,
    private val deviceName: String,
) {
    // -------------------------------------------------------------------------
    // Connection state (exposed as StateFlow for ViewModel / Repository)
    // -------------------------------------------------------------------------

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    // -------------------------------------------------------------------------
    // Inbound signaling callbacks (set by SignalingRepository / WebRTCManager)
    // -------------------------------------------------------------------------

    /** Invoked when an iOS peer sends an SDP offer. */
    var onOffer: ((sdp: JSONObject, fromId: String) -> Unit)? = null

    /** Invoked when an iOS peer sends an ICE candidate. */
    var onIceCandidate: ((candidate: JSONObject, fromId: String) -> Unit)? = null

    /** Invoked when the iOS peer disconnects or sends DISCONNECT. */
    var onDisconnect: (() -> Unit)? = null

    // -------------------------------------------------------------------------
    // Private state
    // -------------------------------------------------------------------------

    private val scope      = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient = OkHttpClient()
    private var webSocket: WebSocket? = null
    private var clientId: String?     = null

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    fun connect() {
        _connectionState.value = ConnectionState.CONNECTING
        val request = Request.Builder().url(serverUrl).build()
        webSocket = httpClient.newWebSocket(request, SignalingListener())
    }

    fun disconnect() {
        webSocket?.close(1000, "App closed")
        scope.cancel()
        httpClient.dispatcher.executorService.shutdown()
    }

    /** Send CONNECT_ACCEPT to the iOS device that sent us a CONNECT_REQUEST. */
    fun sendConnectAccept(targetId: String) {
        send(JSONObject().apply {
            put("type",     "CONNECT_ACCEPT")
            put("targetId", targetId)
        })
    }

    /** Send a WebRTC SDP answer to the iOS peer. */
    fun sendAnswer(sdp: JSONObject, targetId: String) {
        send(JSONObject().apply {
            put("type",     "ANSWER")
            put("targetId", targetId)
            put("sdp",      sdp)
        })
    }

    /** Send a WebRTC ICE candidate to the iOS peer. */
    fun sendIceCandidate(candidate: JSONObject, targetId: String) {
        send(JSONObject().apply {
            put("type",      "ICE_CANDIDATE")
            put("targetId",  targetId)
            put("candidate", candidate)
        })
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun send(json: JSONObject) {
        webSocket?.send(json.toString())
    }

    private fun register() {
        send(JSONObject().apply {
            put("type", "REGISTER")
            put("role", "android_tv")
            put("name", deviceName)
        })
        _connectionState.value = ConnectionState.REGISTERED
    }

    // -------------------------------------------------------------------------
    // WebSocketListener
    // -------------------------------------------------------------------------

    private inner class SignalingListener : WebSocketListener() {

        override fun onOpen(ws: WebSocket, response: Response) {
            // Wait for the server's CONNECTED message before registering.
        }

        override fun onMessage(ws: WebSocket, text: String) {
            scope.launch {
                try {
                    handleMessage(JSONObject(text))
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "JSON parse error: ${e.message}")
                }
            }
        }

        override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
            _connectionState.value = ConnectionState.ERROR
            android.util.Log.e(TAG, "WebSocket failure: ${t.message}")
        }

        override fun onClosed(ws: WebSocket, code: Int, reason: String) {
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    // -------------------------------------------------------------------------
    // Inbound message dispatch
    // -------------------------------------------------------------------------

    private fun handleMessage(json: JSONObject) {
        when (val type = json.optString("type")) {

            // Server greeting – record our assigned clientId and register.
            "CONNECTED" -> {
                clientId = json.optString("clientId")
                register()
            }

            // Updated peer list (informational; logged for debugging).
            "DEVICE_LIST" -> {
                android.util.Log.d(TAG, "Device list: ${json.optJSONArray("devices")}")
            }

            // iOS device wants to pair with us – accept automatically.
            "CONNECT_REQUEST" -> {
                val fromId = json.optString("fromId")
                android.util.Log.d(TAG, "CONNECT_REQUEST from $fromId – accepting")
                sendConnectAccept(fromId)
            }

            // SDP offer from iOS peer – forward to WebRTCManager via callback.
            "OFFER" -> {
                val sdp    = json.optJSONObject("sdp") ?: return
                val fromId = json.optString("fromId")
                _connectionState.value = ConnectionState.STREAMING
                onOffer?.invoke(sdp, fromId)
            }

            // ICE candidate from iOS peer.
            "ICE_CANDIDATE" -> {
                val candidate = json.optJSONObject("candidate") ?: return
                val fromId    = json.optString("fromId")
                onIceCandidate?.invoke(candidate, fromId)
            }

            // iOS side terminated the session.
            "DISCONNECT" -> {
                _connectionState.value = ConnectionState.REGISTERED
                onDisconnect?.invoke()
            }

            // A peer left the signaling server.
            "PEER_DISCONNECTED" -> {
                _connectionState.value = ConnectionState.REGISTERED
                onDisconnect?.invoke()
            }

            "ERROR" -> android.util.Log.e(TAG, "Server error: ${json.optString("message")}")

            else -> android.util.Log.w(TAG, "Unknown message type: $type")
        }
    }

    companion object {
        private const val TAG = "SignalingClient"
    }
}
