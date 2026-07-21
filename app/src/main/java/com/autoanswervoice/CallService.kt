package com.autoanswervoice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.InCallService
import android.util.Log
import java.io.File

class CallService : InCallService() {

    private var mediaPlayer: MediaPlayer? = null
    private var audioManager: AudioManager? = null
    private val handler = Handler(Looper.getMainLooper())

    /** Kullanıcının kaydettiği dosya (recording.mp3).
     *  Varsa önce bu çalınır; yoksa assets/message.mp3 yedek olarak kullanılır. */
    private val recordingFile: File
        get() = File(getExternalFilesDir(null), "recording.mp3")

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.d("CallService", "Yeni arama algılandı.")

        // Arama durum değişikliklerini dinle
        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                super.onStateChanged(call, state)

                when (state) {
                    Call.STATE_RINGING -> {
                        Log.d("CallService", "Arama çalıyor, yanıtlanıyor...")
                        call.answer(0)
                    }
                    Call.STATE_ACTIVE -> {
                        Log.d("CallService", "Arama aktifleşti, ses çalma başlatılıyor...")
                        // Hat bağlantısının oturması için 1 saniye bekle
                        handler.postDelayed({ playAudio(call) }, 1000)
                    }
                    Call.STATE_DISCONNECTED -> {
                        Log.d("CallService", "Arama sonlandı.")
                        releaseAudio()
                    }
                }
            }
        })

        // Eğer servis bağlandığında arama zaten çalma durumundaysa direkt yanıtla
        if (call.state == Call.STATE_RINGING) {
            call.answer(0)
        }
    }

    private fun playAudio(call: Call) {
        try {
            audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

            // Arama modunu aktifleştir ve hoparlörü aç
            audioManager?.mode = AudioManager.MODE_IN_CALL
            audioManager?.isSpeakerphoneOn = true

            releasePlayer()

            mediaPlayer = MediaPlayer().apply {
                // Arama kanalına yönlendiren ses ayarı
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )

                val custom = recordingFile
                if (custom.exists() && custom.length() > 0) {
                    Log.d("CallService", "Özel kayıt oynatılıyor: ${custom.absolutePath}")
                    setDataSource(custom.absolutePath)
                } else {
                    Log.d("CallService", "Varsayılan message.mp3 oynatılıyor.")
                    val afd = assets.openFd("message.mp3")
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    afd.close()
                }

                prepare()
                start()

                setOnCompletionListener {
                    Log.d("CallService", "Ses oynatımı bitti, arama kapatılıyor...")
                    handler.postDelayed({
                        call.disconnect()
                        releaseAudio()
                    }, 500)
                }
            }
        } catch (e: Exception) {
            Log.e("CallService", "Ses çalınırken hata oluştu: ${e.message}", e)
            releaseAudio()
        }
    }

    private fun releaseAudio() {
        runCatching {
            audioManager?.isSpeakerphoneOn = false
            audioManager?.mode = AudioManager.MODE_NORMAL
        }
        releasePlayer()
    }

    private fun releasePlayer() {
        runCatching { mediaPlayer?.stop() }
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        releaseAudio()
    }
}

