package com.autoanswervoice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaPlayer
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import java.util.concurrent.Executors

class CallService : Service() {

    private lateinit var telephonyManager: TelephonyManager
    private var callCallback: CallStateCallback? = null
    private var mediaPlayer: MediaPlayer? = null
    private var wasRinging = false
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    inner class CallStateCallback : TelephonyCallback(), TelephonyCallback.CallStateListener {
        override fun onCallStateChanged(state: Int) {
            when (state) {
                TelephonyManager.CALL_STATE_RINGING -> {
                    wasRinging = true
                    AutoAnswerAccessibilityService.instance?.answerCall()
                }
                TelephonyManager.CALL_STATE_OFFHOOK -> {
                    if (wasRinging) {
                        wasRinging = false
                        handler.postDelayed({ playMessage() }, 1500)
                    }
                }
                TelephonyManager.CALL_STATE_IDLE -> {
                    wasRinging = false
                    releasePlayer()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        callCallback = CallStateCallback()
        telephonyManager.registerTelephonyCallback(executor, callCallback!!)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        callCallback?.let { telephonyManager.unregisterTelephonyCallback(it) }
        releasePlayer()
        executor.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun playMessage() {
        releasePlayer()
        runCatching {
            val afd = assets.openFd("message.mp3")
            mediaPlayer = MediaPlayer().apply {
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                prepare()
                start()
                setOnCompletionListener {
                    releasePlayer()
                    handler.postDelayed({
                        AutoAnswerAccessibilityService.instance?.endCall()
                    }, 500)
                }
            }
        }.onFailure { releasePlayer() }
    }

    private fun releasePlayer() {
        runCatching { mediaPlayer?.stop() }
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AutoAnswer Voice")
            .setContentText("Arama bekleniyor...")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setOngoing(true)
            .build()

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "AutoAnswer Voice", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val NOTIF_ID = 1
        private const val CHANNEL_ID = "autoanswervoice"
    }
}
