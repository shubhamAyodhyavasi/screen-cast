package com.screencast.androidtv

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

/**
 * Drives [MainFragment].
 * Owns the [SignalingClient] and [WebRTCManager] lifecycles.
 */
class MainViewModel : ViewModel() {

    // -------------------------------------------------------------------------
    // Exposed state
    // -------------------------------------------------------------------------

    private val _connectionState = MutableLiveData(ConnectionState.DISCONNECTED)
    val connectionState: LiveData<ConnectionState> = _connectionState

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    // -------------------------------------------------------------------------
    // Dependencies
    // -------------------------------------------------------------------------

    private val signalingClient = SignalingClient(
        serverUrl = "ws://10.0.2.2:8080",   // localhost from Android emulator
        deviceName = "Android TV",
        onStateChange = { _connectionState.postValue(it) },
        onError = { _errorMessage.postValue(it) },
    )

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    fun connect() {
        viewModelScope.launch {
            signalingClient.connect()
        }
    }

    fun disconnect() {
        signalingClient.disconnect()
    }

    override fun onCleared() {
        super.onCleared()
        signalingClient.disconnect()
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
