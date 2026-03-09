package com.signox.player.utils

import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest

/**
 * File utility functions
 */
object FileUtils {
    
    private const val TAG = "FileUtils"
    
    /**
     * Calculate SHA-256 checksum of a file
     */
    fun calculateChecksum(file: File): String? {
        if (!file.exists()) {
            Log.w(TAG, "File does not exist: ${file.absolutePath}")
            return null
        }
        
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating checksum", e)
            null
        }
    }
    
    /**
     * Calculate checksum from input stream
     */
    fun calculateChecksum(inputStream: InputStream): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            var bytesRead: Int
            
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
            
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating checksum from stream", e)
            null
        }
    }
    
    /**
     * Verify file checksum
     */
    fun verifyChecksum(file: File, expectedChecksum: String): Boolean {
        val actualChecksum = calculateChecksum(file) ?: return false
        return actualChecksum.equals(expectedChecksum, ignoreCase = true)
    }
    
    /**
     * Get file extension from URL or filename
     */
    fun getFileExtension(url: String): String {
        val fileName = url.substringAfterLast('/')
        return if (fileName.contains('.')) {
            fileName.substringAfterLast('.')
        } else {
            ""
        }
    }
    
    /**
     * Generate safe filename from URL
     */
    fun getSafeFileName(url: String): String {
        // Extract filename from URL
        val fileName = url.substringAfterLast('/')
            .substringBefore('?') // Remove query parameters
            .replace(Regex("[^a-zA-Z0-9._-]"), "_") // Replace unsafe characters
        
        return if (fileName.isNotEmpty()) fileName else "media_${System.currentTimeMillis()}"
    }
    
    /**
     * Delete file safely
     */
    fun deleteFile(file: File): Boolean {
        return try {
            if (file.exists()) {
                val deleted = file.delete()
                if (deleted) {
                    Log.d(TAG, "Deleted file: ${file.absolutePath}")
                } else {
                    Log.w(TAG, "Failed to delete file: ${file.absolutePath}")
                }
                deleted
            } else {
                Log.d(TAG, "File does not exist: ${file.absolutePath}")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting file: ${file.absolutePath}", e)
            false
        }
    }
    
    /**
     * Delete directory recursively
     */
    fun deleteDirectory(directory: File): Boolean {
        return try {
            if (directory.exists() && directory.isDirectory) {
                directory.listFiles()?.forEach { file ->
                    if (file.isDirectory) {
                        deleteDirectory(file)
                    } else {
                        file.delete()
                    }
                }
                directory.delete()
            } else {
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting directory: ${directory.absolutePath}", e)
            false
        }
    }
    
    /**
     * Get directory size in bytes
     */
    fun getDirectorySize(directory: File): Long {
        if (!directory.exists() || !directory.isDirectory) {
            return 0
        }
        
        var size = 0L
        directory.listFiles()?.forEach { file ->
            size += if (file.isDirectory) {
                getDirectorySize(file)
            } else {
                file.length()
            }
        }
        
        return size
    }
    
    /**
     * Format bytes to human-readable string
     */
    fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.2f KB".format(bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> "%.2f MB".format(bytes / (1024.0 * 1024))
            else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
        }
    }
    
    /**
     * Create directory if it doesn't exist
     */
    fun ensureDirectoryExists(directory: File): Boolean {
        return try {
            if (!directory.exists()) {
                directory.mkdirs()
            } else {
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating directory: ${directory.absolutePath}", e)
            false
        }
    }
}
