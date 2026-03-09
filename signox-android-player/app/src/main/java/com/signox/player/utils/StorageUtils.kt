package com.signox.player.utils

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.util.Log
import java.io.File

/**
 * Storage utility functions
 */
object StorageUtils {
    
    private const val TAG = "StorageUtils"
    
    /**
     * Get available internal storage space in bytes
     */
    fun getAvailableInternalStorage(): Long {
        return try {
            val path = Environment.getDataDirectory()
            val stat = StatFs(path.path)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (e: Exception) {
            Log.e(TAG, "Error getting available storage", e)
            0L
        }
    }
    
    /**
     * Get total internal storage space in bytes
     */
    fun getTotalInternalStorage(): Long {
        return try {
            val path = Environment.getDataDirectory()
            val stat = StatFs(path.path)
            stat.blockCountLong * stat.blockSizeLong
        } catch (e: Exception) {
            Log.e(TAG, "Error getting total storage", e)
            0L
        }
    }
    
    /**
     * Get used internal storage space in bytes
     */
    fun getUsedInternalStorage(): Long {
        return getTotalInternalStorage() - getAvailableInternalStorage()
    }
    
    /**
     * Check if there's enough storage space available
     */
    fun hasEnoughSpace(requiredBytes: Long, bufferPercentage: Double = 0.1): Boolean {
        val available = getAvailableInternalStorage()
        val buffer = (requiredBytes * bufferPercentage).toLong()
        val required = requiredBytes + buffer
        
        Log.d(TAG, "Storage check - Required: ${FileUtils.formatBytes(required)}, " +
                   "Available: ${FileUtils.formatBytes(available)}")
        
        return available >= required
    }
    
    /**
     * Check if storage is critically low (less than 100MB)
     */
    fun isStorageCriticallyLow(): Boolean {
        val available = getAvailableInternalStorage()
        val criticalThreshold = 100L * 1024 * 1024 // 100 MB
        return available < criticalThreshold
    }
    
    /**
     * Get app's cache directory
     */
    fun getCacheDirectory(context: Context): File {
        return context.filesDir.resolve("media").also { dir ->
            if (!dir.exists()) {
                dir.mkdirs()
            }
        }
    }
    
    /**
     * Get images cache directory
     */
    fun getImagesCacheDirectory(context: Context): File {
        return getCacheDirectory(context).resolve("images").also { dir ->
            if (!dir.exists()) {
                dir.mkdirs()
            }
        }
    }
    
    /**
     * Get videos cache directory
     */
    fun getVideosCacheDirectory(context: Context): File {
        return getCacheDirectory(context).resolve("videos").also { dir ->
            if (!dir.exists()) {
                dir.mkdirs()
            }
        }
    }
    
    /**
     * Get cache metadata file
     */
    fun getCacheMetadataFile(context: Context): File {
        return getCacheDirectory(context).resolve("cache_metadata.json")
    }
    
    /**
     * Get cache size in bytes
     */
    fun getCacheSize(context: Context): Long {
        val cacheDir = getCacheDirectory(context)
        return FileUtils.getDirectorySize(cacheDir)
    }
    
    /**
     * Clear all cache
     */
    fun clearCache(context: Context): Boolean {
        val cacheDir = getCacheDirectory(context)
        return FileUtils.deleteDirectory(cacheDir).also { success ->
            if (success) {
                // Recreate directories
                getCacheDirectory(context)
                getImagesCacheDirectory(context)
                getVideosCacheDirectory(context)
                Log.d(TAG, "Cache cleared successfully")
            } else {
                Log.e(TAG, "Failed to clear cache")
            }
        }
    }
    
    /**
     * Get storage statistics
     */
    fun getStorageStats(context: Context): StorageStats {
        return StorageStats(
            totalSpace = getTotalInternalStorage(),
            availableSpace = getAvailableInternalStorage(),
            usedSpace = getUsedInternalStorage(),
            cacheSize = getCacheSize(context)
        )
    }
}

/**
 * Storage statistics data class
 */
data class StorageStats(
    val totalSpace: Long,
    val availableSpace: Long,
    val usedSpace: Long,
    val cacheSize: Long
) {
    val usedPercentage: Double
        get() = if (totalSpace > 0) (usedSpace.toDouble() / totalSpace) * 100 else 0.0
    
    val availablePercentage: Double
        get() = if (totalSpace > 0) (availableSpace.toDouble() / totalSpace) * 100 else 0.0
    
    fun toFormattedString(): String {
        return """
            Total: ${FileUtils.formatBytes(totalSpace)}
            Used: ${FileUtils.formatBytes(usedSpace)} (${String.format("%.1f", usedPercentage)}%)
            Available: ${FileUtils.formatBytes(availableSpace)} (${String.format("%.1f", availablePercentage)}%)
            Cache: ${FileUtils.formatBytes(cacheSize)}
        """.trimIndent()
    }
}
