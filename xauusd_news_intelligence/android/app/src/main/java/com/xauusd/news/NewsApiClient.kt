package com.xauusd.news

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object NewsApiClient {
    fun registerDevice(baseUrl: String, code: String, token: String): Result<String> =
        sendDeviceRequest(baseUrl, "POST", code, token, "Perangkat berhasil didaftarkan untuk menerima peringatan berita.")

    fun unregisterDevice(baseUrl: String, code: String, token: String): Result<String> =
        sendDeviceRequest(baseUrl, "DELETE", code, token, "Perangkat berhasil dihapus dari daftar push.")

    private fun sendDeviceRequest(baseUrl: String, method: String, code: String, token: String, successMessage: String): Result<String> = runCatching {
        val endpoint = URL("${baseUrl.trim().trimEnd('/')}/api/register-device")
        val connection = endpoint.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 8_000
            connection.readTimeout = 10_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            val body = JSONObject().put("code", code).put("token", token).toString().toByteArray(Charsets.UTF_8)
            connection.outputStream.use { it.write(body) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val message = runCatching { JSONObject(response).optString("error") }.getOrNull().orEmpty()
                throw IllegalStateException(message.ifBlank { "Backend merespons HTTP $status" })
            }
            successMessage
        } finally {
            connection.disconnect()
        }
    }
}

