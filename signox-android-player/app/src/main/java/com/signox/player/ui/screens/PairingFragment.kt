package com.signox.player.ui.screens

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.fragment.app.Fragment
import com.signox.player.R
import com.signox.player.databinding.FragmentPairingBinding

class PairingFragment : Fragment() {
    
    private var _binding: FragmentPairingBinding? = null
    private val binding get() = _binding!!
    
    private var pairingCode: String = ""
    private var onResetCallback: (() -> Unit)? = null
    private var onServerConfigCallback: (() -> Unit)? = null
    
    companion object {
        private const val ARG_PAIRING_CODE = "pairing_code"
        
        fun newInstance(
            pairingCode: String,
            onReset: () -> Unit,
            onServerConfig: (() -> Unit)? = null
        ): PairingFragment {
            return PairingFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PAIRING_CODE, pairingCode)
                }
                onResetCallback = onReset
                onServerConfigCallback = onServerConfig
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pairingCode = arguments?.getString(ARG_PAIRING_CODE) ?: ""
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPairingBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Display the pairing code prominently
        binding.pairingCodeDisplay.text = pairingCode
        
        // Start pulsing animation for the waiting indicator
        val pulseAnimation = AnimationUtils.loadAnimation(requireContext(), R.anim.pulse)
        binding.waitingIndicator.startAnimation(pulseAnimation)
        
        binding.resetButton.setOnClickListener {
            onResetCallback?.invoke()
        }
        
        // Hide server config button if callback is null (URL is hardcoded)
        if (onServerConfigCallback == null) {
            binding.serverConfigButton.visibility = View.GONE
        } else {
            binding.serverConfigButton.setOnClickListener {
                onServerConfigCallback?.invoke()
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}