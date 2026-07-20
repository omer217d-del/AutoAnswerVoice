package com.autoanswervoice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.autoanswervoice.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var serviceRunning = false

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

        askPermissions()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

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

    private fun updateUI() {
        val accessOn = AutoAnswerAccessibilityService.instance != null
        val phonePerm = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        binding.tvAccessibility.text = "Erişilebilirlik: ${if (accessOn) "✅" else "❌"}"
        binding.tvService.text = "Servis: ${if (serviceRunning) "✅" else "❌"}"
        binding.tvStatus.text = when {
            !phonePerm -> "Durum: Telefon izni gerekli"
            !accessOn -> "Durum: Erişilebilirlik servisi gerekli"
            serviceRunning -> "Durum: Çalışıyor — Arama bekleniyor"
            else -> "Durum: Durduruldu"
        }
        binding.btnToggle.text = if (serviceRunning) "AÇIK" else "KAPALI"
        binding.btnToggle.backgroundTintList = ColorStateList.valueOf(
            if (serviceRunning) 0xFF2E7D32.toInt() else 0xFF1565C0.toInt()
        )
    }

    private fun askPermissions() {
        val perms = mutableListOf(Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val needed = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 1)
        }
    }
}
