package com.manga.hinglish.translator

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FloatingOverlayService : Service() {

    companion object {
        var isRunning = false
        const val ACTION_PROJECTION_GRANTED = "ACTION_PROJECTION_GRANTED"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val CHANNEL_ID = "manga_translator_channel"
        private const val NOTIF_ID = 1001
    }

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null

    // MediaProjection is created ONCE and reused — never recreated unless it dies
    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        addFloatingButton()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_PROJECTION_GRANTED) {
            val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
            val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_RESULT_DATA)
            }
            if (resultCode != 0 && resultData != null) {
                // Create MediaProjection IMMEDIATELY on permission grant — only done ONCE
                try {
                    val pm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    mediaProjection?.stop()
                    mediaProjection = pm.getMediaProjection(resultCode, resultData)
                    // Auto-capture after short delay
                    handler.postDelayed({ triggerCapture() }, 700)
                } catch (e: Exception) {
                    showToast("Screen capture setup failed: " + (e.message ?: ""))
                }
            }
        }
        return START_STICKY
    }

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    private fun addFloatingButton() {
        val inflater = LayoutInflater.from(this)
        floatingView = inflater.inflate(R.layout.floating_button, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 300
        }

        windowManager.addView(floatingView, params)

        val btn = floatingView!!.findViewById<ImageButton>(R.id.ibTranslate)
        var initialX = 0; var initialY = 0
        var initialTouchX = 0f; var initialTouchY = 0f
        var isDragging = false

        btn.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x; initialY = params.y
                    initialTouchX = event.rawX; initialTouchY = event.rawY
                    isDragging = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (dx * dx + dy * dy > 64) isDragging = true
                    if (isDragging) {
                        params.x = initialX + dx
                        params.y = initialY + dy
                        windowManager.updateViewLayout(floatingView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) triggerCapture()
                    true
                }
                else -> false
            }
        }
    }

    private fun triggerCapture() {
        val btn = floatingView?.findViewById<ImageButton>(R.id.ibTranslate) ?: return
        if (!btn.isEnabled) return
        btn.isEnabled = false
        floatingView?.visibility = View.INVISIBLE

        handler.postDelayed({
            if (mediaProjection == null) {
                // Only ask for permission if we don't have a valid MediaProjection
                floatingView?.visibility = View.VISIBLE
                btn.isEnabled = true
                val intent = Intent(this, ScreenCapturePermissionActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(intent)
            } else {
                doCapture(btn)
            }
        }, 350)
    }

    private fun doCapture(btn: ImageButton) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val bitmap = captureScreen()

                handler.post {
                    floatingView?.visibility = View.VISIBLE
                    btn.isEnabled = true
                }

                if (bitmap == null) {
                    showToast("Screen capture nahi hua — dobara tap karo.")
                    return@launch
                }

                showToast("Text padh raha hoon...")
                val extractedText = OcrProcessor.extractText(bitmap)

                if (extractedText.isBlank()) {
                    showToast("Screen pe koi English text nahi mila.")
                    return@launch
                }

                showToast("Hinglish mein badal raha hoon...")
                val apiKey = GeminiApiKeyStore.get(this@FloatingOverlayService)
                if (apiKey.isEmpty()) {
                    showToast("Gemini API key nahi hai! App kholo aur key daalo.")
                    return@launch
                }

                val translator = GeminiTranslator(apiKey)
                val hinglishText = translator.translate(extractedText)

                handler.post {
                    val intent = Intent(this@FloatingOverlayService, TranslationResultActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        putExtra(TranslationResultActivity.EXTRA_ORIGINAL, extractedText)
                        putExtra(TranslationResultActivity.EXTRA_TRANSLATED, hinglishText)
                    }
                    startActivity(intent)
                }

            } catch (e: Exception) {
                handler.post {
                    floatingView?.visibility = View.VISIBLE
                    btn.isEnabled = true
                    // If projection is dead, reset so user can grant again
                    val errMsg = e.message ?: ""
                    if (errMsg.contains("MediaProjection") || errMsg.contains("projection")) {
                        mediaProjection = null
                        showToast("Screen permission expire hua. Dobara tap karke allow karo.")
                    } else {
                        showToast("Error: " + errMsg.take(80))
                    }
                }
            }
        }
    }

    private fun captureScreen(): Bitmap? {
        val mp = mediaProjection ?: return null

        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        imageReader?.close()
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        // VirtualDisplay is created fresh each capture, released after — MediaProjection stays alive
        val vd = mp.createVirtualDisplay(
            "ScreenCapture", width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, null
        )

        Thread.sleep(500)

        val image = imageReader!!.acquireLatestImage()
        val bmp = if (image != null) {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * width
            val raw = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
            raw.copyPixelsFromBuffer(buffer)
            image.close()
            Bitmap.createBitmap(raw, 0, 0, width, height)
        } else null

        vd.release()
        return bmp
    }

    private fun showToast(msg: String) {
        handler.post { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        floatingView?.let { windowManager.removeView(it) }
        imageReader?.close()
        mediaProjection?.stop()
        mediaProjection = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Manga Translator", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Manga Hinglish Translator active"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1,
            Intent(this, FloatingOverlayService::class.java).apply { action = "STOP" },
            PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Manga Hinglish Translator")
            .setContentText("Purple button active — tap karke translate karo!")
            .setSmallIcon(R.drawable.ic_translate)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
