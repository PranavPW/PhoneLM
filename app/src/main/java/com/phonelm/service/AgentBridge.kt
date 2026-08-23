package com.phonelm.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class AgentAction(val action: String, val text: String?)

object AgentBridge {
    private val _actions = MutableSharedFlow<AgentAction>(extraBufferCapacity = 1)
    val actions = _actions.asSharedFlow()

    fun triggerAction(action: String, text: String?) {
        _actions.tryEmit(AgentAction(action, text))
    }
}
