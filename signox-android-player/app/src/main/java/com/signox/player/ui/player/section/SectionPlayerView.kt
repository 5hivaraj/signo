package com.signox.player.ui.player.section

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.signox.player.R
import com.signox.player.data.api.ApiClient
import com.signox.player.data.dto.LayoutItemDto
import com.signox.player.data.dto.LayoutSectionDto
import com.signox.player.data.dto.MediaType
import com.signox.player.databinding.ViewSectionPlayerBinding

class SectionPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    
    private val binding: ViewSectionPlayerBinding
    private var section: LayoutSectionDto? = null
    private var currentItemIndex = 0
    private var exoPlayer: ExoPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var advanceRunnable: Runnable? = null
    
    companion object {
        private const val TAG = "SectionPlayerView"
        private const val DEFAULT_IMAGE_DURATION = 10 // seconds
    }
    
    init {
        binding = ViewSectionPlayerBinding.inflate(LayoutInflater.from(context), this, true)
        initializePlayer()
    }
    
    fun setSection(section: LayoutSectionDto) {
        this.section = section
        currentItemIndex = 0
    }
    
    private fun initializePlayer() {
        exoPlayer = ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_ENDED -> {
                            // Always advance to next item when video ends (unless single item with loopEnabled)
                            if (hasMultipleItems() || !isLoopEnabled()) {
                                advanceToNext()
                            }
                        }
                        Player.STATE_READY -> {
                            Log.d(TAG, "Video ready in section ${section?.id}")
                        }
                    }
                }
                
                override fun onPlayerError(error: com.google.android.exoplayer2.PlaybackException) {
                    Log.e(TAG, "ExoPlayer error in section ${section?.id}: ${error.message}")
                    advanceToNext() // Skip to next on error
                }
            })
            
            // Enable audio for videos (set volume to 100%)
            volume = 1f
        }
        
        binding.playerView.player = exoPlayer
        binding.playerView.useController = false // Hide controls for kiosk mode
    }
    
    fun startPlayback() {
        val section = this.section
        if (section == null || section.items.isEmpty()) {
            Log.w(TAG, "No section or empty items")
            return
        }
        
        currentItemIndex = 0
        playCurrentItem()
    }
    
    private fun playCurrentItem() {
        val currentItem = getCurrentItem() ?: return
        val sectionId = section?.id ?: "unknown"
        
        Log.d(TAG, "Playing item ${currentItemIndex + 1}/${section?.items?.size} in section $sectionId")
        
        // Update screen orientation if item has specific orientation
        updateScreenOrientation(currentItem.orientation)
        
        when (currentItem.media.type) {
            MediaType.IMAGE -> playImage(currentItem)
            MediaType.VIDEO -> playVideo(currentItem)
        }
    }
    
    private fun updateScreenOrientation(orientation: String?) {
        orientation?.let {
            (context as? android.app.Activity)?.requestedOrientation = when (it.uppercase()) {
                "PORTRAIT" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                "LANDSCAPE" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                else -> return
            }
            Log.d(TAG, "Updated screen orientation to: $orientation")
        }
    }
    
    private fun playImage(item: LayoutItemDto) {
        // Hide video player, show image view
        binding.playerView.visibility = GONE
        binding.imageView.visibility = VISIBLE
        
        // Apply rotation
        binding.imageView.rotation = (item.rotation ?: 0).toFloat()
        
        // Apply scale type based on resizeMode
        binding.imageView.scaleType = when (item.resizeMode?.uppercase()) {
            "FILL" -> android.widget.ImageView.ScaleType.CENTER_CROP
            "STRETCH" -> android.widget.ImageView.ScaleType.FIT_XY
            "FIT" -> android.widget.ImageView.ScaleType.FIT_CENTER
            else -> android.widget.ImageView.ScaleType.FIT_CENTER
        }
        
        // Use OfflineMediaLoader for offline support
        val offlineLoader = com.signox.player.cache.OfflineMediaLoader.getInstance(context)
        val imageUrl = offlineLoader.loadMedia(item.media.url)
        
        Glide.with(context)
            .load(imageUrl)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .error(R.drawable.ic_error_placeholder)
            .into(binding.imageView)
        
        // Schedule advance after duration - use item duration, media duration, or default
        val duration = item.duration ?: item.media.duration ?: DEFAULT_IMAGE_DURATION
        scheduleAdvance(duration * 1000L)
    }
    
    private fun playVideo(item: LayoutItemDto) {
        // Hide image view, show video player
        binding.imageView.visibility = GONE
        binding.playerView.visibility = VISIBLE
        
        // Apply rotation to video player
        binding.playerView.rotation = (item.rotation ?: 0).toFloat()
        
        // Apply resize mode to video player
        binding.playerView.resizeMode = when (item.resizeMode?.uppercase()) {
            "FILL" -> com.google.android.exoplayer2.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            "STRETCH" -> com.google.android.exoplayer2.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
            "FIT" -> com.google.android.exoplayer2.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
            else -> com.google.android.exoplayer2.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        
        // Use OfflineMediaLoader for offline support
        val offlineLoader = com.signox.player.cache.OfflineMediaLoader.getInstance(context)
        val videoUrl = offlineLoader.loadMedia(item.media.url)
        val mediaItem = MediaItem.fromUri(Uri.parse(videoUrl))
        
        exoPlayer?.apply {
            setMediaItem(mediaItem)
            
            // Loop if single item and section loopEnabled
            repeatMode = if (!hasMultipleItems() && isLoopEnabled()) {
                Player.REPEAT_MODE_ONE
            } else {
                Player.REPEAT_MODE_OFF
            }
            
            prepare()
            play()
        }
        
        // If has duration, schedule advance (for looping videos or timed videos)
        if (item.duration != null) {
            scheduleAdvance(item.duration * 1000L)
        }
        // If no duration and multiple items, advance will happen in onPlaybackStateChanged when STATE_ENDED
    }
    
    private fun scheduleAdvance(delayMs: Long) {
        cancelAdvance()
        advanceRunnable = Runnable {
            advanceToNext()
        }
        handler.postDelayed(advanceRunnable!!, delayMs)
    }
    
    private fun cancelAdvance() {
        advanceRunnable?.let { handler.removeCallbacks(it) }
        advanceRunnable = null
    }
    
    private fun advanceToNext() {
        val section = this.section ?: return
        
        if (section.items.size <= 1) {
            // Single item, restart it if looping enabled
            if (isLoopEnabled()) {
                currentItemIndex = 0
            }
        } else {
            // Multiple items, go to next
            currentItemIndex = (currentItemIndex + 1) % section.items.size
        }
        
        Log.d(TAG, "Advancing to item ${currentItemIndex + 1}/${section.items.size} in section ${section.id}")
        
        // Stop current playback
        exoPlayer?.stop()
        cancelAdvance()
        
        // Play next item
        playCurrentItem()
    }
    
    private fun getCurrentItem(): LayoutItemDto? {
        return section?.items?.getOrNull(currentItemIndex)
    }
    
    private fun hasMultipleItems(): Boolean {
        return (section?.items?.size ?: 0) > 1
    }
    
    private fun isLoopEnabled(): Boolean {
        return section?.loopEnabled ?: false
    }
    
    fun pause() {
        exoPlayer?.pause()
        cancelAdvance()
    }
    
    fun resume() {
        exoPlayer?.play()
    }
    
    fun release() {
        exoPlayer?.release()
        exoPlayer = null
        cancelAdvance()
    }
}