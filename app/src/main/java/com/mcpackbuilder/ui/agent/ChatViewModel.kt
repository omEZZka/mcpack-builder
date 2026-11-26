package com.mcpackbuilder.ui.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mcpackbuilder.AppContainer
import com.mcpackbuilder.data.agent.AiAgentClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isTyping: Boolean = false,
    val agentEnabled: Boolean = false,
    val error: String? = null
)

class ChatViewModel(private val container: AppContainer) : ViewModel() {
    private val aiClient = AiAgentClient(container.app)
    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()
    
    init {
        _state.update { it.copy(agentEnabled = aiClient.loadConfig().enabled) }
        // Welcome message
        if (_state.value.agentEnabled) {
            addMessage("Привет! Я AI-ассистент MCPack Builder. Чем могу помочь?", false)
        }
    }
    
    fun sendMessage(text: String) {
        addMessage(text, true)
        _state.update { it.copy(isTyping = true) }
        
        viewModelScope.launch {
            try {
                val config = aiClient.loadConfig()
                // Check if API is properly configured
                val apiConfigured = config.enabled && config.baseUrl.isNotEmpty() && config.apiKey.isNotEmpty()
                
                val response = if (apiConfigured) {
                    // Try real API call
                    try {
                        val result = aiClient.analyzeRequest(text, emptyList())
                        if (result.success && result.message != null) {
                            result.message
                        } else if (result.mods != null && result.mods.isNotEmpty()) {
                            "Найдено модов: ${result.mods.size}. Перейдите в каталог для просмотра."
                        } else if (result.conflicts != null && result.conflicts.isNotEmpty()) {
                            "Обнаружено конфликтов: ${result.conflicts.size}"
                        } else if (result.description != null) {
                            result.description
                        } else {
                            // Fallback to smart responses based on query
                            getSmartResponse(text)
                        }
                    } catch (e: Exception) {
                        // API call failed, use smart responses
                        getSmartResponse(text)
                    }
                } else {
                    // API not configured, use smart responses
                    getSmartResponse(text)
                }
                
                kotlinx.coroutines.delay(1000) // Simulate typing
                addMessage(response, false)
            } catch (e: Exception) {
                addMessage("Ошибка: ${e.message}", false)
            } finally {
                _state.update { it.copy(isTyping = false) }
            }
        }
    }
    
    private fun getSmartResponse(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.contains("привет") || lower.contains("здравствуй") -> "Привет! Я AI-ассистент MCPack Builder. Могу помочь подобрать моды, исправить конфликты или оптимизировать сборку."
            lower.contains("пока") || lower.contains("до свидания") -> "Всего доброго! Обращайтесь ещё."
            lower.contains("сборк") || lower.contains("собрать") || lower.contains("создать") -> "Для создания сборки: 1) Добавьте моды в каталоге 2) Перейдите во вкладку 'Сборка' 3) Нажмите 'Скачать все моды' 4) Нажмите 'Собрать'"
            lower.contains("конфликт") || lower.contains("ошибк") -> "Конфликты модов возникают при одинаковых именах файлов. AI может автоматически разрешить их. Включите опцию в настройках AI-агента."
            lower.contains("скач") || lower.contains("загруз") -> "В разделе 'Сборка' есть кнопка 'Скачать все моды'. Она автоматически загрузит все выбранные аддоны."
            lower.contains("тег") || lower.contains("фильтр") -> "Используйте фильтры в каталоге: категории, теги, сортировка. Тег 'Все' показывает весь список модов."
            lower.contains("мод") && (lower.contains("найди") || lower.contains("подбери") || lower.contains("искать")) -> "Перейдите в каталог, выберите категорию и тег. Например: 'Моды' + 'Хоррор' для хоррор-модов."
            lower.contains("спасиб") -> "Пожалуйста! Всегда рад помочь."
            lower.contains("как") && lower.contains("дела") -> "У меня всё отлично! Готов помогать вам с модами для Minecraft."
            else -> "Понял: '$text'. Для полноценной работы настройте API ключ AI-агента в настройках приложения."
        }
    }

    private fun addMessage(text: String, isUser: Boolean) {
        _state.update { 
            it.copy(messages = it.messages + ChatMessage(text, isUser))
        }
    }
    
    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { ChatViewModel(container) }
        }
    }
}
