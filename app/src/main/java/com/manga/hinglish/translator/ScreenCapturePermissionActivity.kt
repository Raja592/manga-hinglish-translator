package com.manga.hinglish.translator

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/**
 * Transparent activity used to request MediaProjection permission.
 * Once granted, it passes the result to FloatingOverlayService which then
 * auto-triggers a capture — user does NOT need to tap again.
 */
class ScreenCapturePermissionActivity : AppCompatActivity() {

    private val capturePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val intent = Intent(this, FloatingOverlayService::class.java).apply {
                action = FloatingOverlayService.ACTION_PROJECTION_GRANTED
                putExtra(FloatingOverlayService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(FloatingOverlayService.EXTRA_RESULT_DATA, result.data)
            }
            startService(intent)
            Toast.makeText(this, "Capturing screen...", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Screen capture permission denied.", Toast.LENGTH_LONG).show()
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        capturePermissionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }
}
