package com.autoanswervoice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.autoanswervoice.databinding.ActivityMainBinding
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var serviceRunning = false

    // Kayıt
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private val recordingFile: File get() = File(getExternalFilesDir(null), "recording.mp3")
    private val tickHandler = Handler(Looper.getMainLooper())
    private var recordSeconds = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnToggle.setOnClickListener {
            if (serviceRunning) stopCallService() else startCallService()
        }

        binding.btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.btnRecord.setOnClickListener {
            if (isRecording) stopRecording() else startRecording()
        }

        askPermissions()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isRecording) {
            runCatching { mediaRecorder?.stop() }
            runCatching { mediaRecorder?.release() }
            mediaRecorder = null
            isRecording = false
            tickHandler.removeCallbacksAndMessages(null)
        }
    }

    // ─── Servis ──────────────────────────────────────────────

    private fun startCallService() {
        ContextCompat.startForegroundService(this, Intent(this, CallService::class.java))
        serviceRunning = true
        updateUI()
    }

    private fun stopCallService() {
        stopService(Intent(this, CallService::class.java))
        serviceRunning = false
        updateUI()
    }

    // ─── Ses kaydı ───────────────────────────────────────────

    private fun startRecording() {
        val audioPerm = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        if (audioPerm != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQ_RECORD)
            return
        }

        runCatching {
            @Suppress("DEPRECATION")
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                MediaRecorder(this)
            else
                MediaRecorder()

            mediaRecorder!!.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(64_000)
                setOutputFile(recordingFile.absolutePath)
                prepare()
                start()
            }

            isRecording = true
            recordSeconds = 0
            startTick()
            binding.btnRecord.text = "⏹ Kaydı Durdur"
            binding.btnRecord.backgroundTintList = ColorStateList.valueOf(0xFF6D1010.toInt())
            binding.tvRecordStatus.text = "🔴 Kaydediliyor..."
        }.onFailure { e ->
            binding.tvRecordStatus.text = "Kayıt başlatılamadı: ${e.message}"
        }
    }

    private fun stopRecording() {
        tickHandler.removeCallbacksAndMessages(null)
        runCatching { mediaRecorder?.stop() }
        runCatching { mediaRecorder?.release() }
        mediaRecorder = null
        isRecording = false

        binding.btnRecord.text = "🎙 Ses Kaydı Yap"
        binding.btnRecord.backgroundTintList = ColorStateList.valueOf(0xFFB71C1C.toInt())

        binding.tvRecordStatus.text = if (recordingFile.exists() && recordingFile.length() > 0) {
            "✅ Kaydedildi (${recordSeconds}s) — gelen aramada bu kayıt çalınacak"
        } else {
            "❌ Kayıt dosyası oluşturulamadı"
        }
        updateUI()
    }

    private fun startTick() {
        tickHandler.postDelayed(object : Runnable {
            override fun run() {
                if (!isRecording) return
                recordSeconds++
                binding.tvRecordStatus.text = "🔴 Kaydediliyor... ${recordSeconds}s"
                tickHandler.postDelayed(this, 1000)
            }
        }, 1000)
    }

    // ─── UI ──────────────────────────────────────────────────

    private fun updateUI() {
        val accessOn = AutoAnswerAccessibilityService.instance != null
        val phonePerm = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        binding.tvAccessibility.text = "Erişilebilirlik: ${if (accessOn) "✅" else "❌"}"
        binding.tvService.text = "Servis: ${if (serviceRunning) "✅" else "❌"}"
        binding.tvStatus.text = when {
            !phonePerm     -> "Durum: Telefon izni gerekli"
            !accessOn      -> "Durum: Erişilebilirlik servisi gerekli"
            serviceRunning -> "Durum: Çalışıyor — Arama bekleniyor"
            else           -> "Durum: Durduruldu"
        }
        binding.btnToggle.text = if (serviceRunning) "AÇIK" else "KAPALI"
        binding.btnToggle.backgroundTintList = ColorStateList.valueOf(
            if (serviceRunning) 0xFF2E7D32.toInt() else 0xFF1565C0.toInt()
        )

        val hasCustom = recordingFile.exists() && recordingFile.length() > 0
        binding.tvAudioSource.text = if (hasCustom)
            "Ses: 🎙 kişisel kayıt (recording.mp3)"
        else
            "Ses: varsayılan mesaj (message.mp3)"
    }

    // ─── İzinler ─────────────────────────────────────────────

    private fun askPermissions() {
        val perms = mutableListOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.RECORD_AUDIO
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val needed = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), REQ_PERMS)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_RECORD &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startRecording()
        }
    }

    companion object {
        private const val REQ_PERMS  = 1
        private const val REQ_RECORD = 2
    }
}
