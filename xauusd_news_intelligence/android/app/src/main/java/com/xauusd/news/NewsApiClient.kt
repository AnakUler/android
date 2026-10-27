package com.xauusd.news

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

data class WorkerHealth(
    val ok: Boolean,
    val registrationCodeConfigured: Boolean,
    val fcmConfigured: Boolean,
)

object NewsApiClient {
    fun normalizeBaseUrl(value: String): String {
        val trimmed = value.trim().trimEnd('/')
        val uri = runCatching { URI(trimmed) }.getOrElse { throw IllegalArgumentException("Masukkan URL Worker yang valid.") }
        require(uri.scheme.equals("https", ignoreCase = true)) { "URL backend harus menggunakan HTTPS." }
        require(!uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null) {
            "Masukkan alamat Worker HTTPS tanpa akun, parameter, atau fragmen URL."
        }
        require(uri.path.isNullOrBlank() || uri.path == "/") { "Masukkan alamat Worker utama tanpa path tambahan." }
        return "https://${uri.rawAuthority}"
    }

    fun parseHealth(raw: String): WorkerHealth {
        val json = runCatching { JSONObject(raw) }.getOrElse { throw IllegalStateException("Respons Worker bukan JSON yang valid.") }
        if (!json.optBoolean("ok", false)) throw IllegalStateException("Endpoint ini bukan XAUUSD News Worker yang aktif.")
        val configured = json.optJSONObject("configured")
        return WorkerHealth(
            ok = true,
            registrationCodeConfigured = configured?.optBoolean("registrationCode", false) == true,
            fcmConfigured = configured?.optBoolean("fcm", false) == true,
        )
    }

    fun checkHealth(baseUrl: String): Result<WorkerHealth> = runCatching {
        val raw = get("${normalizeBaseUrl(baseUrl)}/api/health")
        parseHealth(raw)
    }

    fun fetchNews(baseUrl: String): Result<String> = runCatching {
        get("${normalizeBaseUrl(baseUrl)}/api/news")
    }

    fun registerDevice(baseUrl: String, code: String, token: String): Result<String> =
        sendDeviceRequest(baseUrl, "POST", code, token, "Perangkat berhasil didaftarkan untuk menerima peringatan berita.")

    fun unregisterDevice(baseUrl: String, code: String, token: String): Result<String> =
        sendDeviceRequest(baseUrl, "DELETE", code, token, "Perangkat berhasil dihapus dari daftar push.")

    private fun get(endpoint: String): String {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 8_000
            connection.readTimeout = 10_000
            return readResponse(connection)
        } finally {
            connection.disconnect()
        }
    }

    private fun sendDeviceRequest(baseUrl: String, method: String, code: String, token: String, successMessage: String): Result<String> = runCatching {
        val endpoint = URL("${normalizeBaseUrl(baseUrl)}/api/register-device")
        val connection = endpoint.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 8_000
            connection.readTimeout = 10_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            val body = JSONObject().put("code", code).put("token", token).toString().toByteArray(Charsets.UTF_8)
            connection.outputStream.use { it.write(body) }
            readResponse(connection)
            successMessage
        } finally {
            connection.disconnect()
        }
    }

    private fun readResponse(connection: HttpURLConnection): String {
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        if (status !in 200..299) {
            val message = runCatching { JSONObject(response).optString("error") }.getOrNull().orEmpty()
            throw IllegalStateException(message.ifBlank { "Backend merespons HTTP $status." })
        }
        return response
    }
}

