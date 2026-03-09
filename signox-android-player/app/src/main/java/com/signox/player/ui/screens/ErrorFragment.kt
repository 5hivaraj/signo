package com.signox.player.ui.screens

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.signox.player.databinding.FragmentErrorBinding

class ErrorFragment : Fragment() {
    
    private var _binding: FragmentErrorBinding? = null
    private val binding get() = _binding!!
    
    private var errorMessage: String? = null
    private var onRetryCallback: (() -> Unit)? = null
    private var onServerSettingsCallback: (() -> Unit)? = null
    
    companion object {
        private const val ARG_ERROR_MESSAGE = "error_message"
        
        fun newInstance(errorMessage: String, onRetry: () -> Unit, onServerSettings: (() -> Unit)? = null): ErrorFragment {
            return ErrorFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_ERROR_MESSAGE, errorMessage)
                }
                onRetryCallback = onRetry
                onServerSettingsCallback = onServerSettings
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        errorMessage = arguments?.getString(ARG_ERROR_MESSAGE) ?: "Unknown error"
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentErrorBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.errorMessageText.text = errorMessage
        
        binding.retryButton.setOnClickListener {
            onRetryCallback?.invoke()
        }
        
        // Hide server settings button if callback is null (URL is hardcoded)
        if (onServerSettingsCallback == null) {
            binding.serverSettingsButton.visibility = View.GONE
        } else {
            binding.serverSettingsButton.setOnClickListener {
                onServerSettingsCallback?.invoke()
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}