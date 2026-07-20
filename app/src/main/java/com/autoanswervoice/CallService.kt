package com.autoanswervoice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telecom.TelecomManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import java.io.File
import java.util.concurrent.Executors

class CallService : Service() {

    private lateinit var telephonyManager: TelephonyManager
    private lateinit var audioManager: AudioManager
    private var callCallback: CallStateCallback? = null
    private var mediaPlayer: MediaPlayer? = null
    private var wasRinging = false
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    /** Kullanıcının kaydettiği dosya (recording.mp3).
     *  Varsa önce bu çalınır; yoksa assets/message.mp3 yedek olarak kullanılır. */
    private val recordingFile: File
        get() = File(getExternalFilesDir(null), "recording.mp3")

    inner class CallStateCallback : TelephonyCallback(), TelephonyCallback.CallStateListener {
        override fun onCallStateChanged(state: Int) {
            when (state) {
                TelephonyManager.CALL_STATE_RINGING -> {
                    wasRinging = true
                    // 1. Önce TelecomManager ile yanıtla (en güvenilir yöntem)
                    // 2. Başarısız olursa Accessibility Service fallback'i devreye girer
                    answerRingingCall()
                }
                TelephonyManager.CALL_STATE_OFFHOOK -> {
                    if (wasRinging) {
                        wasRinging = false
                        // Arama açıldı: kısa bir gecikme sonra mesajı çal
                        handler.postDelayed({ playMessage() }, 1000)
                    }
                }
                TelephonyManager.CALL_STATE_IDLE -> {
                    wasRinging = false
                    restoreAudio()
                    releasePlayer()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(
            NOTIF_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        callCallback = CallStateCallback()
        telephonyManager.registerTelephonyCallback(executor, callCallback!!)
    }

    override fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        callCallback?.let { telephonyManager.unregisterTelephonyCallback(it) }
        restoreAudio()
        releasePlayer()
        executor.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: android.content.Intent?): IBinder? = null

    // ─── Aramayı Yanıtlama ───────────────────────────────────

    @Suppress("DEPRECATION")
    private fun answerRingingCall() {
        // Yöntem 1: TelecomManager (API 26+, ANSWER_PHONE_CALLS izni gerekli)
        runCatching {
            val telecom = getSystemService(TELECOM_SERVICE) as TelecomManager
            telecom.acceptRingingCall()
            return
        }
        // Yöntem 2: Accessibility Service aracılığıyla UI düğmesine tıkla
        AutoAnswerAccessibilityService.instance?.answerCall()
    }

    // ─── Ses Çalma ───────────────────────────────────────────

    private fun playMessage() {
        releasePlayer()

        // Mikrofonu sessize al — ortam sesi karşı tarafa gitmesin
        audioManager.isMicrophoneMute = true

        // Ses modunu arama moduna al
        audioManager.mode = AudioManager.MODE_IN_CALL

        val custom = recordingFile
        if (custom.exists() && custom.length() > 0) {
            playFile(custom)
        } else {
            playAsset()
        }
    }

    /**
     * Ses özelliklerini telefon araması kanalına yönlendirir.
     * USAGE_VOICE_COMMUNICATION ile MediaPlayer'ın sesi hem kulaklıktan
     * duyulur hem de destekleyen cihazlarda uplink'e (karşı tarafa) iletilir.
     */
    private fun buildAudioAttributes(): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

    /** Dosya sistemindeki recording.mp3'ü çal */
    private fun playFile(file: File) {
        runCatching {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(buildAudioAttributes())
                setDataSource(file.absolutePath)
                prepare()
                start()
                setOnCompletionListener { onPlaybackComplete() }
            }
        }.onFailure {
            // Dosya bozuksa assets'e düş
            playAsset()
        }
    }

    /** assets/message.mp3'ü çal (yedek) */
    private fun playAsset() {
        runCatching {
            val afd = assets.openFd("message.mp3")
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(buildAudioAttributes())
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                prepare()
                start()
                setOnCompletionListener { onPlaybackComplete() }
            }
        }.onFailure {
            restoreAudio()
            releasePlayer()
        }
    }

    private fun onPlaybackComplete() {
        restoreAudio()
        releasePlayer()
        handler.postDelayed({
            AutoAnswerAccessibilityService.instance?.endCall()
        }, 500)
    }

    private fun restoreAudio() {
        runCatching {
            audioManager.isMicrophoneMute = false
            audioManager.mode = AudioManager.MODE_NORMAL
        }
    }

    private fun releasePlayer() {
        runCatching { mediaPlayer?.stop() }
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
    }

    // ─── Bildirim ────────────────────────────────────────────

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AutoAnswer Voice")
            .setContentText("Arama bekleniyor...")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setOngoing(true)
            .build()

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "AutoAnswer Voice",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val NOTIF_ID   = 1
        private const val CHANNEL_ID = "autoanswervoice"
    }
}
