package com.signox.player.cache

import android.content.Context
import android.util.Log
import com.signox.player.data.api.ApiClient
import com.signox.player.data.dto.LayoutDto
import com.signox.player.data.dto.PlaylistDto

/**
 * Main interface for loading media with offline support
 */
class OfflineMediaLoader(
    private val context: Context,
    private val cacheManager: MediaCacheManager,
    private val downloadManager: MediaDownloadManager,
    private val networkMonitor: NetworkMonitor
) {
    
    companion object {
        private const val TAG = "OfflineMediaLoader"
        
        @Volatile
        private var instance: OfflineMediaLoader? = null
        
        fun getInstance(context: Context): OfflineMediaLoader {
            return instance ?: synchronized(this) {
                instance ?: createInstance(context).also { instance = it }
            }
        }
        
        private fun createInstance(context: Context): OfflineMediaLoader {
            val cacheManager = MediaCacheManager(context)
            val networkMonitor = NetworkMonitor(context)
            val downloadManager = MediaDownloadManager(context, cacheManager, networkMonitor)
            
            // Start download manager
            downloadManager.start()
            
            return OfflineMediaLoader(context, cacheManager, downloadManager, networkMonitor)
        }
    }
    
    /**
     * Load media URL - returns local file path if cached, otherwise streaming URL
     */
    fun loadMedia(mediaUrl: String): String {
        Log.d(TAG, "Loading media: $mediaUrl")
        
        // 1. Check if file exists in local cache
        val cachedFile = cacheManager.getCachedFile(mediaUrl)
        
        if (cachedFile != null && cachedFile.exists()) {
            Log.d(TAG, "Using cached file: ${cachedFile.absolutePath}")
            return cachedFile.absolutePath
        }
        
        // 2. Check network connectivity
        if (networkMonitor.isNetworkAvailable()) {
            // Queue for download with high priority (currently playing)
            downloadManager.queueDownload(mediaUrl, DownloadPriority.HIGH)
            
            // Return streaming URL
            val streamingUrl = ApiClient.getMediaUrl(mediaUrl)
            Log.d(TAG, "Streaming from: $streamingUrl")
            return streamingUrl
        }
        
        // 3. No cache and no network - return streaming URL anyway (will fail gracefully)
        Log.w(TAG, "Media not cached and no network: $mediaUrl")
        return ApiClient.getMediaUrl(mediaUrl)
    }
    
    /**
     * Preload playlist media files
     */
    fun preloadPlaylist(playlist: PlaylistDto) {
        Log.d(TAG, "Preloading playlist: ${playlist.name} (${playlist.items.size} items)")
        
        val mediaUrls = playlist.items.mapNotNull { it.media?.url }
        
        if (mediaUrls.isEmpty()) {
            Log.w(TAG, "No media items in playlist")
            return
        }
        
        // Queue first item with high priority (will play first)
        if (mediaUrls.isNotEmpty()) {
            downloadManager.queueDownload(mediaUrls[0], DownloadPriority.HIGH)
        }
        
        // Queue remaining items with medium priority
        if (mediaUrls.size > 1) {
            downloadManager.queueDownloads(
                mediaUrls.subList(1, mediaUrls.size),
                DownloadPriority.MEDIUM
            )
        }
        
        Log.d(TAG, "Queued ${mediaUrls.size} media files for download")
    }
    
    /**
     * Preload layout media files
     */
    fun preloadLayout(layout: LayoutDto) {
        Log.d(TAG, "Preloading layout: ${layout.name} (${layout.sections.size} sections)")
        
        val mediaUrls = mutableListOf<String>()
        
        // Collect all media URLs from all sections
        layout.sections.forEach { section ->
            section.items.forEach { item ->
                item.media?.url?.let { url ->
                    mediaUrls.add(url)
                }
            }
        }
        
        if (mediaUrls.isEmpty()) {
            Log.w(TAG, "No media items in layout")
            return
        }
        
        // Queue first few items with high priority
        val highPriorityCount = minOf(3, mediaUrls.size)
        downloadManager.queueDownloads(
            mediaUrls.subList(0, highPriorityCount),
            DownloadPriority.HIGH
        )
        
        // Queue remaining items with medium priority
        if (mediaUrls.size > highPriorityCount) {
            downloadManager.queueDownloads(
                mediaUrls.subList(highPriorityCount, mediaUrls.size),
                DownloadPriority.MEDIUM
            )
        }
        
        Log.d(TAG, "Queued ${mediaUrls.size} media files for download")
    }
    
    /**
     * Check if media is available offline
     */
    fun isAvailableOffline(mediaUrl: String): Boolean {
        return cacheManager.isCached(mediaUrl)
    }
    
    /**
     * Check if playlist is fully cached
     */
    fun isPlaylistCached(playlist: PlaylistDto): Boolean {
        val mediaUrls = playlist.items.mapNotNull { it.media?.url }
        return mediaUrls.all { cacheManager.isCached(it) }
    }
    
    /**
     * Check if layout is fully cached
     */
    fun isLayoutCached(layout: LayoutDto): Boolean {
        val mediaUrls = mutableListOf<String>()
        layout.sections.forEach { section ->
            section.items.forEach { item ->
                item.media?.url?.let { mediaUrls.add(it) }
            }
        }
        return mediaUrls.all { cacheManager.isCached(it) }
    }
    
    /**
     * Get cache statistics
     */
    fun getCacheStats(): CacheStats {
        return cacheManager.getStats()
    }
    
    /**
     * Clear cache
     */
    fun clearCache(): Boolean {
        Log.d(TAG, "Clearing cache")
        return cacheManager.clearCache()
    }
    
    /**
     * Verify cache integrity
     */
    fun verifyCache(): List<String> {
        Log.d(TAG, "Verifying cache integrity")
        return cacheManager.verifyIntegrity()
    }
    
    /**
     * Clean up old media files not in current config
     */
    fun cleanupOldMedia(currentMediaUrls: List<String>) {
        Log.d(TAG, "Cleaning up old media")
        
        val cachedUrls = cacheManager.getCachedMediaUrls()
        val urlsToRemove = cachedUrls.filter { it !in currentMediaUrls }
        
        if (urlsToRemove.isNotEmpty()) {
            Log.d(TAG, "Removing ${urlsToRemove.size} old media files")
            urlsToRemove.forEach { url ->
                cacheManager.removeFromCache(url)
            }
        } else {
            Log.d(TAG, "No old media files to remove")
        }
    }
    
    /**
     * Get network state
     */
    fun getNetworkState(): NetworkState {
        return networkMonitor.getNetworkState()
    }
    
    /**
     * Check if network is available
     */
    fun isNetworkAvailable(): Boolean {
        return networkMonitor.isNetworkAvailable()
    }
    
    /**
     * Set whether to allow downloads on cellular
     */
    fun setAllowCellularDownloads(allow: Boolean) {
        downloadManager.setAllowCellularDownloads(allow)
    }
    
    /**
     * Get download progress for media
     */
    fun getDownloadProgress(mediaUrl: String): Int {
        return downloadManager.getProgress(mediaUrl)
    }
    
    /**
     * Check if media is currently downloading
     */
    fun isDownloading(mediaUrl: String): Boolean {
        return downloadManager.isDownloading(mediaUrl)
    }
    
    /**
     * Cancel download
     */
    fun cancelDownload(mediaUrl: String) {
        downloadManager.cancelDownload(mediaUrl)
    }
    
    /**
     * Shutdown - stop all downloads
     */
    fun shutdown() {
        Log.d(TAG, "Shutting down")
        downloadManager.stop()
    }
}
