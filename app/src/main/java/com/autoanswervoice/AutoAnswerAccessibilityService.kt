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
            // Genel İngilizce
            "answer", "accept", "pick up",
            // Türkçe
            "yanıtla", "kabul", "cevap", "aç",
            // Yaygın view ID parçaları (üretici bağımsız)
            "answer_action", "call_accept", "btn_answer",
            "incoming_call_accept", "action_accept_call",
            "ans_action_bg", "ans_call", "accept_call",
            "floating_accept_btn", "btnAnswer",
            // Samsung, Xiaomi, Huawei, OPPO, vb.
            "key_answer", "endcall",  "ic_answer",
            "call_button_answer", "answer_btn"
        )

        private val END_KEYWORDS = listOf(
            // Genel İngilizce
            "end", "decline", "reject", "hang", "hangup",
            // Türkçe
            "bitir", "kapat", "reddet",
            // Yaygın view ID parçaları
            "end_call", "call_end", "btn_hangup", "btn_end",
            "call_decline", "decline_action", "reject_action",
            "neg_action_bg", "reject_call", "action_reject_call",
            "floating_reject_btn", "btnDecline",
            // Samsung, Xiaomi, Huawei, OPPO, vb.
            "key_endcall", "ic_decline",
            "call_button_end", "end_btn"
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
        // Önce tüm pencerelerde UI düğmesini bulmayı dene
        if (!tryClickInAllWindows(ANSWER_KEYWORDS)) {
            // Bulamazsa HEADSETHOOK tuşu simüle et (Bluetooth kulaklık bas-yanıtla)
            sendHeadsetHook()
        }
    }

    fun endCall() {
        // Önce tüm pencerelerde UI düğmesini bulmayı dene
        if (!tryClickInAllWindows(END_KEYWORDS)) {
            // Bulamazsa global BACK action'ı dene
            if (!performGlobalAction(GLOBAL_ACTION_BACK)) {
                sendHeadsetHook()
            }
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
        val desc   = node.contentDescription?.toString()?.lowercase() ?: ""
        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        val text   = node.text?.toString()?.lowercase() ?: ""
        val cls    = node.className?.toString()?.lowercase() ?: ""

        if (keywords.any { k -> desc.contains(k) || viewId.contains(k) || text.contains(k) }) {
            val target = findClickable(node)
            if (target != null) {
                target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }
        }

        // ImageButton / ImageView olan ve içeriği boş düğümleri de dene
        if ((cls.contains("imagebutton") || cls.contains("imageview")) &&
            node.isClickable && desc.isEmpty() && text.isEmpty()
        ) {
            // Bu tür düğmeler arama ekranında yanıtla/bitir olabilir;
            // sadece keywords listesiyle eşleşmeyenleri atla
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (clickNode(child, keywords)) return true
        }
        return false
    }

    /** Düğümden veya ata düğümlerden tıklanabilir olanı bul */
    private fun findClickable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isClickable) return node
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable) return parent
            parent = parent.parent
        }
        return null
    }
}
