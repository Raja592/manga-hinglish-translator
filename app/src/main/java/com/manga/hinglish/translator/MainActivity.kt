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

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            updateUiState()
            Toast.makeText(this, "Overlay permission granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Overlay permission denied. App won't work without it.", Toast.LENGTH_LONG).show()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) updateUiState()
    }

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
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        }

        binding.btnGrantNotification.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        binding.btnStartService.setOnClickListener {
            startFloatingService()
        }

        binding.btnStopService.setOnClickListener {
            stopFloatingService()
        }

        binding.etApiKey.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val key = binding.etApiKey.text.toString().trim()
                if (key.isNotEmpty()) {
                    GeminiApiKeyStore.set(this, key)
                    Toast.makeText(this, "API key saved!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.btnSaveKey.setOnClickListener {
            val key = binding.etApiKey.text.toString().trim()
            if (key.isEmpty()) {
                Toast.makeText(this, "Please enter your Gemini API key", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            GeminiApiKeyStore.set(this, key)
            Toast.makeText(this, "API key saved!", Toast.LENGTH_SHORT).show()
            updateUiState()
        }

        binding.tvGetApiKey.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"))
            startActivity(intent)
        }
    }

    private fun updateUiState() {
        val hasOverlay = Settings.canDrawOverlays(this)
        val hasApiKey = GeminiApiKeyStore.get(this).isNotEmpty()
        val serviceRunning = FloatingOverlayService.isRunning

        // Overlay permission row
        binding.ivOverlayCheck.visibility = if (hasOverlay) View.VISIBLE else View.GONE
        binding.btnGrantOverlay.visibility = if (hasOverlay) View.GONE else View.VISIBLE

        // API key row
        binding.ivApiKeyCheck.visibility = if (hasApiKey) View.VISIBLE else View.GONE
        if (hasApiKey && binding.etApiKey.text.isNullOrEmpty()) {
            binding.etApiKey.setText("••••••••••••••••")
        }

        // Service control
        binding.btnStartService.isEnabled = hasOverlay && hasApiKey && !serviceRunning
        binding.btnStopService.isEnabled = serviceRunning

        binding.tvStatus.text = when {
            serviceRunning -> "✅ Floating button is active! Go to your manga app."
            !hasOverlay -> "⚠️ Please grant overlay permission first."
            !hasApiKey -> "⚠️ Please enter your Gemini API key."
            else -> "Ready to start!"
        }

        // Notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNotif = checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            binding.layoutNotifPermission.visibility = if (hasNotif) View.GONE else View.VISIBLE
        } else {
            binding.layoutNotifPermission.visibility = View.GONE
        }
    }

    private fun startFloatingService() {
        val intent = Intent(this, FloatingOverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "Floating button started! You can now switch to your manga app.", Toast.LENGTH_LONG).show()
        updateUiState()
        // Minimize the app
        moveTaskToBack(true)
    }

    private fun stopFloatingService() {
        val intent = Intent(this, FloatingOverlayService::class.java)
        stopService(intent)
        updateUiState()
    }
}
