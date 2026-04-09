package com.screencast.androidtv

import android.util.Log
import org.json.JSONObject

/**
 * Stub WebRTC manager for the Android TV receiver.
 *
 * In a production implementation this class would:
 * 1. Initialise a `PeerConnection` using the Google WebRTC library.
 * 2. Set the remote description from the incoming offer.
 * 3. Create an answer and send it via [SignalingClient].
 * 4. Apply received ICE candidates to the peer connection.
 * 5. Render the incoming video track onto a [android.view.SurfaceView].
 *
 * Integrating the full WebRTC SDK is beyond the scope of this skeleton
 * but the interface is fully defined here so the rest of the app can
 * compile and run without it.
 */
class WebRTCManager(
    private val signalingClient: SignalingClient,
) {

    private var remotePeerId: String? = null

    // -------------------------------------------------------------------------
    // Called by SignalingClient
    // -------------------------------------------------------------------------

    /**
     * Processes an incoming SDP offer from the iOS sender.
     * @param sdp    Raw SDP JSON object from the signaling message.
     * @param fromId ClientId of the iOS sender.
     */
    fun handleOffer(sdp: JSONObject, fromId: String) {
        remotePeerId = fromId
        Log.d(TAG, "Received offer from $fromId: $sdp")

        // TODO:
        //   peerConnection.setRemoteDescription(SessionDescription(OFFER, sdp.getString("sdp")))
        //   peerConnection.createAnswer { answer ->
        //       peerConnection.setLocalDescription(answer)
        //       signalingClient.sendAnswer(answer.toJSON(), fromId)
        //   }

        // Stub: acknowledge with a placeholder answer so the flow is logged.
        val stubAnswer = JSONObject().apply {
            put("type", "answer")
            put("sdp", "stub-sdp")
        }
        signalingClient.sendAnswer(stubAnswer, fromId)
        Log.d(TAG, "Sent stub answer to $fromId")
    }

    /**
     * Applies a received ICE candidate to the active peer connection.
     */
    fun handleRemoteIceCandidate(candidate: JSONObject, fromId: String) {
        Log.d(TAG, "ICE candidate from $fromId: $candidate")
        // TODO: peerConnection.addIceCandidate(IceCandidate(...))
    }

    /**
     * Tear down the current WebRTC session.
     */
    fun hangup() {
        Log.d(TAG, "Hangup – closing peer connection")
        remotePeerId = null
        // TODO: peerConnection.close()
    }

    companion object {
        private const val TAG = "WebRTCManager"
    }
}
