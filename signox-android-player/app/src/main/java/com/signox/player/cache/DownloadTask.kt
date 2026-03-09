package com.signox.player.cache

/**
 * Represents a media download task
 */
data class DownloadTask(
    val id: String,
    val mediaUrl: String,
    val fullUrl: String,
    val mediaType: MediaType,
    val priority: DownloadPriority,
    var status: DownloadStatus = DownloadStatus.PENDING,
    var progress: Int = 0,
    var downloadedBytes: Long = 0,
    var totalBytes: Long = 0,
    var error: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    var startedAt: Long? = null,
    var completedAt: Long? = null
) : Comparable<DownloadTask> {
    
    /**
     * Compare tasks by priority (higher priority first)
     */
    override fun compareTo(other: DownloadTask): Int {
        // First compare by priority
        val priorityComparison = other.priority.ordinal.compareTo(this.priority.ordinal)
        if (priorityComparison != 0) {
            return priorityComparison
        }
        
        // Then by creation time (older first)
        return this.createdAt.compareTo(other.createdAt)
    }
    
    /**
     * Check if download is in progress
     */
    fun isInProgress(): Boolean {
        return status == DownloadStatus.DOWNLOADING
    }
    
    /**
     * Check if download is completed
     */
    fun isCompleted(): Boolean {
        return status == DownloadStatus.COMPLETED
    }
    
    /**
     * Check if download failed
     */
    fun isFailed(): Boolean {
        return status == DownloadStatus.FAILED
    }
    
    /**
     * Check if download is pending
     */
    fun isPending(): Boolean {
        return status == DownloadStatus.PENDING
    }
    
    /**
     * Get download speed in bytes per second
     */
    fun getDownloadSpeed(): Long {
        if (startedAt == null || downloadedBytes == 0L) {
            return 0
        }
        
        val elapsedSeconds = (System.currentTimeMillis() - startedAt!!) / 1000.0
        return if (elapsedSeconds > 0) {
            (downloadedBytes / elapsedSeconds).toLong()
        } else {
            0
        }
    }
    
    /**
     * Get estimated time remaining in seconds
     */
    fun getEstimatedTimeRemaining(): Long {
        val speed = getDownloadSpeed()
        if (speed == 0L || totalBytes == 0L) {
            return 0
        }
        
        val remainingBytes = totalBytes - downloadedBytes
        return remainingBytes / speed
    }
    
    /**
     * Update progress
     */
    fun updateProgress(downloaded: Long, total: Long) {
        downloadedBytes = downloaded
        totalBytes = total
        progress = if (total > 0) {
            ((downloaded.toDouble() / total) * 100).toInt()
        } else {
            0
        }
    }
    
    /**
     * Mark as started
     */
    fun markAsStarted() {
        status = DownloadStatus.DOWNLOADING
        startedAt = System.currentTimeMillis()
    }
    
    /**
     * Mark as completed
     */
    fun markAsCompleted() {
        status = DownloadStatus.COMPLETED
        progress = 100
        completedAt = System.currentTimeMillis()
    }
    
    /**
     * Mark as failed
     */
    fun markAsFailed(errorMessage: String) {
        status = DownloadStatus.FAILED
        error = errorMessage
        completedAt = System.currentTimeMillis()
    }
    
    /**
     * Mark as cancelled
     */
    fun markAsCancelled() {
        status = DownloadStatus.CANCELLED
        completedAt = System.currentTimeMillis()
    }
}
