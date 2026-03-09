package com.signox.player.data.api

import com.signox.player.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    
    // Server URL comes from BuildConfig so we can use
    // different backends for debug (local) and release (production)
    private const val DEFAULT_BASE_URL = "https://www.signoxcms.com/api"
    private var baseUrl: String = BuildConfig.API_BASE_URL.ifEmpty { DEFAULT_BASE_URL }
    private var retrofit: Retrofit? = null
    
    fun setBaseUrl(url: String) {
        // Intentionally ignored at runtime – the URL is fixed per build
        // via BuildConfig.API_BASE_URL (see app/build.gradle.kts).
        // This method is kept for API compatibility only.
    }
    
    fun getBaseUrl(): String = baseUrl
    
    fun hasBaseUrl(): Boolean = true
    
    private fun getRetrofit(): Retrofit {
        if (retrofit == null) {
            val currentBaseUrl = baseUrl
            
            val logging = HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BODY
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            }
            
            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
            
            retrofit = Retrofit.Builder()
                .baseUrl("$currentBaseUrl/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
        return retrofit!!
    }
    
    val api: SignoXApi by lazy {
        getRetrofit().create(SignoXApi::class.java)
    }
    
    fun getMediaUrl(mediaUrl: String): String {
        return if (mediaUrl.startsWith("http")) {
            mediaUrl
        } else {
            // Media files are served from /uploads, not /api/uploads
            // Remove /api from base URL for media files
            val mediaBaseUrl = baseUrl.replace("/api", "")
            "$mediaBaseUrl${mediaUrl}"
        }
    }
}