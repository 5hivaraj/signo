package com.signox.player.cache

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.File

/**
 * Metadata for cached media files
 */
data class CacheMetadata(
    @SerializedName("media")
    val media: MutableList<CachedMediaInfo> = mutableListOf(),
    
    @SerializedName("totalSize")
    var totalSize: Long = 0,
    
    @SerializedName("maxCacheSize")
    val maxCacheSize: Long = 5L * 1024 * 1024 * 1024, // 5GB default
    
    @SerializedName("lastUpdated")
    var lastUpdated: Long = System.currentTimeMillis()
) {
    companion object {
        fun fromJson(json: String): CacheMetadata {
            return try {
                Gson().fromJson(json, CacheMetadata::class.java)
            } catch (e: Exception) {
                CacheMetadata()
            }
        }
    }
    
    fun toJson(): String {
        return Gson().toJson(this)
    }
    
    fun addMedia(info: CachedMediaInfo) {
        // Remove existing entry if present
        media.removeAll { it.url == info.url }
        media.add(info)
        recalculateTotalSize()
    }
    
    fun removeMedia(url: String) {
        media.removeAll { it.url == url }
        recalculateTotalSize()
    }
    
    fun getMedia(url: String): CachedMediaInfo? {
        return media.find { it.url == url }
    }
    
    fun updateLastUsed(url: String) {
        media.find { it.url == url }?.lastUsed = System.currentTimeMillis()
        lastUpdated = System.currentTimeMillis()
    }
    
    fun recalculateTotalSize() {
        totalSize = media.sumOf { it.fileSize }
        lastUpdated = System.currentTimeMillis()
    }
    
    fun getLRUMedia(): List<CachedMediaInfo> {
        return media.sortedBy { it.lastUsed }
    }
    
    fun getAvailableSpace(): Long {
        return maxCacheSize - totalSize
    }
    
    fun needsEviction(requiredSpace: Long): Boolean {
        return getAvailableSpace() < requiredSpace
    }
}

/**
 * Information about a single cached media file
 */
data class CachedMediaInfo(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("url")
    val url: String,
    
    @SerializedName("localPath")
    val localPath: String,
    
    @SerializedName("fileSize")
    val fileSize: Long,
    
    @SerializedName("checksum")
    val checksum: String? = null,
    
    @SerializedName("type")
    val type: MediaType,
    
    @SerializedName("downloadedAt")
    val downloadedAt: Long = System.currentTimeMillis(),
    
    @SerializedName("lastUsed")
    var lastUsed: Long = System.currentTimeMillis()
) {
    fun getFile(): File = File(localPath)
    
    fun exists(): Boolean = getFile().exists()
    
    fun isValid(): Boolean = exists() && getFile().length() == fileSize
}

/**
 * Media type enum
 */
enum class MediaType {
    @SerializedName("image")
    IMAGE,
    
    @SerializedName("video")
    VIDEO,
    
    @SerializedName("unknown")
    UNKNOWN;
    
    companion object {
        fun fromUrl(url: String): MediaType {
            return when {
                url.endsWith(".jpg", ignoreCase = true) ||
                url.endsWith(".jpeg", ignoreCase = true) ||
                url.endsWith(".png", ignoreCase = true) ||
                url.endsWith(".gif", ignoreCase = true) ||
                url.endsWith(".webp", ignoreCase = true) -> IMAGE
                
                url.endsWith(".mp4", ignoreCase = true) ||
                url.endsWith(".webm", ignoreCase = true) ||
                url.endsWith(".mkv", ignoreCase = true) ||
                url.endsWith(".avi", ignoreCase = true) -> VIDEO
                
                else -> UNKNOWN
            }
        }
        
        fun fromMimeType(mimeType: String): MediaType {
            return when {
                mimeType.startsWith("image/") -> IMAGE
                mimeType.startsWith("video/") -> VIDEO
                else -> UNKNOWN
            }
        }
    }
}

/**
 * Download priority levels
 */
enum class DownloadPriority {
    HIGH,    // Currently playing or next in queue
    MEDIUM,  // In current playlist/layout
    LOW      // Other content
}

/**
 * Download status
 */
enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    CANCELLED
}

/**
 * Network state
 */
enum class NetworkState {
    WIFI,
    CELLULAR,
    OFFLINE
}
