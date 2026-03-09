package com.signox.player.data.dto

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class DisplayDto(
    val id: String,
    val name: String,
    val pairingCode: String? = null,
    val isPaired: Boolean = false,
    val isOnline: Boolean = false,
    val lastSeen: String? = null,
    val deviceToken: String? = null,
    val clientId: String? = null
) : Parcelable

// New API response models to match web player
@Parcelize
data class PairingCodeResponse(
    val pairingCode: String,
    val displayId: String
) : Parcelable

@Parcelize
data class PairingStatusRequest(
    val pairingCode: String
) : Parcelable

@Parcelize
data class PairingStatusResponse(
    val isPaired: Boolean,
    val deviceToken: String? = null,
    val displayId: String? = null
) : Parcelable

@Parcelize
data class DisplayStatusResponse(
    val id: String,
    val pairingCode: String,
    val isPaired: Boolean,
    val deviceToken: String? = null
) : Parcelable

@Parcelize
data class ConfigResponse(
    val playlist: PlaylistDto? = null,
    val layout: LayoutDto? = null,
    val activeSchedule: ActiveScheduleDto? = null
) : Parcelable

@Parcelize
data class ActiveScheduleDto(
    val id: String,
    val name: String,
    val priority: Int
) : Parcelable