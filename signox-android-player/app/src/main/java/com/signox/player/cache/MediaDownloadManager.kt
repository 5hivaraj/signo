package com.signox.player.cache

import android.content.Context
import android.util.Log
import com.signox.player.data.api.ApiClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.util.*
import java.util.concurrent.PriorityBlockingQueue

/**
 * Manages background media downloads
 */
class MediaDownloadManager(
    private val context: Context,
    private val cacheManager: MediaCacheManager,
    private val networkMonitor: NetworkMonitor
) {
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val downloadQueue = PriorityBlockingQueue<DownloadTask>()
    private val activeDownloads = mutableMapOf<String, Job>()
    
    private val _downloadTasks = MutableStateFlow<Map<String, DownloadTask>>(emptyMap())
    val downloadTasks: StateFlow<Map<String, DownloadTask>> = _downloadTasks
    
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    
    @Volatile
    private var isRunning = false
    
    @Volatile
    private var allowCellularDownloads = true // Enable cellular downloads by default
    
    @Volatile
    private var maxConcurrentDownloads = 2
    
    companion object {
        private const val TAG = "MediaDownloadManager"
    }
    
    init {
        // Observe network changes
        scope.launch {
            networkMonitor.observeNetworkState().collect { networkState ->
                Log.d(TAG, "Network state changed: $networkState")
                when (networkState) {
                    NetworkState.WIFI -> resumeDownloads()
                    NetworkState.CELLULAR -> {
                        if (!allowCellularDownloads) {
                            pauseDownloads()
                        }
                    }
                    NetworkState.OFFLINE -> pauseDownloads()
                }
            }
        }
    }
    
    /**
     * Start download manager
     */
    fun start() {
        if (isRunning) {
            Log.d(TAG, "Already running")
            return
        }
        
        isRunning = true
        Log.d(TAG, "Started")
        
        // Start worker coroutines
        repeat(maxConcurrentDownloads) { workerId ->
            scope.launch {
                processDownloadQueue(workerId)
            }
        }
    }
    
    /**
     * Stop download manager
     */
    fun stop() {
        isRunning = false
        Log.d(TAG, "Stopped")
        
        // Cancel all active downloads
        activeDownloads.values.forEach { it.cancel() }
        activeDownloads.clear()
    }
    
    /**
     * Queue media for download
     */
    fun queueDownload(
        mediaUrl: String,
        priority: DownloadPriority = DownloadPriority.MEDIUM
    ) {
        // Check if already cached
        if (cacheManager.isCached(mediaUrl)) {
            Log.d(TAG, "Already cached: $mediaUrl")
            return
        }
        
        // Check if already in queue or downloading
        val existingTask = _downloadTasks.value[mediaUrl]
        if (existingTask != null && (existingTask.isPending() || existingTask.isInProgress())) {
            Log.d(TAG, "Already queued/downloading: $mediaUrl")
            return
        }
        
        val fullUrl = ApiClient.getMediaUrl(mediaUrl)
        val mediaType = MediaType.fromUrl(mediaUrl)
        
        val task = DownloadTask(
            id = UUID.randomUUID().toString(),
            mediaUrl = mediaUrl,
            fullUrl = fullUrl,
            mediaType = mediaType,
            priority = priority
        )
        
        downloadQueue.offer(task)
        updateTaskState(task)
        
        Log.d(TAG, "Queued: $mediaUrl (priority: $priority)")
    }
    
    /**
     * Queue multiple media files
     */
    fun queueDownloads(
        mediaUrls: List<String>,
        priority: DownloadPriority = DownloadPriority.MEDIUM
    ) {
        mediaUrls.forEach { url ->
            queueDownload(url, priority)
        }
    }
    
    /**
     * Cancel download
     */
    fun cancelDownload(mediaUrl: String) {
        // Cancel active download
        activeDownloads[mediaUrl]?.cancel()
        activeDownloads.remove(mediaUrl)
        
        // Remove from queue
        downloadQueue.removeAll { it.mediaUrl == mediaUrl }
        
        // Update task state
        _downloadTasks.value[mediaUrl]?.let { task ->
            task.markAsCancelled()
            updateTaskState(task)
        }
        
        Log.d(TAG, "Cancelled: $mediaUrl")
    }
    
    /**
     * Pause all downloads
     */
    fun pauseDownloads() {
        Log.d(TAG, "Pausing downloads")
        activeDownloads.values.forEach { it.cancel() }
        activeDownloads.clear()
    }
    
    /**
     * Resume downloads
     */
    fun resumeDownloads() {
        Log.d(TAG, "Resuming downloads")
        // Downloads will resume automatically as workers process the queue
    }
    
    /**
     * Clear completed downloads from state
     */
    fun clearCompleted() {
        val tasks = _downloadTasks.value.toMutableMap()
        tasks.entries.removeAll { it.value.isCompleted() }
        _downloadTasks.value = tasks
    }
    
    /**
     * Get download progress for a media URL
     */
    fun getProgress(mediaUrl: String): Int {
        return _downloadTasks.value[mediaUrl]?.progress ?: 0
    }
    
    /**
     * Check if media is downloading
     */
    fun isDownloading(mediaUrl: String): Boolean {
        return _downloadTasks.value[mediaUrl]?.isInProgress() == true
    }
    
    /**
     * Get queue size
     */
    fun getQueueSize(): Int {
        return downloadQueue.size
    }
    
    /**
     * Get active downloads count
     */
    fun getActiveDownloadsCount(): Int {
        return activeDownloads.size
    }
    
    /**
     * Set whether to allow downloads on cellular
     */
    fun setAllowCellularDownloads(allow: Boolean) {
        allowCellularDownloads = allow
        Log.d(TAG, "Allow cellular downloads: $allow")
        
        if (allow && networkMonitor.isCellularConnected()) {
            resumeDownloads()
        }
    }
    
    /**
     * Process download queue (worker coroutine)
     */
    private suspend fun processDownloadQueue(workerId: Int) {
        Log.d(TAG, "Worker $workerId started")
        
        while (isRunning) {
            try {
                // Check if we should download
                if (!shouldDownload()) {
                    delay(1000)
                    continue
                }
                
                // Get next task from queue
                val task = downloadQueue.poll()
                if (task == null) {
                    delay(500)
                    continue
                }
                
                // Download the file
                downloadFile(task, workerId)
                
            } catch (e: CancellationException) {
                Log.d(TAG, "Worker $workerId cancelled")
                return // Exit the worker coroutine
            } catch (e: Exception) {
                Log.e(TAG, "Worker $workerId error", e)
                delay(1000)
            }
        }
        
        Log.d(TAG, "Worker $workerId stopped")
    }
    
    /**
     * Check if downloads should proceed
     */
    private fun shouldDownload(): Boolean {
        return when (networkMonitor.getNetworkState()) {
            NetworkState.WIFI -> true
            NetworkState.CELLULAR -> allowCellularDownloads
            NetworkState.OFFLINE -> false
        }
    }
    
    /**
     * Download a single file
     */
    private suspend fun downloadFile(task: DownloadTask, workerId: Int) {
        val job = scope.launch {
            try {
                Log.d(TAG, "Worker $workerId downloading: ${task.mediaUrl}")
                
                task.markAsStarted()
                updateTaskState(task)
                
                // Track active download
                activeDownloads[task.mediaUrl] = coroutineContext[Job]!!
                
                // Build request
                val request = Request.Builder()
                    .url(task.fullUrl)
                    .build()
                
                // Execute request
                val response = okHttpClient.newCall(request).execute()
                
                if (!response.isSuccessful) {
                    throw Exception("HTTP ${response.code}: ${response.message}")
                }
                
                val contentLength = response.body?.contentLength() ?: 0
                task.totalBytes = contentLength
                
                // Download and cache
                response.body?.byteStream()?.use { inputStream ->
                    val cachedFile = cacheManager.addToCache(
                        mediaUrl = task.mediaUrl,
                        inputStream = ProgressInputStream(inputStream) { downloaded ->
                            task.updateProgress(downloaded, contentLength)
                            updateTaskState(task)
                        },
                        mediaType = task.mediaType
                    )
                    
                    if (cachedFile != null) {
                        task.markAsCompleted()
                        Log.d(TAG, "Worker $workerId completed: ${task.mediaUrl}")
                    } else {
                        throw Exception("Failed to cache file")
                    }
                }
                
                updateTaskState(task)
                
            } catch (e: CancellationException) {
                Log.d(TAG, "Worker $workerId cancelled: ${task.mediaUrl}")
                task.markAsCancelled()
                updateTaskState(task)
            } catch (e: Exception) {
                Log.e(TAG, "Worker $workerId failed: ${task.mediaUrl}", e)
                task.markAsFailed(e.message ?: "Unknown error")
                updateTaskState(task)
            } finally {
                activeDownloads.remove(task.mediaUrl)
            }
        }
        
        job.join()
    }
    
    /**
     * Update task state
     */
    private fun updateTaskState(task: DownloadTask) {
        val tasks = _downloadTasks.value.toMutableMap()
        tasks[task.mediaUrl] = task
        _downloadTasks.value = tasks
    }
    
    /**
     * Input stream wrapper that reports progress
     */
    private class ProgressInputStream(
        private val inputStream: InputStream,
        private val onProgress: (Long) -> Unit
    ) : InputStream() {
        
        private var totalBytesRead = 0L
        
        override fun read(): Int {
            val byte = inputStream.read()
            if (byte != -1) {
                totalBytesRead++
                onProgress(totalBytesRead)
            }
            return byte
        }
        
        override fun read(b: ByteArray): Int {
            val bytesRead = inputStream.read(b)
            if (bytesRead > 0) {
                totalBytesRead += bytesRead
                onProgress(totalBytesRead)
            }
            return bytesRead
        }
        
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val bytesRead = inputStream.read(b, off, len)
            if (bytesRead > 0) {
                totalBytesRead += bytesRead
                onProgress(totalBytesRead)
            }
            return bytesRead
        }
        
        override fun close() {
            inputStream.close()
        }
    }
}
