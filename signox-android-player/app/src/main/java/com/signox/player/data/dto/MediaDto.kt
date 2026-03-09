package com.signox.player.data.dto

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class MediaDto(
    val id: String,
    val name: String,
    val type: MediaType,
    val url: String,
    val originalUrl: String? = null, // Original MP4 URL for offline playback
    val duration: Int? = null,
    val mimeType: String? = null
) : Parcelable

enum class MediaType {
    IMAGE, VIDEO
}