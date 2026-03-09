package com.signox.player.ui.screens

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.fragment.app.Fragment
import com.signox.player.R
import com.signox.player.databinding.FragmentStandbyBinding

class StandbyFragment : Fragment() {
    
    private var _binding: FragmentStandbyBinding? = null
    private val binding get() = _binding!!
    
    private var displayId: String = ""
    
    companion object {
        private const val ARG_DISPLAY_ID = "display_id"
        
        fun newInstance(displayId: String): StandbyFragment {
            return StandbyFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_DISPLAY_ID, displayId)
                }
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        displayId = arguments?.getString(ARG_DISPLAY_ID) ?: "Unknown"
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStandbyBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Show display ID (last 8 characters like web player)
        val shortDisplayId = if (displayId.length > 8) {
            displayId.takeLast(8)
        } else {
            displayId
        }
        binding.displayIdText.text = "Display ID: $shortDisplayId"
        
        // Start pulsing animation for the online indicator
        val pulseAnimation = AnimationUtils.loadAnimation(requireContext(), R.anim.pulse)
        binding.onlineIndicator.startAnimation(pulseAnimation)
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}