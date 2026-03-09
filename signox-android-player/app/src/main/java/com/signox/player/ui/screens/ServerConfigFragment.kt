package com.signox.player.ui.screens

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.signox.player.BuildConfig
import com.signox.player.data.repository.PlayerRepository
import com.signox.player.databinding.FragmentServerConfigBinding

class ServerConfigFragment : Fragment() {
    
    private var _binding: FragmentServerConfigBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var repository: PlayerRepository
    private var onConfigSavedCallback: (() -> Unit)? = null
    private var isFirstTime: Boolean = false
    
    companion object {
        private const val ARG_IS_FIRST_TIME = "is_first_time"
        
        fun newInstance(isFirstTime: Boolean = false, onConfigSaved: () -> Unit): ServerConfigFragment {
            return ServerConfigFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_IS_FIRST_TIME, isFirstTime)
                }
                onConfigSavedCallback = onConfigSaved
            }
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentServerConfigBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        repository = PlayerRepository(requireContext())
        isFirstTime = arguments?.getBoolean(ARG_IS_FIRST_TIME, false) ?: false
        
        // Update UI for first-time setup
        if (isFirstTime) {
            // Clear the input field for first-time setup
            binding.serverUrlInput.setText("")
            binding.serverUrlInput.hint = "Enter server IP address (e.g., 192.168.1.100:5000)"
            // Hide cancel button for first-time setup
            binding.cancelButton.visibility = View.GONE
        } else {
            // Pre-fill with current server URL for configuration changes
            val currentUrl = repository.getCurrentServerUrl()
            if (currentUrl != null) {
                binding.serverUrlInput.setText(currentUrl)
            }
            binding.cancelButton.visibility = View.VISIBLE
        }
        
        binding.saveButton.setOnClickListener {
            val serverUrl = binding.serverUrlInput.text.toString().trim()
            if (isValidUrl(serverUrl)) {
                repository.saveServerUrl(serverUrl)
                onConfigSavedCallback?.invoke()
            } else {
                binding.serverUrlInput.error = "Please enter a valid URL (e.g., http://192.168.1.100:5000)"
            }
        }
        
        // Update button text based on context
        if (isFirstTime) {
            binding.saveButton.text = "Connect"
        } else {
            binding.saveButton.text = "Save"
        }
        
        binding.cancelButton.setOnClickListener {
            if (!isFirstTime) {
                onConfigSavedCallback?.invoke() // Just go back without saving
            }
        }
    }
    
    private fun isValidUrl(url: String): Boolean {
        if (url.isEmpty()) return false
        
        // Auto-add http:// if not present
        val formattedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
            "http://$url"
        } else {
            url
        }
        
        // Update the input field with the formatted URL
        binding.serverUrlInput.setText(formattedUrl)
        
        return formattedUrl.startsWith("http://") || formattedUrl.startsWith("https://")
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}