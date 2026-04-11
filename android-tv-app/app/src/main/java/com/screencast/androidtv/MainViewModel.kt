package com.screencast.androidtv

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

/**
 * MainViewModel
 *
 * Drives [MainFragment]. Owns the [SignalingRepository] lifecycle and
 * translates repository state into LiveData for the UI layer.
 *
 * To point this app at a real server, change [SERVER_URL] below or
 * inject it via a build config / environment variable.
 */
class MainViewModel : ViewModel() {

    companion object {
        /**
         * Address of the signaling server.
         * - Emulator: "ws://10.0.2.2:8080"  (localhost loopback)
         * - Physical device: "ws://<your-server-ip>:8080"
         */
        private const val SERVER_URL  = "ws://10.0.2.2:8080"
        private const val DEVICE_NAME = "Android TV"
    }

    // -------------------------------------------------------------------------
    // Repository
    // -------------------------------------------------------------------------

    private val repository = SignalingRepository(
        serverUrl  = SERVER_URL,
        deviceName = DEVICE_NAME,
    )

    // -------------------------------------------------------------------------
    // Exposed state
    // -------------------------------------------------------------------------

    /**
     * Connection / registration state as LiveData so [MainFragment] can
     * observe it on the main thread without additional boilerplate.
     */
    val connectionState: LiveData<ConnectionState> =
        repository.connectionState.asLiveData(viewModelScope.coroutineContext)

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    init {
        // Wire WebRTC signaling callbacks through the repository.
        repository.onOffer = { sdp, fromId ->
            // TODO: pass to WebRTCManager once it is integrated.
            android.util.Log.d("MainViewModel", "Offer from $fromId: $sdp")
        }
        repository.onIceCandidate = { candidate, fromId ->
            android.util.Log.d("MainViewModel", "ICE from $fromId: $candidate")
        }
        repository.onDisconnect = {
            android.util.Log.d("MainViewModel", "Peer disconnected")
        }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Connect to the signaling server (called from the Fragment). */
    fun connect() {
        viewModelScope.launch {
            repository.connect()
        }
    }

    /** Gracefully disconnect (called from the Fragment on destroy). */
    fun disconnect() {
        repository.disconnect()
    }

    override fun onCleared() {
        super.onCleared()
        repository.disconnect()
    }
}

// ---------------------------------------------------------------------------
// Supporting types
// ---------------------------------------------------------------------------

enum class ConnectionState(val label: String) {
    DISCONNECTED("Waiting for connection…"),
    CONNECTING("Connecting to signaling server…"),
    REGISTERED("Registered – waiting for iOS sender…"),
    STREAMING("Receiving stream"),
    ERROR("Error"),
}
