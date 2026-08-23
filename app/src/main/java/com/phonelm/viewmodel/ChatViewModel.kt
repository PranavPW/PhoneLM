package com.phonelm.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phonelm.core.LlamaEngine
import com.phonelm.rag.VectorStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class ChatMessage(
    val role: String, // "user", "model", "system"
    val content: String,
    val thinkingProcess: String? = null // For CoT models
)

data class UiState(
    val messages: List<ChatMessage> = emptyList(),
    val isModelLoaded: Boolean = false,
    val isLoading: Boolean = false,
    val currentModelName: String? = null,
    val error: String? = null
)

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.phonelm.data.ObjectBox
import com.phonelm.data.VectorStore
import com.phonelm.rag.EmbeddingGenerator

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val embeddingGenerator by lazy { EmbeddingGenerator(application) }
    private val vectorStore by lazy { 
        ObjectBox.init(application)
        VectorStore(embeddingGenerator) 
    }

    fun loadModel(path: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val success = LlamaEngine.loadModelSafe(path)
            if (success) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false, 
                    isModelLoaded = true, 
                    currentModelName = File(path).name,
                    error = null
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false, 
                    error = "Failed to load model"
                )
            }
        }
    }

    fun addDocument(uri: android.net.Uri, context: android.content.Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                // Ensure ObjectBox is init
                ObjectBox.init(getApplication())
                
                val processor = com.phonelm.rag.DocumentProcessor(context)
                val resultDetails = processor.processPdf(uri)
                // DocumentProcessor now handles chunking and storing in VectorStore internally
                
                _uiState.value = _uiState.value.copy(isLoading = false)
                // Notify user system message
                 val currentMessages = _uiState.value.messages.toMutableList()
                 currentMessages.add(ChatMessage("system", "Document processed: $resultDetails"))
                 _uiState.value = _uiState.value.copy(messages = currentMessages)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun unloadModel() {
        LlamaEngine.unloadModel()
        _uiState.value = _uiState.value.copy(isModelLoaded = false, currentModelName = null)
    }

    fun sendMessage(text: String) {
        val currentMessages = _uiState.value.messages.toMutableList()
        currentMessages.add(ChatMessage("user", text))
        _uiState.value = _uiState.value.copy(messages = currentMessages, isLoading = true)

        viewModelScope.launch {
            try {
                // RAG Retrieval
                val relevantDocs = vectorStore.searchSimilar(text, 3)
                val contextBlock = if (relevantDocs.isNotEmpty()) {
                    relevantDocs.joinToString("\n\n") { "Title: ${it.fileName}\nContent: ${it.text}" }
                } else {
                    ""
                }
                
                val finalPrompt = if (contextBlock.isNotBlank()) {
                     """
                     Use the following context to answer the user's question.
                     Context:
                     $contextBlock
                     
                     Question: $text
                     """.trimIndent()
                } else {
                    text
                }

                // Generate
                val response = LlamaEngine.generateCompletion(finalPrompt) // In real app, prompt template applied here
                
                // Parse thinking <think>...</think>
                var finalContent = response
                var thinking: String? = null
                
                // Simple regex based parsing for DeepSeek R1 style
                val thinkRegex = Regex("<think>(.*?)</think>", RegexOption.DOT_MATCHES_ALL)
                val match = thinkRegex.find(response)
                if (match != null) {
                    thinking = match.groupValues[1].trim()
                    finalContent = response.replace(match.value, "").trim()
                }

                // Match JSON blocks e.g. ```json {"action": "click", "text": "foo"} ```
                // or just raw JSON lines { "action": ... }
                val jsonRegex = Regex("\\{.*\"action\":\\s*\"([a-zA-Z]+)\".*\"text\":\\s*\"(.*?)\".*\\}")
                val jsonMatch = jsonRegex.find(finalContent)
                
                if (jsonMatch != null) {
                    val action = jsonMatch.groupValues[1]
                    val targetText = jsonMatch.groupValues[2]
                    // Trigger agent
                    com.phonelm.service.AgentBridge.triggerAction(action, targetText)
                    
                    // Add a system update message
                    currentMessages.add(ChatMessage("system", "Agent executing: $action on '$targetText'"))
                }

                currentMessages.add(ChatMessage("model", finalContent, thinking))
                _uiState.value = _uiState.value.copy(
                    messages = currentMessages, 
                    isLoading = false
                )
            } catch (e: Exception) {
                 _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }
}
