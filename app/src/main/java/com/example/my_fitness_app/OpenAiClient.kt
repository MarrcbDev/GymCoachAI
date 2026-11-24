package com.example.my_fitness_app

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
        .connectTimeout(25, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()

    suspend fun ask(question: String, profileContext: String): String = withContext(Dispatchers.IO) {
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content",
                "Eres GymCoachAI, un asistente de entrenamiento responsable. Personaliza consejos usando este perfil: $profileContext. " +
                "Responde en español, de forma clara y breve. No diagnostiques enfermedades ni sustituyas profesionales de salud."))
            .put(JSONObject().put("role", "user").put("content", question))
        val body = JSONObject().put("model", "gpt-4o-mini").put("messages", messages).put("temperature", 0.7)
            .toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url("https://api.openai.com/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey").post(body).build()
        client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = runCatching { JSONObject(payload).getJSONObject("error").getString("message") }.getOrNull()
                error(detail ?: "OpenAI respondió HTTP ${response.code}.")
            }
            JSONObject(payload).getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content")
        }
    }
}
