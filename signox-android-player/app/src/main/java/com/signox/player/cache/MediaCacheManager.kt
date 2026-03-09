package com.signox.player.cache

import android.content.Context
import android.util.Log
import com.signox.player.utils.FileUtils
import com.signox.player.utils.StorageUtils
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Manages local media file cache
 */
class MediaCacheManager(private val context: Context) {
    
    private val cacheDir: File = StorageUtils.getCacheDirectory(context)
    private val imagesDir: File = StorageUtils.getImagesCacheDirectory(context)
    private val videosDir: File = StorageUtils.getVideosCacheDirectory(context)
    private val metadataFile: File = StorageUtils.getCacheMetadataFile(context)
    
    private var metadata: CacheMetadata = loadMetadata()
    
    companion object {
        private const val TAG = "MediaCacheManager"
        private const val DEFAULT_MAX_CACHE_SIZE = 5L * 1024 * 1024 * 1024 // 5GB
    }
    
    init {
        Log.d(TAG, "Initialized - Cache dir: ${cacheDir.absolutePath}")
        Log.d(TAG, "Current cache size: ${FileUtils.formatBytes(metadata.totalSize)}")
    }
    
    /**
     * Load metadata from file
     */
    private fun loadMetadata(): CacheMetadata {
        return try {
            if (metadataFile.exists()) {
                val json = metadataFile.readText()
                CacheMetadata.fromJson(json).also {
                    Log.d(TAG, "Loaded metadata: ${it.media.size} files, " +
                               "${FileUtils.formatBytes(it.totalSize)}")
                }
            } else {
                Log.d(TAG, "No metadata file found, creating new")
                CacheMetadata(maxCacheSize = DEFAULT_MAX_CACHE_SIZE)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading metadata", e)
            CacheMetadata(maxCacheSize = DEFAULT_MAX_CACHE_SIZE)
        }
    }
    
    /**
     * Save metadata to file
     */
    private fun saveMetadata() {
        try {
            metadata.recalculateTotalSize()
            metadataFile.writeText(metadata.toJson())
            Log.d(TAG, "Saved metadata: ${metadata.media.size} files")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving metadata", e)
        }
    }
    
    /**
     * Get cached file for a media URL
     */
    fun getCachedFile(mediaUrl: String): File? {
        val cachedInfo = metadata.getMedia(mediaUrl)
        
        if (cachedInfo != null) {
            val file = cachedInfo.getFile()
            
            if (file.exists() && cachedInfo.isValid()) {
                // Update last used timestamp
                metadata.updateLastUsed(mediaUrl)
                saveMetadata()
                
                Log.d(TAG, "Cache HIT: $mediaUrl -> ${file.absolutePath}")
                return file
            } else {
                // File is missing or corrupted, remove from metadata
                Log.w(TAG, "Cache INVALID: $mediaUrl - removing from cache")
                metadata.removeMedia(mediaUrl)
                saveMetadata()
            }
        }
        
        Log.d(TAG, "Cache MISS: $mediaUrl")
        return null
    }
    
    /**
     * Add file to cache
     */
    fun addToCache(
        mediaUrl: String,
        inputStream: InputStream,
        mediaType: MediaType,
        checksum: String? = null
    ): File? {
        return try {
            // Generate safe filename
            val fileName = FileUtils.getSafeFileName(mediaUrl)
            
            // Determine target directory based on media type
            val targetDir = when (mediaType) {
                MediaType.IMAGE -> imagesDir
                MediaType.VIDEO -> videosDir
                MediaType.UNKNOWN -> cacheDir
            }
            
            val targetFile = targetDir.resolve(fileName)
            
            // Check if we need to evict files
            val estimatedSize = inputStream.available().toLong()
            if (metadata.needsEviction(estimatedSize)) {
                evictLRU(estimatedSize)
            }
            
            // Check storage space
            if (!StorageUtils.hasEnoughSpace(estimatedSize)) {
                Log.e(TAG, "Not enough storage space for $mediaUrl")
                return null
            }
            
            // Write file
            FileOutputStream(targetFile).use { output ->
                inputStream.copyTo(output)
            }
            
            val fileSize = targetFile.length()
            Log.d(TAG, "Cached file: $mediaUrl -> ${targetFile.absolutePath} " +
                       "(${FileUtils.formatBytes(fileSize)})")
            
            // Calculate checksum if not provided
            val finalChecksum = checksum ?: FileUtils.calculateChecksum(targetFile)
            
            // Add to metadata
            val cachedInfo = CachedMediaInfo(
                id = mediaUrl.hashCode().toString(),
                url = mediaUrl,
                localPath = targetFile.absolutePath,
                fileSize = fileSize,
                checksum = finalChecksum,
                type = mediaType
            )
            
            metadata.addMedia(cachedInfo)
            saveMetadata()
            
            targetFile
        } catch (e: Exception) {
            Log.e(TAG, "Error adding to cache: $mediaUrl", e)
            null
        }
    }
    
    /**
     * Remove file from cache
     */
    fun removeFromCache(mediaUrl: String): Boolean {
        val cachedInfo = metadata.getMedia(mediaUrl) ?: return false
        
        return try {
            val file = cachedInfo.getFile()
            val deleted = FileUtils.deleteFile(file)
            
            if (deleted) {
                metadata.removeMedia(mediaUrl)
                saveMetadata()
                Log.d(TAG, "Removed from cache: $mediaUrl")
            }
            
            deleted
        } catch (e: Exception) {
            Log.e(TAG, "Error removing from cache: $mediaUrl", e)
            false
        }
    }
    
    /**
     * Check if media is cached
     */
    fun isCached(mediaUrl: String): Boolean {
        return getCachedFile(mediaUrl) != null
    }
    
    /**
     * Get cache size in bytes
     */
    fun getCacheSize(): Long {
        return metadata.totalSize
    }
    
    /**
     * Get available cache space
     */
    fun getAvailableSpace(): Long {
        return metadata.getAvailableSpace()
    }
    
    /**
     * Get all cached media URLs
     */
    fun getCachedMediaUrls(): List<String> {
        return metadata.media.map { it.url }
    }
    
    /**
     * Clear entire cache
     */
    fun clearCache(): Boolean {
        return try {
            // Delete all files
            metadata.media.forEach { info ->
                FileUtils.deleteFile(info.getFile())
            }
            
            // Clear metadata
            metadata = CacheMetadata(maxCacheSize = metadata.maxCacheSize)
            saveMetadata()
            
            Log.d(TAG, "Cache cleared")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing cache", e)
            false
        }
    }
    
    /**
     * Evict least recently used files to free up space
     */
    fun evictLRU(requiredSpace: Long) {
        Log.d(TAG, "Evicting LRU files to free ${FileUtils.formatBytes(requiredSpace)}")
        
        val lruFiles = metadata.getLRUMedia()
        var freedSpace = 0L
        
        for (info in lruFiles) {
            if (freedSpace >= requiredSpace) {
                break
            }
            
            Log.d(TAG, "Evicting: ${info.url} (${FileUtils.formatBytes(info.fileSize)})")
            
            if (removeFromCache(info.url)) {
                freedSpace += info.fileSize
            }
        }
        
        Log.d(TAG, "Evicted ${FileUtils.formatBytes(freedSpace)}")
    }
    
    /**
     * Verify cache integrity
     */
    fun verifyIntegrity(): List<String> {
        val corruptedFiles = mutableListOf<String>()
        
        metadata.media.forEach { info ->
            if (!info.isValid()) {
                Log.w(TAG, "Corrupted file: ${info.url}")
                corruptedFiles.add(info.url)
                removeFromCache(info.url)
            } else if (info.checksum != null) {
                val file = info.getFile()
                if (!FileUtils.verifyChecksum(file, info.checksum)) {
                    Log.w(TAG, "Checksum mismatch: ${info.url}")
                    corruptedFiles.add(info.url)
                    removeFromCache(info.url)
                }
            }
        }
        
        if (corruptedFiles.isNotEmpty()) {
            Log.w(TAG, "Found ${corruptedFiles.size} corrupted files")
        }
        
        return corruptedFiles
    }
    
    /**
     * Get cache statistics
     */
    fun getStats(): CacheStats {
        return CacheStats(
            totalFiles = metadata.media.size,
            totalSize = metadata.totalSize,
            maxSize = metadata.maxCacheSize,
            availableSpace = metadata.getAvailableSpace(),
            imageCount = metadata.media.count { it.type == MediaType.IMAGE },
            videoCount = metadata.media.count { it.type == MediaType.VIDEO }
        )
    }
}

/**
 * Cache statistics
 */
data class CacheStats(
    val totalFiles: Int,
    val totalSize: Long,
    val maxSize: Long,
    val availableSpace: Long,
    val imageCount: Int,
    val videoCount: Int
) {
    val usagePercentage: Double
        get() = if (maxSize > 0) (totalSize.toDouble() / maxSize) * 100 else 0.0
    
    fun toFormattedString(): String {
        return """
            Files: $totalFiles (Images: $imageCount, Videos: $videoCount)
            Size: ${FileUtils.formatBytes(totalSize)} / ${FileUtils.formatBytes(maxSize)}
            Usage: ${String.format("%.1f", usagePercentage)}%
            Available: ${FileUtils.formatBytes(availableSpace)}
        """.trimIndent()
    }
}
