package com.zaidashiq.chat.data.network

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        encodeDefaults = true
    }

    fun create(deviceId: String): RailwayApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        }

        val okHttp = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(180, TimeUnit.SECONDS)
            .callTimeout(240, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val reqBuilder = chain.request().newBuilder()
                    .header("X-Device-Id", deviceId)
                    .header("Accept", "application/json")
                if (BackendConfig.isGiphyConfigured) {
                    reqBuilder.header("X-Giphy-Key", BackendConfig.giphyApiKey)
                }
                chain.proceed(reqBuilder.build())
            }
            .addInterceptor(logging)
            .build()

        return Retrofit.Builder()
            .baseUrl(BackendConfig.normalizedRailwayBaseUrl)
            .client(okHttp)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(RailwayApiService::class.java)
    }
}
