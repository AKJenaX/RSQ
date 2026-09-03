package com.example.rsq.data.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object NetworkModule {
    // Development BASE_URL:
    // 10.0.2.2 is for Android Emulator
    // 192.168.31.247 is the Windows PC LAN IP for physical device testing
    private const val BASE_URL = "http://192.168.31.247:8000/"

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(logging)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val razorpayService: RazorpayService = retrofit.create(RazorpayService::class.java)
}
