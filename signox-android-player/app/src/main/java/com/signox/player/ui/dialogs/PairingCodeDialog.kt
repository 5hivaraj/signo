package com.signox.player.ui.dialogs

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.signox.player.databinding.DialogPairingCodeBinding

class PairingCodeDialog : DialogFragment() {
    
    private var _binding: DialogPairingCodeBinding? = null
    private val binding get() = _binding!!
    
    private var pairingCode: String = ""
    private var onDismissCallback: (() -> Unit)? = null
    
    companion object {
        private const val ARG_PAIRING_CODE = "pairing_code"
        
        fun newInstance(
            pairingCode: String,
            onDismiss: (() -> Unit)? = null
        ): PairingCodeDialog {
            return PairingCodeDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_PAIRING_CODE, pairingCode)
                }
                onDismissCallback = onDismiss
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pairingCode = arguments?.getString(ARG_PAIRING_CODE) ?: ""
    }
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        return dialog
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogPairingCodeBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Set the pairing code
        binding.pairingCodeText.text = pairingCode
        
        // Set up copy button
        binding.copyButton.setOnClickListener {
            copyPairingCodeToClipboard()
        }
        
        // Set up close button
        binding.closeButton.setOnClickListener {
            dismiss()
        }
        
        // Make dialog non-cancelable by touching outside
        dialog?.setCanceledOnTouchOutside(false)
        isCancelable = false
    }
    
    private fun copyPairingCodeToClipboard() {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Pairing Code", pairingCode)
        clipboard.setPrimaryClip(clip)
        
        Toast.makeText(requireContext(), "Pairing code copied to clipboard", Toast.LENGTH_SHORT).show()
    }
    
    fun updateStatus(status: String) {
        if (_binding != null) {
            binding.statusText.text = status
        }
    }
    
    fun updatePairingCode(newPairingCode: String) {
        if (_binding != null) {
            pairingCode = newPairingCode
            binding.pairingCodeText.text = pairingCode
        }
    }
    
    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        onDismissCallback?.invoke()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}