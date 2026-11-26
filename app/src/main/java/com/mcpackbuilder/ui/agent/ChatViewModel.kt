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
                // Simulate AI response (replace with actual API call)
                val config = aiClient.loadConfig()
                val response = if (config.enabled && config.baseUrl.isNotEmpty() && config.apiKey.isNotEmpty()) {
                    // Real API call
                    try {
                        val result = aiClient.analyzeRequest(text, emptyList())
                        if (result.success) {
                            result.message ?: "Запрос обработан успешно"
                        } else {
                            result.message ?: "Ошибка обработки запроса"
                        }
                    } catch (e: Exception) {
                        "Ошибка API: ${e.message}"
                    }
                } else {
                    when {
                        text.contains("привет", ignoreCase = true) -> "Здравствуйте! Готов помочь с подбором модов."
                        text.contains("сборк", ignoreCase = true) -> "Для создания сборки выберите моды в каталоге и перейдите на вкладку Сборка."
                        text.contains("конфликт", ignoreCase = true) -> "Конфликты модов можно исправить в настройках AI-агента."
                        text.contains("скач", ignoreCase = true) -> "Нажмите кнопку 'Скачать все моды' в разделе Сборка."
                        else -> "Настройте API ключ в настройках для полноценной работы AI-агента."
                    }
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
