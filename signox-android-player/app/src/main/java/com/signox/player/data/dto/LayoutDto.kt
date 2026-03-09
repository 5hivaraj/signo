package com.signox.player.data.dto

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class LayoutDto(
    val id: String,
    val name: String,
    val width: Int,
    val height: Int,
    val orientation: String? = "LANDSCAPE",
    val sections: List<LayoutSectionDto>
) : Parcelable

@Parcelize
data class LayoutSectionDto(
    val id: String,
    val name: String,
    val order: Int,
    val x: Float, // percentage 0-100
    val y: Float, // percentage 0-100
    val width: Float, // percentage 0-100
    val height: Float, // percentage 0-100
    val loopEnabled: Boolean,
    val frequency: Int? = null,
    val items: List<LayoutItemDto>
) : Parcelable

@Parcelize
data class LayoutItemDto(
    val id: String,
    val order: Int,
    val duration: Int? = null, // seconds
    val orientation: String? = null, // LANDSCAPE or PORTRAIT
    val resizeMode: String? = "FIT", // FIT, FILL, STRETCH
    val rotation: Int? = 0, // 0, 90, 180, 270
    val media: MediaDto
) : Parcelable