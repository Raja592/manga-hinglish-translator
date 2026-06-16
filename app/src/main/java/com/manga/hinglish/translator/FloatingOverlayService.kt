package com.manga.hinglish.translator

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
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
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val CHANNEL_ID = "manga_translator_channel"
        private const val NOTIF_ID = 1001
    }

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())

    // Stored projection intent for lazy start
    private var pendingResultCode: Int = 0
    private var pendingResultData: Intent? = null

    override fun onBind(intent: IBinder?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        addFloatingButton()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            pendingResultCode = it.getIntExtra(EXTRA_RESULT_CODE, 0)
            pendingResultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                it.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                it.getParcelableExtra(EXTRA_RESULT_DATA)
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
            x = 100
            y = 300
        }

        windowManager.addView(floatingView, params)

        val btn = floatingView!!.findViewById<ImageButton>(R.id.ibTranslate)

        // Make button draggable
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        btn.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 5 || Math.abs(dy) > 5) isDragging = true
                    if (isDragging) {
                        params.x = initialX + dx
                        params.y = initialY + dy
                        windowManager.updateViewLayout(floatingView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) onCaptureClicked()
                    true
                }
                else -> false
            }
        }
    }

    private fun onCaptureClicked() {
        val btn = floatingView?.findViewById<ImageButton>(R.id.ibTranslate) ?: return
        btn.isEnabled = false

        // Hide the floating button briefly so it doesn't appear in capture
        floatingView?.visibility = View.INVISIBLE

        handler.postDelayed({
            captureAndTranslate(btn)
        }, 200) // small delay to let the button disappear from screen
    }

    private fun captureAndTranslate(btn: ImageButton) {
        if (pendingResultData == null || pendingResultCode == 0) {
            // Need to request screen capture permission first
            floatingView?.visibility = View.VISIBLE
            btn.isEnabled = true
            val intent = Intent(this, ScreenCapturePermissionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            return
        }

        serviceScope.launch(Dispatchers.IO) {
            try {
                val bitmap = captureScreen()
                floatingView?.visibility = View.VISIBLE
                btn.isEnabled = true

                if (bitmap == null) {
                    showToast("Could not capture screen. Try again.")
                    return@launch
                }

                // OCR
                val extractedText = OcrProcessor.extractText(bitmap)

                if (extractedText.isBlank()) {
                    showToast("No English text found on screen.")
                    return@launch
                }

                // Gemini translation
                val apiKey = GeminiApiKeyStore.get(this@FloatingOverlayService)
                val translator = GeminiTranslator(apiKey)
                val hinglishText = translator.translate(extractedText)

                // Show result
                launch(Dispatchers.Main) {
                    showTranslationResult(extractedText, hinglishText)
                }

            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    floatingView?.visibility = View.VISIBLE
                    btn.isEnabled = true
                    showToast("Error: ${e.message}")
                }
            }
        }
    }

    private fun captureScreen(): Bitmap? {
        if (mediaProjection == null) {
            val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(pendingResultCode, pendingResultData!!)
        }

        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        imageReader?.close()
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        val virtualDisplay = mediaProjection!!.createVirtualDisplay(
            "ScreenCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface,
            null, null
        )

        Thread.sleep(300) // wait for frame

        val image = imageReader!!.acquireLatestImage()
        val bitmap = if (image != null) {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * width
            val bmp = Bitmap.createBitmap(
                width + rowPadding / pixelStride,
                height,
                Bitmap.Config.ARGB_8888
            )
            bmp.copyPixelsFromBuffer(buffer)
            image.close()
            Bitmap.createBitmap(bmp, 0, 0, width, height)
        } else null

        virtualDisplay.release()
        return bitmap
    }

    private fun showTranslationResult(original: String, translated: String) {
        val intent = Intent(this, TranslationResultActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(TranslationResultActivity.EXTRA_ORIGINAL, original)
            putExtra(TranslationResultActivity.EXTRA_TRANSLATED, translated)
        }
        startActivity(intent)
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
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Manga Translator",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Manga Hinglish Translator floating service"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, FloatingOverlayService::class.java).apply {
                action = "STOP"
            },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Manga Hinglish Translator")
            .setContentText("Floating button is active. Tap it to translate manga text.")
            .setSmallIcon(R.drawable.ic_translate)
            .setContentIntent(openIntent)
            .addAction(0, "Stop", stopIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
