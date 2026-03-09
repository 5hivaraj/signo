package com.signox.player.ui.player

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import com.signox.player.data.dto.LayoutDto
import com.signox.player.data.dto.LayoutSectionDto
import com.signox.player.databinding.FragmentLayoutPlayerBinding
import com.signox.player.ui.player.section.SectionPlayerView

class LayoutPlayerFragment : Fragment() {
    
    private var _binding: FragmentLayoutPlayerBinding? = null
    private val binding get() = _binding!!
    
    private var layout: LayoutDto? = null
    private val sectionPlayers = mutableListOf<SectionPlayerView>()
    
    companion object {
        private const val TAG = "LayoutPlayer"
        private const val ARG_LAYOUT = "layout"
        
        fun newInstance(layout: LayoutDto): LayoutPlayerFragment {
            return LayoutPlayerFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_LAYOUT, layout)
                }
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        layout = arguments?.getParcelable(ARG_LAYOUT)
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLayoutPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupLayout()
    }
    
    private fun setupLayout() {
        val layout = this.layout
        if (layout == null || layout.sections.isEmpty()) {
            Log.w(TAG, "No layout or empty sections")
            return
        }
        
        Log.d(TAG, "Setting up layout with ${layout.sections.size} sections")
        
        // Sort sections by order
        val sortedSections = layout.sections.sortedBy { it.order }
        
        for (section in sortedSections) {
            createSectionPlayer(section)
        }
        
        // Start all section players
        sectionPlayers.forEach { it.startPlayback() }
    }
    
    private fun createSectionPlayer(section: LayoutSectionDto) {
        if (section.items.isEmpty()) {
            Log.w(TAG, "Section ${section.id} has no items, skipping")
            return
        }
        
        val sectionPlayer = SectionPlayerView(requireContext()).apply {
            setSection(section)
        }
        
        // Calculate position and size based on screen dimensions
        val layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        
        sectionPlayer.layoutParams = layoutParams
        
        // Add to container
        binding.sectionsContainer.addView(sectionPlayer)
        sectionPlayers.add(sectionPlayer)
        
        // Position the section using post to ensure layout is complete
        sectionPlayer.post {
            positionSection(sectionPlayer, section)
        }
        
        Log.d(TAG, "Created section player for ${section.id} with ${section.items.size} items")
    }
    
    private fun positionSection(sectionView: SectionPlayerView, section: LayoutSectionDto) {
        val container = binding.sectionsContainer
        val containerWidth = container.width
        val containerHeight = container.height
        
        if (containerWidth == 0 || containerHeight == 0) {
            // Layout not ready yet, try again
            sectionView.post {
                positionSection(sectionView, section)
            }
            return
        }
        
        // Calculate actual pixel positions from percentages
        val x = (section.x / 100f * containerWidth).toInt()
        val y = (section.y / 100f * containerHeight).toInt()
        val width = (section.width / 100f * containerWidth).toInt()
        val height = (section.height / 100f * containerHeight).toInt()
        
        // Update layout params
        val layoutParams = sectionView.layoutParams as FrameLayout.LayoutParams
        layoutParams.width = width
        layoutParams.height = height
        layoutParams.leftMargin = x
        layoutParams.topMargin = y
        
        sectionView.layoutParams = layoutParams
        
        Log.d(TAG, "Positioned section ${section.id}: x=$x, y=$y, w=$width, h=$height")
    }
    
    override fun onPause() {
        super.onPause()
        sectionPlayers.forEach { it.pause() }
    }
    
    override fun onResume() {
        super.onResume()
        sectionPlayers.forEach { it.resume() }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        sectionPlayers.forEach { it.release() }
        sectionPlayers.clear()
        _binding = null
    }
}