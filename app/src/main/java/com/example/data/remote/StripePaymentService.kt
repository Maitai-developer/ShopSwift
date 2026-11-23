package com.example.data.remote

import com.example.BuildConfig
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class PaymentIntentRequest(
    val amountCents: Long,
    val currency: String = "usd",
    val customerEmail: String,
    val description: String
)

@JsonClass(generateAdapter = true)
data class PaymentIntentResponse(
    val paymentIntentClientSecret: String,
    val customerId: String? = null,
    val ephemeralKey: String? = null,
    val paymentIntentId: String? = null
)

interface StripeBackendApi {
    @POST("create-payment-intent")
    suspend fun createPaymentIntent(@Body request: PaymentIntentRequest): PaymentIntentResponse
}

object StripeConfigHelper {
    private const val PLACEHOLDER_KEY = "pk_test_placeholder"
    private const val PLACEHOLDER_URL = "https://api.example.com/stripe/"

    fun publishableKey(): String = BuildConfig.STRIPE_PUBLISHABLE_KEY.trim()

    fun backendBaseUrl(): String = BuildConfig.STRIPE_BACKEND_URL.trim()

    fun isLiveStripeConfigured(): Boolean {
        val key = publishableKey()
        val url = backendBaseUrl()
        return key.isNotBlank() &&
            key != PLACEHOLDER_KEY &&
            key.startsWith("pk_") &&
            url.isNotBlank() &&
            url != PLACEHOLDER_URL &&
            url.startsWith("https://")
    }

    fun createBackendApi(): StripeBackendApi {
        val rawUrl = backendBaseUrl().ifBlank { PLACEHOLDER_URL }
        val normalizedUrl = if (rawUrl.endsWith("/")) rawUrl else "$rawUrl/"

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()

        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

        return Retrofit.Builder()
            .baseUrl(normalizedUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(StripeBackendApi::class.java)
    }
}
