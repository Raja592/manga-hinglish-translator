package com.manga.hinglish.translator

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.manga.hinglish.translator.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val overlayPermLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updateUiState()
        if (Settings.canDrawOverlays(this))
            Toast.makeText(this, "Overlay permission mili!", Toast.LENGTH_SHORT).show()
    }

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { updateUiState() }

    private val accessibilityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { updateUiState() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupUi()
        updateUiState()
    }

    override fun onResume() {
        super.onResume()
        updateUiState()
    }

    private fun setupUi() {
        binding.btnGrantOverlay.setOnClickListener {
            overlayPermLauncher.launch(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        }

        binding.btnGrantAccessibility.setOnClickListener {
            Toast.makeText(this, "Manga Hinglish dhundho aur Enable karo", Toast.LENGTH_LONG).show()
            accessibilityLauncher.launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.btnGrantNotification.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        binding.btnStartService.setOnClickListener { startFloatingService() }
        binding.btnStopService.setOnClickListener { stopFloatingService() }

        binding.etApiKey.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val k = binding.etApiKey.text.toString().trim()
                if (k.isNotEmpty()) { GeminiApiKeyStore.set(this, k); updateUiState() }
            }
        }

        binding.btnSaveKey.setOnClickListener {
            val k = binding.etApiKey.text.toString().trim()
            if (k.isEmpty()) { Toast.makeText(this, "API key daalo pehle", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            GeminiApiKeyStore.set(this, k)
            Toast.makeText(this, "API key save ho gayi!", Toast.LENGTH_SHORT).show()
            updateUiState()
        }

        binding.tvGetApiKey.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey")))
        }
    }

    private fun updateUiState() {
        val hasOverlay = Settings.canDrawOverlays(this)
        val hasAccessibility = MangaAccessibilityService.isEnabled(this)
        val hasApiKey = GeminiApiKeyStore.get(this).isNotEmpty()
        val running = FloatingOverlayService.isRunning

        // Step 1: Overlay
        binding.ivOverlayCheck.visibility = if (hasOverlay) View.VISIBLE else View.GONE
        binding.btnGrantOverlay.visibility = if (hasOverlay) View.GONE else View.VISIBLE

        // Step 2: Accessibility
        binding.ivAccessibilityCheck.visibility = if (hasAccessibility) View.VISIBLE else View.GONE
        binding.btnGrantAccessibility.visibility = if (hasAccessibility) View.GONE else View.VISIBLE

        // Step 3: API Key
        binding.ivApiKeyCheck.visibility = if (hasApiKey) View.VISIBLE else View.GONE
        if (hasApiKey && binding.etApiKey.text.isNullOrEmpty()) {
            binding.etApiKey.setText("\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022")
        }

        // Start/Stop
        binding.btnStartService.isEnabled = hasOverlay && hasAccessibility && hasApiKey && !running
        binding.btnStopService.isEnabled = running

        // Notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNotif = checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            binding.layoutNotifPermission.visibility = if (hasNotif) View.GONE else View.VISIBLE
        } else {
            binding.layoutNotifPermission.visibility = View.GONE
        }

        binding.tvStatus.text = when {
            running -> "\u2705 Floating button active! Manga app pe jao."
            !hasOverlay -> "\u26a0\ufe0f Step 1: Overlay permission do."
            !hasAccessibility -> "\u26a0\ufe0f Step 2: Accessibility service enable karo!"
            !hasApiKey -> "\u26a0\ufe0f Step 3: Gemini API key daalo."
            else -> "Ready! Start Floating Button dabao."
        }
    }

    private fun startFloatingService() {
        val i = Intent(this, FloatingOverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i) else startService(i)
        Toast.makeText(this, "Floating button start! Manga app pe jao.", Toast.LENGTH_LONG).show()
        updateUiState()
        moveTaskToBack(true)
    }

    private fun stopFloatingService() {
        stopService(Intent(this, FloatingOverlayService::class.java))
        updateUiState()
    }
}
