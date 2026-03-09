package com.signox.player.data.api

import com.signox.player.data.dto.*
import retrofit2.Response
import retrofit2.http.*

interface SignoXApi {
    
    // Pairing endpoints - match web player
    @POST("displays/pairing-code")
    suspend fun generatePairingCode(): Response<PairingCodeResponse>
    
    @POST("displays/check-status")
    suspend fun checkPairingStatus(@Body request: PairingStatusRequest): Response<PairingStatusResponse>
    
    @GET("displays/{displayId}/status")
    suspend fun getDisplayStatus(@Path("displayId") displayId: String): Response<DisplayStatusResponse>
    
    // Player config endpoint - match web player
    @GET("player/config")
    suspend fun getConfig(@Header("Authorization") token: String): Response<ConfigResponse>
    
    // Heartbeat endpoint - match web player
    @POST("displays/{displayId}/heartbeat")
    suspend fun sendHeartbeat(
        @Path("displayId") displayId: String,
        @Header("Authorization") token: String
    ): Response<Unit>
}