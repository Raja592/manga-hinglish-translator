package com.manga.hinglish.translator

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class ScreenCapturePermissionActivity : AppCompatActivity() {

    private val launcher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val svc = Intent(this, FloatingOverlayService::class.java).apply {
                action = FloatingOverlayService.ACTION_PROJECTION_GRANTED
                putExtra(FloatingOverlayService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(FloatingOverlayService.EXTRA_RESULT_DATA, result.data)
            }
            startService(svc)
            Toast.makeText(this, "Screen capture allowed! Translating...", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Screen capture denied. Dobara try karo.", Toast.LENGTH_LONG).show()
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        launcher.launch(pm.createScreenCaptureIntent())
    }
}
