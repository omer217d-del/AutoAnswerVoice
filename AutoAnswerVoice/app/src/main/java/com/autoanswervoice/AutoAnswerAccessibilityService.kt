package com.autoanswervoice

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AutoAnswerAccessibilityService : AccessibilityService() {

    companion object {
        var instance: AutoAnswerAccessibilityService? = null

        private val ANSWER_KEYWORDS = listOf(
            "answer", "yanıtla", "kabul", "accept", "cevap",
            "answer_action", "call_accept", "btn_answer",
            "incoming_call_accept", "action_accept_call",
            "ans_action_bg", "ans_call"
        )

        private val END_KEYWORDS = listOf(
            "end", "bitir", "kapat", "decline", "reject", "hang",
            "hangup", "end_call", "call_end", "btn_hangup", "btn_end",
            "call_decline", "decline_action", "reject_action",
            "neg_action_bg", "reject_call", "action_reject_call"
        )
    }

    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    fun answerCall() {
        if (!tryClickInAllWindows(ANSWER_KEYWORDS)) {
            sendHeadsetHook()
        }
    }

    fun endCall() {
        if (!tryClickInAllWindows(END_KEYWORDS)) {
            sendHeadsetHook()
        }
    }

    private fun sendHeadsetHook() {
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        val t = SystemClock.uptimeMillis()
        am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HEADSETHOOK, 0))
        am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HEADSETHOOK, 0))
    }

    private fun tryClickInAllWindows(keywords: List<String>): Boolean {
        val wins = windows
        if (!wins.isNullOrEmpty()) {
            for (window in wins) {
                val root = window.root ?: continue
                if (clickNode(root, keywords)) return true
            }
        }
        val fallback = rootInActiveWindow ?: return false
        return clickNode(fallback, keywords)
    }

    private fun clickNode(node: AccessibilityNodeInfo, keywords: List<String>): Boolean {
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        val text = node.text?.toString()?.lowercase() ?: ""

        if (keywords.any { k -> desc.contains(k) || viewId.contains(k) || text.contains(k) }) {
            val target = if (node.isClickable) node else {
                var p = node.parent
                while (p != null && !p.isClickable) p = p.parent
                p
            }
            if (target != null && target.isClickable) {
                target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (clickNode(child, keywords)) return true
        }
        return false
    }
}
