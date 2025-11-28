package com.ead.boshi_client.data.network

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Factory for creating BoshiApi instances with Retrofit.
 * Handles HTTP client configuration and serialization.
 */
object BoshiClient {
    /**
     * Creates a BoshiApi instance configured for local testing.
     *
     * @param baseUrl The base URL for the API (e.g., "http://localhost:8080")
     * @return Configured BoshiApi instance
     */
    fun create(baseUrl: String = "http://localhost:8080"): BoshiService {
        // Configure OkHttp client
        val httpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            // Add logging interceptor for debugging
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()

        // Configure JSON serialization
        val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = false
            isLenient = true
        }

        // Create Retrofit instance
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(httpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        return retrofit.create(BoshiService::class.java)
    }
}
