package com.signox.player.ui.player

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerView
import com.signox.player.R
import com.signox.player.cache.OfflineMediaLoader
import com.signox.player.data.api.ApiClient
import com.signox.player.data.dto.MediaType
import com.signox.player.data.dto.PlaylistDto
import com.signox.player.data.dto.PlaylistItemDto
import com.signox.player.databinding.FragmentPlaylistPlayerBinding
import java.io.File

class PlaylistPlayerFragment : Fragment() {
    
    private var _binding: FragmentPlaylistPlayerBinding? = null
    private val binding get() = _binding!!
    
    private var playlist: PlaylistDto? = null
    private var currentItemIndex = 0
    private var exoPlayer: ExoPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var advanceRunnable: Runnable? = null
    
    companion object {
        private const val TAG = "PlaylistPlayer"
        private const val ARG_PLAYLIST = "playlist"
        private const val DEFAULT_IMAGE_DURATION = 10 // seconds
        
        // Singleton cache instance to prevent "Another SimpleCache instance" error on orientation change
        @Volatile
        private var exoPlayerCache: com.google.android.exoplayer2.upstream.cache.SimpleCache? = null
        
        @Synchronized
        private fun getOrCreateCache(context: Context): com.google.android.exoplayer2.upstream.cache.SimpleCache {
            return exoPlayerCache ?: run {
                val cacheDir = File(context.cacheDir, "exoplayer")
                val databaseProvider = com.google.android.exoplayer2.database.StandaloneDatabaseProvider(context)
                
                // 1GB cache with eviction starting at 800MB (80% full)
                val maxCacheSize = 1L * 1024 * 1024 * 1024 // 1GB
                val evictionThreshold = (maxCacheSize * 0.8).toLong() // 800MB
                
                val cache = com.google.android.exoplayer2.upstream.cache.SimpleCache(
                    cacheDir,
                    com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor(evictionThreshold),
                    databaseProvider
                )
                exoPlayerCache = cache
                
                Log.i(TAG, "ExoPlayer cache initialized: max=${formatBytes(maxCacheSize)}, eviction threshold=${formatBytes(evictionThreshold)}")
                cache
            }
        }
        
        fun newInstance(playlist: PlaylistDto): PlaylistPlayerFragment {
            return PlaylistPlayerFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_PLAYLIST, playlist)
                }
            }
        }
        
        /**
         * Format bytes to human-readable string
         */
        private fun formatBytes(bytes: Long): String {
            return when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> "${bytes / 1024} KB"
                bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
                else -> "${bytes / (1024 * 1024 * 1024)} GB"
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playlist = arguments?.getParcelable(ARG_PLAYLIST)
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlaylistPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initializePlayer()
        
        // Log cache statistics on startup
        logCacheStats()
        
        startPlayback()
    }
    
    private fun initializePlayer() {
        // Get or create singleton cache instance
        val simpleCache = getOrCreateCache(requireContext())
        
        // Create data source factory that supports both HTTP and file:// URIs
        val dataSourceFactory = com.google.android.exoplayer2.upstream.DefaultDataSource.Factory(requireContext())
        
        // Create cache data source factory with AGGRESSIVE caching for offline playback
        val cacheDataSourceFactory = com.google.android.exoplayer2.upstream.cache.CacheDataSource.Factory()
            .setCache(simpleCache)
            .setUpstreamDataSourceFactory(dataSourceFactory)
            .setCacheWriteDataSinkFactory(
                com.google.android.exoplayer2.upstream.cache.CacheDataSink.Factory()
                    .setCache(simpleCache)
            )
            .setFlags(
                // Enable aggressive caching
                com.google.android.exoplayer2.upstream.cache.CacheDataSource.FLAG_BLOCK_ON_CACHE
            )
            .setCacheKeyFactory { dataSpec ->
                // Use URL as cache key for consistent caching
                dataSpec.uri.toString()
            }
        
        exoPlayer = ExoPlayer.Builder(requireContext())
            .setMediaSourceFactory(
                com.google.android.exoplayer2.source.DefaultMediaSourceFactory(cacheDataSourceFactory)
            )
            .build().apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_ENDED -> {
                            // Always advance to next item when video ends
                            Log.d(TAG, "Video ended - advancing to next")
                            advanceToNext()
                        }
                        Player.STATE_READY -> {
                            Log.d(TAG, "Video ready to play")
                        }
                        Player.STATE_BUFFERING -> {
                            Log.d(TAG, "Video buffering...")
                        }
                        Player.STATE_IDLE -> {
                            Log.d(TAG, "Video idle")
                        }
                    }
                }
                
                override fun onPlayerError(error: com.google.android.exoplayer2.PlaybackException) {
                    Log.e(TAG, "ExoPlayer error: ${error.message}")
                    Log.e(TAG, "Error code: ${error.errorCode}")
                    Log.e(TAG, "Error cause: ${error.cause?.message}")
                    error.cause?.printStackTrace()
                    
                    // On error, skip to next item (don't retry infinitely)
                    Log.w(TAG, "Skipping to next item due to playback error")
                    advanceToNext()
                }
            })
            
            // Enable audio for videos (set volume to 100%)
            volume = 1f
        }
        
        binding.playerView.player = exoPlayer
        binding.playerView.useController = false // Hide controls for kiosk mode
        
        Log.d(TAG, "ExoPlayer initialized with cache support and offline playback")
    }
    
    private fun startPlayback() {
        val playlist = this.playlist
        if (playlist == null || playlist.items.isEmpty()) {
            Log.w(TAG, "No playlist or empty playlist")
            return
        }
        
        currentItemIndex = 0
        playCurrentItem()
    }
    
    private fun playCurrentItem() {
        val currentItem = getCurrentItem() ?: return
        
        Log.d(TAG, "Playing item ${currentItemIndex + 1}/${playlist?.items?.size}: ${currentItem.media.name}")
        
        // Update screen orientation if item has specific orientation
        updateScreenOrientation(currentItem.orientation)
        
        when (currentItem.media.type) {
            MediaType.IMAGE -> playImage(currentItem)
            MediaType.VIDEO -> playVideo(currentItem)
        }
    }
    
    private fun updateScreenOrientation(orientation: String?) {
        orientation?.let {
            activity?.requestedOrientation = when (it.uppercase()) {
                "PORTRAIT" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                "LANDSCAPE" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                else -> return
            }
            Log.d(TAG, "Updated screen orientation to: $orientation")
        }
    }
    
    private fun playImage(item: PlaylistItemDto) {
        Log.d(TAG, "=== PLAYING IMAGE ===")
        Log.d(TAG, "Media name: ${item.media.name}")
        Log.d(TAG, "Media URL: ${item.media.url}")
        
        // Stop any video playback first
        exoPlayer?.stop()
        
        // Hide video player, show image view
        binding.playerView.visibility = View.GONE
        binding.imageView.visibility = View.VISIBLE
        
        Log.d(TAG, "ImageView visibility set to VISIBLE")
        Log.d(TAG, "PlayerView visibility set to GONE")
        
        // Clear previous image first
        binding.imageView.setImageDrawable(null)
        
        // Apply rotation with proper handling for 90/270 degrees
        val rotationDegrees = (item.rotation ?: 0).toFloat()
        binding.imageView.rotation = rotationDegrees
        
        // For 90 or 270 degree rotations, we need to swap width/height
        // by adjusting scale to fit properly
        binding.imageView.post {
            binding.imageView.pivotX = binding.imageView.width / 2f
            binding.imageView.pivotY = binding.imageView.height / 2f
            
            // Adjust scale for 90/270 degree rotations to fit properly
            if (rotationDegrees == 90f || rotationDegrees == 270f) {
                val viewWidth = binding.imageView.width.toFloat()
                val viewHeight = binding.imageView.height.toFloat()
                
                if (viewWidth > 0 && viewHeight > 0) {
                    // Calculate scale to fit rotated content
                    val scale = minOf(viewWidth / viewHeight, viewHeight / viewWidth)
                    binding.imageView.scaleX = scale
                    binding.imageView.scaleY = scale
                }
            } else {
                // Reset scale for 0/180 degree rotations
                binding.imageView.scaleX = 1f
                binding.imageView.scaleY = 1f
            }
        }
        
        Log.d(TAG, "Rotation: ${item.rotation ?: 0}")
        
        // Apply scale type based on resizeMode
        binding.imageView.scaleType = when (item.resizeMode?.uppercase()) {
            "FILL" -> ImageView.ScaleType.CENTER_CROP
            "STRETCH" -> ImageView.ScaleType.FIT_XY
            "FIT" -> ImageView.ScaleType.FIT_CENTER
            else -> ImageView.ScaleType.FIT_CENTER
        }
        Log.d(TAG, "Scale type: ${binding.imageView.scaleType}")
        
        // Use full URL for image loading (Glide handles caching automatically)
        val imageUrl = ApiClient.getMediaUrl(item.media.url)
        
        Log.d(TAG, "Loading image from: $imageUrl")
        
        // Load image with Glide (Glide handles caching automatically)
        Glide.with(this)
            .load(imageUrl)
            .diskCacheStrategy(DiskCacheStrategy.ALL) // Cache both original and resized
            .error(R.drawable.ic_error_placeholder)
            .into(object : com.bumptech.glide.request.target.CustomTarget<android.graphics.drawable.Drawable>() {
                override fun onResourceReady(
                    resource: android.graphics.drawable.Drawable,
                    transition: com.bumptech.glide.request.transition.Transition<in android.graphics.drawable.Drawable>?
                ) {
                    Log.d(TAG, "✅ IMAGE LOADED SUCCESSFULLY")
                    Log.d(TAG, "Image size: ${resource.intrinsicWidth}x${resource.intrinsicHeight}")
                    
                    // Set the image
                    binding.imageView.setImageDrawable(resource)
                    
                    // Force view to redraw
                    binding.imageView.post {
                        binding.imageView.requestLayout()
                        binding.imageView.invalidate()
                        Log.d(TAG, "ImageView dimensions: ${binding.imageView.width}x${binding.imageView.height}")
                        Log.d(TAG, "ImageView visibility: ${binding.imageView.visibility}")
                    }
                }
                
                override fun onLoadFailed(errorDrawable: android.graphics.drawable.Drawable?) {
                    Log.e(TAG, "❌ IMAGE LOAD FAILED")
                    binding.imageView.setImageDrawable(errorDrawable)
                }
                
                override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {
                    // Optional: handle cleanup
                }
            })
        
        // Schedule advance after duration - use item duration, media duration, or default
        val duration = item.duration ?: item.media.duration ?: DEFAULT_IMAGE_DURATION
        Log.d(TAG, "Image will display for $duration seconds")
        scheduleAdvance(duration * 1000L)
        
        Log.d(TAG, "=== IMAGE SETUP COMPLETE ===")
    }
    
    private fun playVideo(item: PlaylistItemDto) {
        Log.d(TAG, "=== PLAYING VIDEO ===")
        Log.d(TAG, "Media name: ${item.media.name}")
        Log.d(TAG, "Media URL: ${item.media.url}")
        Log.d(TAG, "Original URL: ${item.media.originalUrl}")
        
        // Stop any previous playback
        exoPlayer?.stop()
        
        // Hide image view, show video player
        binding.imageView.visibility = View.GONE
        binding.playerView.visibility = View.VISIBLE
        
        Log.d(TAG, "VideoPlayer visibility set to VISIBLE")
        Log.d(TAG, "ImageView visibility set to GONE")
        
        // Apply rotation with proper handling for 90/270 degrees
        val rotationDegrees = (item.rotation ?: 0).toFloat()
        binding.playerView.rotation = rotationDegrees
        
        // For 90 or 270 degree rotations, we need to swap width/height
        // by adjusting scale to fit properly
        binding.playerView.post {
            binding.playerView.pivotX = binding.playerView.width / 2f
            binding.playerView.pivotY = binding.playerView.height / 2f
            
            // Adjust scale for 90/270 degree rotations to fit properly
            if (rotationDegrees == 90f || rotationDegrees == 270f) {
                val viewWidth = binding.playerView.width.toFloat()
                val viewHeight = binding.playerView.height.toFloat()
                
                if (viewWidth > 0 && viewHeight > 0) {
                    // Calculate scale to fit rotated content
                    val scale = minOf(viewWidth / viewHeight, viewHeight / viewWidth)
                    binding.playerView.scaleX = scale
                    binding.playerView.scaleY = scale
                }
            } else {
                // Reset scale for 0/180 degree rotations
                binding.playerView.scaleX = 1f
                binding.playerView.scaleY = 1f
            }
        }
        
        Log.d(TAG, "Rotation: ${item.rotation ?: 0}")
        
        // Apply resize mode to video player
        binding.playerView.resizeMode = when (item.resizeMode?.uppercase()) {
            "FILL" -> com.google.android.exoplayer2.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            "STRETCH" -> com.google.android.exoplayer2.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
            "FIT" -> com.google.android.exoplayer2.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
            else -> com.google.android.exoplayer2.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        Log.d(TAG, "Resize mode: ${item.resizeMode}")
        
        // Simplified URL selection: Always prefer originalUrl (MP4) over HLS
        val isHLS = item.media.url.contains("/hls/") && item.media.url.endsWith("/index.m3u8")
        val hasOriginalUrl = !item.media.originalUrl.isNullOrEmpty()
        
        val videoUrlToUse = when {
            // If we have originalUrl, always use it (better for caching)
            hasOriginalUrl -> {
                Log.d(TAG, "✅ Using originalUrl (MP4) for reliable caching")
                item.media.originalUrl!!
            }
            // Fallback to regular URL (HLS or direct video)
            else -> {
                if (isHLS) {
                    Log.w(TAG, "⚠️ Using HLS without originalUrl - offline playback may be limited")
                } else {
                    Log.d(TAG, "Using direct video URL")
                }
                item.media.url
            }
        }
        
        // Always use HTTP URL - ExoPlayer's CacheDataSource handles everything automatically
        // It will:
        // 1. Check cache first
        // 2. If cached → serve from cache (works offline)
        // 3. If not cached → stream and cache simultaneously
        val playbackUrl = ApiClient.getMediaUrl(videoUrlToUse)
        
        // Check cache status for logging
        val cacheStatus = checkCacheStatus(playbackUrl)
        
        Log.d(TAG, "=== VIDEO PLAYBACK INFO ===")
        Log.d(TAG, "Selected URL: $videoUrlToUse")
        Log.d(TAG, "Full URL: $playbackUrl")
        Log.d(TAG, "Is HLS: $isHLS")
        Log.d(TAG, "Has originalUrl: $hasOriginalUrl")
        Log.d(TAG, "Cache status: $cacheStatus")
        Log.d(TAG, "========================")
        
        val mediaItem = MediaItem.fromUri(Uri.parse(playbackUrl))
        
        exoPlayer?.apply {
            setMediaItem(mediaItem)
            repeatMode = if (item.loopVideo == true) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            prepare()
            play()
            
            Log.d(TAG, "ExoPlayer prepared and playing")
            Log.d(TAG, "Loop mode: ${if (item.loopVideo == true) "ON" else "OFF"}")
        }
        
        // If loopVideo is true and duration is set, schedule advance after duration
        if (item.loopVideo == true && item.duration != null) {
            Log.d(TAG, "Video will loop for ${item.duration} seconds")
            scheduleAdvance(item.duration * 1000L)
        } else {
            Log.d(TAG, "Video will play once and advance automatically")
        }
        // Otherwise video will advance automatically when it ends via onPlaybackStateChanged
        
        // Preload next items in background
        preloadUpcomingItems()
        
        Log.d(TAG, "=== VIDEO SETUP COMPLETE ===")
    }
    
    private fun scheduleAdvance(delayMs: Long) {
        cancelAdvance()
        advanceRunnable = Runnable {
            Log.d(TAG, "Handler runnable executing - advancing to next")
            advanceToNext()
        }
        val posted = handler.postDelayed(advanceRunnable!!, delayMs)
        Log.d(TAG, "Scheduled advance in ${delayMs}ms, posted=$posted")
    }
    
    private fun cancelAdvance() {
        if (advanceRunnable != null) {
            handler.removeCallbacks(advanceRunnable!!)
            Log.d(TAG, "Cancelled pending advance")
        }
        advanceRunnable = null
    }
    
    private fun advanceToNext() {
        val playlist = this.playlist ?: return
        
        currentItemIndex = (currentItemIndex + 1) % playlist.items.size
        Log.d(TAG, "Advancing to item ${currentItemIndex + 1}/${playlist.items.size}")
        
        // Stop current playback
        exoPlayer?.stop()
        cancelAdvance()
        
        // Play next item
        playCurrentItem()
    }
    
    private fun getCurrentItem(): PlaylistItemDto? {
        return playlist?.items?.getOrNull(currentItemIndex)
    }
    
    /**
     * Check if a video URL is cached by ExoPlayer
     */
    private fun checkCacheStatus(url: String): String {
        val cache = exoPlayerCache ?: return "Cache not initialized"
        
        return try {
            val dataSpec = com.google.android.exoplayer2.upstream.DataSpec.Builder()
                .setUri(Uri.parse(url))
                .build()
            
            val cachedBytes = cache.getCachedBytes(
                dataSpec.uri.toString(),
                dataSpec.position,
                com.google.android.exoplayer2.C.LENGTH_UNSET.toLong()
            )
            
            when {
                cachedBytes > 0 -> "✓ Cached (${Companion.formatBytes(cachedBytes)})"
                else -> "☁ Not cached"
            }
        } catch (e: Exception) {
            "? Unknown (${e.message})"
        }
    }
    
    /**
     * Preload upcoming items in playlist for smooth playback
     */
    private fun preloadUpcomingItems() {
        val playlist = this.playlist ?: return
        val lookahead = 3 // Preload next 3 items
        
        // Get upcoming items
        val upcomingIndices = (1..lookahead).map { offset ->
            (currentItemIndex + offset) % playlist.items.size
        }
        
        Log.d(TAG, "Preloading upcoming items: $upcomingIndices")
        
        upcomingIndices.forEachIndexed { index, itemIndex ->
            val item = playlist.items.getOrNull(itemIndex) ?: return@forEachIndexed
            
            // Only preload videos
            if (item.media.type != MediaType.VIDEO) return@forEachIndexed
            
            // Prefer originalUrl for preloading
            val urlToPreload = if (!item.media.originalUrl.isNullOrEmpty()) {
                item.media.originalUrl!!
            } else {
                item.media.url
            }
            
            val fullUrl = ApiClient.getMediaUrl(urlToPreload)
            
            // Check if already cached
            val cacheStatus = checkCacheStatus(fullUrl)
            
            if (cacheStatus.contains("Not cached")) {
                Log.d(TAG, "Queuing preload for item $itemIndex: ${item.media.name}")
                // ExoPlayer will cache automatically when we prepare the media item
                // We can trigger preload by creating a MediaItem (but not playing it)
                // For now, just log - ExoPlayer will cache during playback
            } else {
                Log.d(TAG, "Item $itemIndex already cached: ${item.media.name}")
            }
        }
    }
    
    /**
     * Log cache statistics
     */
    private fun logCacheStats() {
        val cache = exoPlayerCache ?: return
        
        try {
            val cacheSpace = cache.cacheSpace
            val maxCacheSize = 1L * 1024 * 1024 * 1024 // 1GB
            val evictionThreshold = (maxCacheSize * 0.8).toLong() // 800MB
            val usagePercent = (cacheSpace.toDouble() / maxCacheSize * 100).toInt()
            val evictionPercent = (cacheSpace.toDouble() / evictionThreshold * 100).toInt()
            
            Log.i(TAG, "=== CACHE STATISTICS ===")
            Log.i(TAG, "Cache size: ${Companion.formatBytes(cacheSpace)} / ${Companion.formatBytes(maxCacheSize)}")
            Log.i(TAG, "Usage: $usagePercent% of max")
            Log.i(TAG, "Eviction threshold: ${Companion.formatBytes(evictionThreshold)} (80%)")
            Log.i(TAG, "Eviction status: $evictionPercent% of threshold ${if (cacheSpace >= evictionThreshold) "⚠️ WILL EVICT" else "✓ OK"}")
            Log.i(TAG, "========================")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get cache stats: ${e.message}")
        }
    }
    
    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause called - pausing playback")
        exoPlayer?.pause()
        cancelAdvance()
    }
    
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume called - resuming playback")
        exoPlayer?.play()
        
        // Restart timer for images if we're currently showing an image
        val currentItem = getCurrentItem()
        if (currentItem != null && currentItem.media.type == MediaType.IMAGE) {
            val duration = currentItem.duration ?: currentItem.media.duration ?: DEFAULT_IMAGE_DURATION
            Log.d(TAG, "Restarting image timer for $duration seconds")
            scheduleAdvance(duration * 1000L)
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        
        // Log final cache stats
        logCacheStats()
        
        // Release player but NOT the cache (cache is singleton)
        exoPlayer?.release()
        exoPlayer = null
        cancelAdvance()
        _binding = null
    }
}