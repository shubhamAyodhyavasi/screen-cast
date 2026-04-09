package com.screencast.androidtv

import android.util.Log
import org.json.JSONObject

/**
 * WebRTCManager (stub)
 *
 * Placeholder for the Android TV WebRTC receiver pipeline.
 *
 * In a full implementation this class would:
 *   1. Initialise a `PeerConnection` using the Google WebRTC library.
 *   2. Set the remote description from the incoming SDP offer.
 *   3. Create an SDP answer and send it via [SignalingRepository.sendAnswer].
 *   4. Apply received ICE candidates to the peer connection.
 *   5. Render the decoded video track onto a [android.view.SurfaceView].
 *
 * The interface matches what [MainViewModel] wires up via
 * [SignalingRepository] callbacks, so swapping in a real implementation
 * requires no changes outside this file.
 *
 * @param repository Used to send the SDP answer and ICE candidates back
 *                   to the iOS peer.
 */
class WebRTCManager(
    private val repository: SignalingRepository,
) {
    private var remotePeerId: String? = null

    // -------------------------------------------------------------------------
    // Called by the ViewModel (via SignalingRepository callbacks)
    // -------------------------------------------------------------------------

    /**
     * Handle an incoming SDP offer from the iOS sender.
     *
     * @param sdp    Raw SDP JSON object from the OFFER signaling message.
     * @param fromId ClientId of the iOS sender.
     */
    fun handleOffer(sdp: JSONObject, fromId: String) {
        remotePeerId = fromId
        Log.d(TAG, "Received OFFER from $fromId")

        // TODO:
        //   peerConnection.setRemoteDescription(SessionDescription(OFFER, sdp.getString("sdp")))
        //   peerConnection.createAnswer { answer ->
        //       peerConnection.setLocalDescription(answer)
        //       repository.sendAnswer(answer.toJSON(), fromId)
        //   }

        // Stub: send a placeholder answer so the signaling flow can be
        // exercised end-to-end before WebRTC is fully integrated.
        val stubAnswer = JSONObject().apply {
            put("type", "answer")
            put("sdp",  "stub-sdp")
        }
        repository.sendAnswer(stubAnswer, fromId)
        Log.d(TAG, "Sent stub ANSWER to $fromId")
    }

    /**
     * Apply an incoming ICE candidate from the iOS peer to the active
     * peer connection.
     *
     * @param candidate JSON object containing the ICE candidate fields.
     * @param fromId    ClientId of the iOS peer.
     */
    fun handleRemoteIceCandidate(candidate: JSONObject, fromId: String) {
        Log.d(TAG, "ICE_CANDIDATE from $fromId: $candidate")
        // TODO: peerConnection.addIceCandidate(IceCandidate(...))
    }

    /**
     * Tear down the current WebRTC session (peer disconnected or DISCONNECT
     * message received).
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
