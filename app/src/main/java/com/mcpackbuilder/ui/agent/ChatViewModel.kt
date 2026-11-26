package com.mcpackbuilder.ui.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mcpackbuilder.AppContainer
import com.mcpackbuilder.data.agent.AiAgentClient
import com.mcpackbuilder.data.agent.ChatTurn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatMessage(val text: String, val isUser: Boolean, val timestamp: Long = System.currentTimeMillis())

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
        val cfg = aiClient.loadConfig()
        val ready = cfg.enabled && cfg.baseUrl.isNotBlank() && cfg.model.isNotBlank()
        _state.update { it.copy(agentEnabled = ready) }
        addMessage(
            if (ready) "Привет! Я AI-ассистент MCPack Builder. Спросите про моды, сборку или конфликты."
            else "AI-агент не настроен. Откройте Настройки → AI Агент, укажите Base URL, модель и ключ.",
            false
        )
    }

    fun refreshStatus() {
        val cfg = aiClient.loadConfig()
        _state.update { it.copy(agentEnabled = cfg.enabled && cfg.baseUrl.isNotBlank() && cfg.model.isNotBlank()) }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        addMessage(text, true)
        _state.update { it.copy(isTyping = true, error = null) }

        viewModelScope.launch {
            try {
                val history = _state.value.messages
                    .dropLast(1)
                    .map { ChatTurn(if (it.isUser) "user" else "assistant", it.text) }
                val result = aiClient.chat(text, history)
                addMessage(result.message ?: "Не удалось получить ответ", false)
                if (!result.success) {
                    _state.update { it.copy(error = result.message) }
                }
            } catch (e: Exception) {
                addMessage("Ошибка: ${e.message ?: "неизвестно"}", false)
            } finally {
                _state.update { it.copy(isTyping = false) }
            }
        }
    }

    fun clearChat() {
        _state.update { it.copy(messages = emptyList()) }
        addMessage("История очищена. Чем помочь?", false)
    }

    private fun addMessage(text: String, isUser: Boolean) {
        _state.update { it.copy(messages = it.messages + ChatMessage(text, isUser)) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { ChatViewModel(container) }
        }
    }
}
