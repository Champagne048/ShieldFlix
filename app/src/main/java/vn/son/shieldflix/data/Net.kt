package vn.son.shieldflix.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Gọi HTTP dùng chung (OkHttp, chạy trên Dispatchers.IO). */
object Net {
    const val USER_AGENT = "ShieldFlix v1.0"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build()

    /** Không tự đi theo chuyển hướng: Jackett hay trả 302 sang "magnet:". */
    val noRedirect: OkHttpClient = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private fun request(url: String, headers: Map<String, String>): Request.Builder {
        val b = Request.Builder().url(url).header("User-Agent", USER_AGENT)
        headers.forEach { (k, v) -> b.header(k, v) }
        return b
    }

    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String =
        withContext(Dispatchers.IO) {
            client.newCall(request(url, headers).build()).execute().use { r ->
                val body = r.body?.string() ?: ""
                if (!r.isSuccessful) throw IOException("HTTP ${r.code} – ${url.substringBefore('?')}")
                body
            }
        }

    suspend fun bytes(url: String, headers: Map<String, String> = emptyMap()): ByteArray =
        withContext(Dispatchers.IO) {
            client.newCall(request(url, headers).build()).execute().use { r ->
                if (!r.isSuccessful) throw IOException("HTTP ${r.code} – ${url.substringBefore('?')}")
                r.body?.bytes() ?: ByteArray(0)
            }
        }

    suspend fun postJson(url: String, json: String, headers: Map<String, String> = emptyMap()): String =
        withContext(Dispatchers.IO) {
            val body = json.toRequestBody("application/json".toMediaType())
            client.newCall(request(url, headers).post(body).build()).execute().use { r ->
                val text = r.body?.string() ?: ""
                if (!r.isSuccessful) throw IOException("HTTP ${r.code} – ${text.take(200)}")
                text
            }
        }
}
