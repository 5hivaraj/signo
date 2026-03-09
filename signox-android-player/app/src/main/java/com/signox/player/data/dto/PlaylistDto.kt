package com.signox.player.data.dto

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class PlaylistDto(
    val id: String,
    val name: String,
    val items: List<PlaylistItemDto>
) : Parcelable

@Parcelize
data class PlaylistItemDto(
    val id: String,
    val order: Int,
    val duration: Int? = null, // seconds override
    val loopVideo: Boolean? = false,
    val orientation: String? = null, // LANDSCAPE or PORTRAIT
    val resizeMode: String? = "FIT", // FIT, FILL, STRETCH
    val rotation: Int? = 0, // 0, 90, 180, 270
    val media: MediaDto
) : Parcelable