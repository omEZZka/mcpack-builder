package com.mcpackbuilder.data.agent

import android.content.Context
import com.mcpackbuilder.domain.model.ModEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URL

data class AiAgentConfig(
    val enabled: Boolean = false,
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val timeout: Int = 60,
    val autoDownload: Boolean = true,
    val autoFix: Boolean = true,
    val recommend: Boolean = true,
    val provider: String = "custom"
)

data class AiAgentResponse(
    val success: Boolean,
    val message: String?,
    val mods: List<ModEntry>?,
    val conflicts: List<String>?,
    val optimizations: List<String>?,
    val description: String?,
    val replacements: Map<String, String>?,
    val priorities: List<String>?,
    val errors: List<String>?
)

data class ConnectionTestResult(val ok: Boolean, val detail: String)

data class ChatTurn(val role: String, val content: String)

class AiAgentClient(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun completionsUrl(baseUrl: String): String {
        val base = baseUrl.trim().trimEnd('/')
        return if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
    }

    suspend fun testConnection(): ConnectionTestResult = withContext(Dispatchers.IO) {
        val config = loadConfig()
        if (config.baseUrl.isBlank()) return@withContext ConnectionTestResult(false, "Укажите Base URL")
        if (config.apiKey.isBlank() && !isLocalHost(config.baseUrl)) {
            return@withContext ConnectionTestResult(false, "Укажите API ключ")
        }
        if (config.model.isBlank()) return@withContext ConnectionTestResult(false, "Укажите модель")
        try {
            val body = buildJsonObject {
                put("model", config.model)
                put("messages", JsonArray(listOf(buildJsonObject {
                    put("role", "user")
                    put("content", "ping")
                })))
                put("max_tokens", 5)
            }.toString()
            val (code, text) = post(config, body)
            when {
                code in 200..299 -> ConnectionTestResult(true, "Подключено · HTTP $code")
                code == 401 -> ConnectionTestResult(false, "Неверный API ключ (401)")
                code == 404 -> ConnectionTestResult(false, "Модель или URL не найдены (404)")
                code == 429 -> ConnectionTestResult(false, "Лимит запросов (429)")
                else -> ConnectionTestResult(false, "HTTP $code: ${text.take(120)}")
            }
        } catch (e: Exception) {
            ConnectionTestResult(false, "Ошибка сети: ${e.message ?: "неизвестно"}")
        }
    }

    private fun isLocalHost(url: String) =
        url.contains("localhost") || url.contains("127.0.0.1") || url.contains("10.0.2.2") || url.contains("192.168.")

    suspend fun chat(userMessage: String, history: List<ChatTurn>, context: String? = null): AiAgentResponse = withContext(Dispatchers.IO) {
        val config = loadConfig()
        if (config.baseUrl.isBlank() || config.model.isBlank()) {
            return@withContext fail("AI-агент не настроен. Укажите Base URL и модель в настройках.")
        }
        if (config.apiKey.isBlank() && !isLocalHost(config.baseUrl)) {
            return@withContext fail("Не указан API ключ. Добавьте его в настройках AI-агента.")
        }
        try {
            val messages = JsonArray(buildList {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", if (context.isNullOrBlank()) SYSTEM_PROMPT else "$SYSTEM_PROMPT\n\nТекущий контекст:\n$context")
                })
                history.takeLast(12).forEach { turn ->
                    add(buildJsonObject {
                        put("role", turn.role)
                        put("content", turn.content)
                    })
                }
                add(buildJsonObject {
                    put("role", "user")
                    put("content", userMessage)
                })
            })
            val body = buildJsonObject {
                put("model", config.model)
                put("messages", messages)
                put("temperature", 0.6)
            }.toString()
            val (code, text) = post(config, body)
            if (code !in 200..299) return@withContext fail("HTTP $code: ${text.take(160)}")
            val content = json.parseToJsonElement(text).jsonObject["choices"]?.jsonArray
                ?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
                ?: return@withContext fail("Пустой ответ модели")
            AiAgentResponse(true, content.trim(), null, null, null, null, null, null, null)
        } catch (e: Exception) {
            fail("Ошибка запроса: ${e.message ?: "неизвестно"}")
        }
    }

    suspend fun analyzeRequest(userQuery: String, selectedMods: List<ModEntry>): AiAgentResponse =
        chat(userQuery, emptyList())

    private fun fail(msg: String) = AiAgentResponse(false, msg, null, null, null, null, null, null, null)

    private fun post(config: AiAgentConfig, body: String): Pair<Int, String> {
        val url = URL(completionsUrl(config.baseUrl))
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = config.timeout * 1000
        conn.readTimeout = config.timeout * 1000
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Accept", "application/json")
        if (config.apiKey.isNotBlank()) {
            conn.setRequestProperty("Authorization", "Bearer ${config.apiKey}")
        }
        conn.doOutput = true
        conn.outputStream.use { it.write(body.toByteArray()) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.use { it.bufferedReader().readText() } ?: ""
        return code to text
    }

    fun saveConfig(config: AiAgentConfig) {
        val prefs = context.getSharedPreferences("ai_agent", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean("enabled", config.enabled)
            putString("base_url", config.baseUrl)
            putString("api_key", config.apiKey)
            putString("model", config.model)
            putInt("timeout", config.timeout)
            putBoolean("auto_download", config.autoDownload)
            putBoolean("auto_fix", config.autoFix)
            putBoolean("recommend", config.recommend)
            putString("provider", config.provider)
            apply()
        }
    }

    fun loadConfig(): AiAgentConfig {
        val prefs = context.getSharedPreferences("ai_agent", Context.MODE_PRIVATE)
        return AiAgentConfig(
            enabled = prefs.getBoolean("enabled", false),
            baseUrl = prefs.getString("base_url", "") ?: "",
            apiKey = prefs.getString("api_key", "") ?: "",
            model = prefs.getString("model", "") ?: "",
            timeout = prefs.getInt("timeout", 60),
            autoDownload = prefs.getBoolean("auto_download", true),
            autoFix = prefs.getBoolean("auto_fix", true),
            recommend = prefs.getBoolean("recommend", true),
            provider = prefs.getString("provider", "custom") ?: "custom"
        )
    }

    companion object {
        const val SYSTEM_PROMPT =
            "Ты — умный AI-ассистент приложения MCPack Builder для Minecraft Bedrock. " +
            "Помогаешь подбирать аддоны, текстуры, шейдеры и карты, объясняешь как собрать сборку (BP+RP), " +
            "как разрешать конфликты (перезапись файлов, реестры, Script API) и оптимизировать паки. " +
            "Отвечай структурировано и кратко на русском: сначала суть, затем шаги списком. " +
            "Если пользователь просит подобрать моды — перечисли 3-5 конкретных типов аддонов и совместимость."
    }
}
