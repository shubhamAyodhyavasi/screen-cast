package com.screencast.androidtv

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

/**
 * Manages the WebSocket connection to the signaling server.
 *
 * Responsibilities:
 * - Register as a "receiver" device.
 * - Route incoming offer / ICE-candidate messages to [WebRTCManager].
 * - Send answer / ICE-candidate messages back to the peer.
 */
class SignalingClient(
    private val serverUrl: String,
    private val deviceName: String,
    private val onStateChange: (ConnectionState) -> Unit,
    private val onError: (String) -> Unit,
) {

    // -------------------------------------------------------------------------
    // Dependencies
    // -------------------------------------------------------------------------

    /** Injected externally once the SurfaceView is ready. */
    var webRtcManager: WebRTCManager? = null

    // -------------------------------------------------------------------------
    // Private state
    // -------------------------------------------------------------------------

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient = OkHttpClient()
    private var webSocket: WebSocket? = null
    private var clientId: String? = null

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    fun connect() {
        onStateChange(ConnectionState.CONNECTING)
        val request = Request.Builder().url(serverUrl).build()
        webSocket = httpClient.newWebSocket(request, SignalingListener())
    }

    fun disconnect() {
        webSocket?.close(1000, "App closed")
        scope.cancel()
        httpClient.dispatcher.executorService.shutdown()
    }

    fun sendAnswer(sdp: JSONObject, targetId: String) {
        send(
            JSONObject().apply {
                put("type", "answer")
                put("clientId", clientId ?: "")
                put("timestamp", Instant.now().toString())
                put("targetId", targetId)
                put("sdp", sdp)
            }
        )
    }

    fun sendIceCandidate(candidate: JSONObject, targetId: String) {
        send(
            JSONObject().apply {
                put("type", "ice-candidate")
                put("clientId", clientId ?: "")
                put("timestamp", Instant.now().toString())
                put("targetId", targetId)
                put("candidate", candidate)
            }
        )
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun send(json: JSONObject) {
        webSocket?.send(json.toString())
    }

    private fun register() {
        send(
            JSONObject().apply {
                put("type", "register")
                put("clientId", clientId ?: UUID.randomUUID().toString())
                put("timestamp", Instant.now().toString())
                put("role", "receiver")
                put("deviceName", deviceName)
            }
        )
        onStateChange(ConnectionState.REGISTERED)
    }

    // -------------------------------------------------------------------------
    // WebSocketListener
    // -------------------------------------------------------------------------

    private inner class SignalingListener : WebSocketListener() {

        override fun onOpen(ws: WebSocket, response: Response) {
            // Wait for the server's "connected" message before registering.
        }

        override fun onMessage(ws: WebSocket, text: String) {
            scope.launch {
                try {
                    handleMessage(JSONObject(text))
                } catch (e: Exception) {
                    onError("JSON parse error: ${e.message}")
                }
            }
        }

        override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
            onStateChange(ConnectionState.ERROR)
            onError(t.message ?: "WebSocket error")
        }

        override fun onClosed(ws: WebSocket, code: Int, reason: String) {
            onStateChange(ConnectionState.DISCONNECTED)
        }
    }

    // -------------------------------------------------------------------------
    // Message dispatch
    // -------------------------------------------------------------------------

    private fun handleMessage(json: JSONObject) {
        when (val type = json.optString("type")) {
            "connected" -> {
                clientId = json.optString("clientId")
                register()
            }

            "device-list" -> {
                // Optionally show the sender list in the UI.
                val devices: JSONArray = json.optJSONArray("devices") ?: JSONArray()
                android.util.Log.d(TAG, "Device list updated: $devices")
            }

            "offer" -> {
                val sdp      = json.optJSONObject("sdp") ?: return
                val fromId   = json.optString("clientId")
                webRtcManager?.handleOffer(sdp, fromId)
                onStateChange(ConnectionState.STREAMING)
            }

            "ice-candidate" -> {
                val candidate = json.optJSONObject("candidate") ?: return
                val fromId    = json.optString("clientId")
                webRtcManager?.handleRemoteIceCandidate(candidate, fromId)
            }

            "hangup" -> {
                webRtcManager?.hangup()
                onStateChange(ConnectionState.REGISTERED)
            }

            "peer-disconnected" -> {
                webRtcManager?.hangup()
                onStateChange(ConnectionState.REGISTERED)
            }

            "error" -> onError(json.optString("message", "Unknown server error"))

            else -> android.util.Log.w(TAG, "Unknown message type: $type")
        }
    }

    companion object {
        private const val TAG = "SignalingClient"
    }
}
