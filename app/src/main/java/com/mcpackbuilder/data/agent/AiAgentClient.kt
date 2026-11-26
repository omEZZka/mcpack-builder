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
    val model: String = "default",
    val timeout: Int = 60
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

class AiAgentClient(private val context: Context) {
    
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    
    suspend fun analyzeRequest(userQuery: String, selectedMods: List<ModEntry>): AiAgentResponse = withContext(Dispatchers.IO) {
        val config = loadConfig()
        if (!config.enabled || config.baseUrl.isBlank()) {
            return@withContext AiAgentResponse(
                success = false,
                message = "AI-агент не настроен. Добавьте API ключ в настройках.",
                mods = null, conflicts = null, optimizations = null,
                description = null, replacements = null, priorities = null, errors = null
            )
        }
        
        try {
            val requestBody = buildJsonRequest(userQuery, selectedMods)
            val response = sendRequest(config, requestBody)
            parseResponse(response)
        } catch (e: Exception) {
            AiAgentResponse(
                success = false,
                message = "Ошибка соединения с AI-агентом: ${e.message}",
                mods = null, conflicts = null, optimizations = null,
                description = null, replacements = null, priorities = null, errors = null
            )
        }
    }
    
    suspend fun optimizeBuild(selectedMods: List<ModEntry>, buildErrors: List<String>): AiAgentResponse = withContext(Dispatchers.IO) {
        val config = loadConfig()
        if (!config.enabled || config.baseUrl.isBlank()) {
            return@withContext AiAgentResponse(
                success = false,
                message = "AI-агент не настроен",
                mods = null, conflicts = null, optimizations = null,
                description = null, replacements = null, priorities = null, errors = null
            )
        }
        
        try {
            val requestBody = buildOptimizeRequest(selectedMods, buildErrors)
            val response = sendRequest(config, requestBody)
            parseResponse(response)
        } catch (e: Exception) {
            AiAgentResponse(
                success = false,
                message = "Ошибка оптимизации: ${e.message}",
                mods = null, conflicts = null, optimizations = null,
                description = null, replacements = null, priorities = null, errors = null
            )
        }
    }
    
    private fun buildJsonRequest(query: String, mods: List<ModEntry>): String {
        val modsArray = JsonArray(mods.map { m ->
            buildJsonObject {
                put("title", m.title)
                put("category", m.category ?: "")
                put("page_url", m.pageUrl)
            }
        })
        return buildJsonObject {
            put("action", "analyze_request")
            put("query", query)
            put("selected_mods", modsArray)
        }.toString()
    }
    
    private fun buildOptimizeRequest(mods: List<ModEntry>, errors: List<String>): String {
        val modsArray = JsonArray(mods.map { m ->
            buildJsonObject {
                put("title", m.title)
                put("file_path", m.filePath ?: "")
            }
        })
        val errorsArray = JsonArray(errors.map { JsonPrimitive(it) })
        return buildJsonObject {
            put("action", "optimize_build")
            put("mods", modsArray)
            put("errors", errorsArray)
        }.toString()
    }
    
    private fun sendRequest(config: AiAgentConfig, requestBody: String): String {
        val url = URL("${config.baseUrl}/api/v1/agent")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = config.timeout * 1000
        conn.readTimeout = config.timeout * 1000
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer ${config.apiKey}")
        conn.doOutput = true
        conn.outputStream.use { it.write(requestBody.toByteArray()) }
        
        val responseCode = conn.responseCode
        val inputStream = if (responseCode == 200) conn.inputStream else conn.errorStream
        return inputStream.use { it.bufferedReader().readText() }
    }
    
    private fun parseResponse(jsonStr: String): AiAgentResponse {
        val element = json.parseToJsonElement(jsonStr)
        val jsonObj = element.jsonObject
        return AiAgentResponse(
            success = jsonObj["success"]?.jsonPrimitive?.boolean ?: false,
            message = jsonObj["message"]?.jsonPrimitive?.contentOrNull,
            mods = jsonObj["mods"]?.jsonArray?.mapNotNull { el ->
                val obj = el.jsonObject
                val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                ModEntry(
                    id = obj["id"]?.jsonPrimitive?.content ?: title,
                    title = title,
                    pageUrl = obj["page_url"]?.jsonPrimitive?.content ?: "",
                    category = obj["category"]?.jsonPrimitive?.contentOrNull
                )
            },
            conflicts = jsonObj["conflicts"]?.jsonArray?.map { it.jsonPrimitive.content },
            optimizations = jsonObj["optimizations"]?.jsonArray?.map { it.jsonPrimitive.content },
            description = jsonObj["description"]?.jsonPrimitive?.contentOrNull,
            replacements = jsonObj["replacements"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content },
            priorities = jsonObj["priorities"]?.jsonArray?.map { it.jsonPrimitive.content },
            errors = jsonObj["errors"]?.jsonArray?.map { it.jsonPrimitive.content }
        )
    }
    
    fun saveConfig(config: AiAgentConfig) {
        val prefs = context.getSharedPreferences("ai_agent", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean("enabled", config.enabled)
            putString("base_url", config.baseUrl)
            putString("api_key", config.apiKey)
            putString("model", config.model)
            putInt("timeout", config.timeout)
            apply()
        }
    }
    
    fun loadConfig(): AiAgentConfig {
        val prefs = context.getSharedPreferences("ai_agent", Context.MODE_PRIVATE)
        return AiAgentConfig(
            enabled = prefs.getBoolean("enabled", false),
            baseUrl = prefs.getString("base_url", "") ?: "",
            apiKey = prefs.getString("api_key", "") ?: "",
            model = prefs.getString("model", "default") ?: "default",
            timeout = prefs.getInt("timeout", 60)
        )
    }
}
