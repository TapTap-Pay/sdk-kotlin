package rs.taptap.sdk

import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp interceptor that stamps every request with
 * `Authorization: Bearer <apiKey>`. Applied to the OkHttpClient used
 * by Connect-Kotlin's transport, so it covers unary and streaming.
 */
internal class AuthInterceptor(private val apiKey: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(
            chain.request().newBuilder()
                .header("Authorization", "Bearer $apiKey")
                .build(),
        )
}

/**
 * Identifies the SDK in User-Agent for support attribution and to
 * surface SDK version issues. Caller-supplied `extra` (e.g.
 * "my-app/1.4.0") is appended if present.
 */
internal class UserAgentInterceptor(extra: String?) : Interceptor {
    private val value: String = buildString {
        append("taptap-sdk-kotlin/").append(VERSION)
        append(" (jvm/").append(System.getProperty("java.version") ?: "unknown").append(')')
        if (!extra.isNullOrBlank()) append(' ').append(extra)
    }

    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(
            chain.request().newBuilder()
                .header("User-Agent", value)
                .build(),
        )
}
