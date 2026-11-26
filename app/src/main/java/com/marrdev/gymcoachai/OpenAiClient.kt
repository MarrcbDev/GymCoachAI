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

class OpenAiClient(private val apiKey: String) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS) // los modelos de razonamiento pueden tardar
        .build()

    private val json = "application/json; charset=utf-8".toMediaType()

    /** messages: lista de (rol, texto). Devuelve el texto de la respuesta del modelo. */
    suspend fun chat(model: String, messages: List<Pair<String, String>>): String = withContext(Dispatchers.IO) {
        try {
            send(model, messages, jsonMode = true)
        } catch (e: ApiException) {
            // Algunos modelos no admiten response_format: se reintenta sin él.
            if (e.code == 400 && e.message.orEmpty().contains("response_format", ignoreCase = true)) {
                send(model, messages, jsonMode = false)
            } else throw e
        }
    }

    private fun send(model: String, messages: List<Pair<String, String>>, jsonMode: Boolean): String {
        val arr = JSONArray()
        messages.forEach { arr.put(JSONObject().put("role", it.first).put("content", it.second)) }
        val body = JSONObject().put("model", model).put("messages", arr)
        if (jsonMode) body.put("response_format", JSONObject().put("type", "json_object"))
        val request = Request.Builder().url("https://api.openai.com/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(body.toString().toRequestBody(json)).build()
        client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ApiException(response.code, errorDetail(payload) ?: "OpenAI respondió HTTP ${response.code}.")
            return JSONObject(payload).getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").optString("content", "")
        }
    }

    /** Modelos de chat disponibles para esta API Key. */
    suspend fun listModels(): List<String> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("https://api.openai.com/v1/models")
            .header("Authorization", "Bearer $apiKey").get().build()
        client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ApiException(response.code, errorDetail(payload) ?: "OpenAI respondió HTTP ${response.code}.")
            val data = JSONObject(payload).getJSONArray("data")
            val excluded = listOf(
                "audio", "realtime", "transcribe", "tts", "image", "embedding", "moderation", "whisper",
                "dall-e", "search", "instruct", "davinci", "babbage", "codex", "computer-use", "diarize", "sora",
            )
            (0 until data.length()).map { data.getJSONObject(it).getString("id") }
                .filter { id -> (id.startsWith("gpt-") || Regex("^o\\d").containsMatchIn(id)) && excluded.none { id.contains(it) } }
                .distinct().sorted()
        }
    }

    private fun errorDetail(payload: String): String? =
        runCatching { JSONObject(payload).getJSONObject("error").getString("message") }.getOrNull()
}

class ApiException(val code: Int, message: String) : Exception(message)
