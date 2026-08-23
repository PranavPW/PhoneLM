package com.phonelm.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class PhoneLMAccessibilityService : AccessibilityService() {
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("PhoneLMService", "Service Connected")
        serviceScope.launch {
            AgentBridge.actions.collect { action ->
                performAgentAction(action)
            }
        }
    }

    private fun performAgentAction(action: AgentAction) {
        val root = rootInActiveWindow ?: return
        Log.d("PhoneLMService", "Performing action: ${action.action} on ${action.text}")

        if (action.action.equals("click", ignoreCase = true) && action.text != null) {
            val nodes = root.findAccessibilityNodeInfosByText(action.text)
            if (nodes != null && nodes.isNotEmpty()) {
                // Try to click the first clickable parent or the node itself
                for (node in nodes) {
                    if (clickNode(node)) break
                }
            } else {
                Log.d("PhoneLMService", "Node not found with text: ${action.text}")
            }
        } else if (action.action.equals("scroll", ignoreCase = true)) {
             // Simple scroll forward on first scrollable
             findScrollable(root)?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        }
    }

    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        val parent = node.parent
        if (parent != null) {
            return clickNode(parent)
        }
        return false
    }
    
    private fun findScrollable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
             val child = node.getChild(i) ?: continue
             val found = findScrollable(child)
             if (found != null) return found
        }
        return null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Monitor events if needed for "Seeing" the screen (dumping node tree)
    }

    override fun onInterrupt() {
        Log.d("PhoneLMService", "Service Interrupted")
    }
}
