package com.screencast.androidtv

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.screencast.androidtv.databinding.FragmentMainBinding

/**
 * MainFragment – shows connection status and the incoming stream surface.
 *
 * In a full implementation this fragment would host a [android.view.SurfaceView]
 * that is fed by the WebRTC video track decoded by [WebRTCManager].
 */
class MainFragment : Fragment() {

    private var _binding: FragmentMainBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: MainViewModel

    // -------------------------------------------------------------------------
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        // Observe connection state
        viewModel.connectionState.observe(viewLifecycleOwner) { state ->
            binding.statusText.text = state.label
        }

        // Observe any error messages
        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                binding.statusText.text = "Error: $error"
            }
        }

        // Connect to the signaling server as soon as the fragment is visible
        viewModel.connect()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.disconnect()
        _binding = null
    }
}
