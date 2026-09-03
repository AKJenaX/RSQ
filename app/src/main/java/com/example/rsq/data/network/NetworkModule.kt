package com.example.rsq.data.network

import android.os.Build
import android.util.Log
import com.example.rsq.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {

    /**
     * Inspects device parameters to determine if running on an Android Emulator or physical hardware.
     */
    private fun isEmulator(): Boolean {
        val isEmu = (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk" == Build.PRODUCT)
        Log.d("NetworkModule", "Device inspection - isEmulator: $isEmu, Model: ${Build.MODEL}, Fingerprint: ${Build.FINGERPRINT}")
        return isEmu
    }

    /**
     * Active Base URL resolved dynamically based on execution environment:
     * - Android Emulator: http://10.0.2.2:8000/
     * - Physical Device: http://10.10.12.27:8000/
     * - Release: https://api.rsq-app.com/
     */
    val BASE_URL: String by lazy {
        val url = if (isEmulator()) {
            BuildConfig.EMULATOR_BASE_URL
        } else {
            BuildConfig.DEV_LAN_BASE_URL
        }
        Log.i("NetworkModule", "==================================================")
        Log.i("NetworkModule", "ACTIVE BACKEND BASE_URL RESOLVED: $url")
        Log.i("NetworkModule", "==================================================")
        url
    }

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(logging)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val razorpayService: RazorpayService by lazy {
        retrofit.create(RazorpayService::class.java)
    }
}
