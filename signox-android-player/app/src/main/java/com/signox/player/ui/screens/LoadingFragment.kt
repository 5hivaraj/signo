package com.signox.player.ui.screens

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.signox.player.databinding.FragmentLoadingBinding

class LoadingFragment : Fragment() {
    
    private var _binding: FragmentLoadingBinding? = null
    private val binding get() = _binding!!
    
    private var message: String? = null
    
    companion object {
        private const val ARG_MESSAGE = "message"
        
        fun newInstance(message: String = "Loading..."): LoadingFragment {
            return LoadingFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_MESSAGE, message)
                }
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        message = arguments?.getString(ARG_MESSAGE) ?: "Loading..."
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoadingBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.messageText.text = message
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}