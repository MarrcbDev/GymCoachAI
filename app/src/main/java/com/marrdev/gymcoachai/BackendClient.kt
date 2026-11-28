package com.marrdev.gymcoachai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class BackendReply(val content: String, val remaining: Int?, val limit: Int?)

/**
 * Cliente del backend (Cloudflare Worker, ver BACKEND.md). El backend valida el token de Firebase,
 * aplica el límite diario y usa la API Key del desarrollador, que nunca viaja dentro de la app.
 */
class BackendClient(private val baseUrl: String) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()
    private val json = "application/json; charset=utf-8".toMediaType()

    suspend fun chat(idToken: String, model: String, system: String, messages: List<Pair<String, String>>): BackendReply =
        withContext(Dispatchers.IO) {
            val arr = JSONArray()
            messages.forEach { arr.put(JSONObject().put("role", it.first).put("content", it.second)) }
            val body = JSONObject().put("model", model).put("system", system).put("messages", arr)
            val request = Request.Builder().url("$baseUrl/chat")
                .header("Authorization", "Bearer $idToken")
                .post(body.toString().toRequestBody(json)).build()
            client.newCall(request).execute().use { response ->
                val payload = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw ApiException(response.code, serverError(payload, response.code))
                val obj = JSONObject(payload)
                BackendReply(
                    content = obj.optString("content", ""),
                    remaining = if (obj.has("remaining")) obj.optInt("remaining") else null,
                    limit = if (obj.has("limit")) obj.optInt("limit") else null,
                )
            }
        }

    /** Modelos que el backend permite usar. */
    suspend fun models(idToken: String): List<String> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$baseUrl/models")
            .header("Authorization", "Bearer $idToken").get().build()
        client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ApiException(response.code, serverError(payload, response.code))
            val arr = JSONObject(payload).optJSONArray("models") ?: JSONArray()
            (0 until arr.length()).map { arr.getString(it) }
        }
    }

    private fun serverError(payload: String, code: Int): String =
        runCatching { JSONObject(payload).getString("error") }.getOrNull() ?: "El servicio respondió HTTP $code."
}
