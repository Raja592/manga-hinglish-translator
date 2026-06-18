package com.manga.hinglish.translator

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
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
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FloatingOverlayService : Service() {

    companion object {
        var isRunning = false
        private const val CHANNEL_ID = "manga_translator_channel"
        private const val NOTIF_ID = 1001
    }

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var resultView: View? = null   // small floating result card
    private var resultParams: WindowManager.LayoutParams? = null

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
        if (intent?.action == "STOP") stopSelf()
        return START_STICKY
    }

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    private fun addFloatingButton() {
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_button, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 50; y = 300 }

        windowManager.addView(floatingView, params)
        val btn = floatingView!!.findViewById<ImageButton>(R.id.ibTranslate)

        var ix = 0; var iy = 0; var tx = 0f; var ty = 0f; var drag = false
        btn.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { ix = params.x; iy = params.y; tx = e.rawX; ty = e.rawY; drag = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - tx).toInt(); val dy = (e.rawY - ty).toInt()
                    if (dx * dx + dy * dy > 64) drag = true
                    if (drag) { params.x = ix + dx; params.y = iy + dy; windowManager.updateViewLayout(floatingView, params) }
                    true
                }
                MotionEvent.ACTION_UP -> { if (!drag) onTranslateClicked(); true }
                else -> false
            }
        }
    }

    private fun onTranslateClicked() {
        val btn = floatingView?.findViewById<ImageButton>(R.id.ibTranslate) ?: return
        if (!btn.isEnabled) return

        val svc = MangaAccessibilityService.instance
        if (svc == null) {
            showToast("Accessibility service enable karo! App mein jaake Step 2 pe Enable karo.")
            return
        }

        // Dismiss any previous result
        dismissResult()

        btn.isEnabled = false
        floatingView?.visibility = View.INVISIBLE

        handler.postDelayed({
            svc.captureScreen { bitmap ->
                handler.post {
                    floatingView?.visibility = View.VISIBLE
                    btn.isEnabled = true
                }
                if (bitmap == null) { showToast("Screenshot nahi hua. Dobara try karo."); return@captureScreen }
                serviceScope.launch(Dispatchers.IO) { processCapture(bitmap) }
            }
        }, 300)
    }

    private suspend fun processCapture(bitmap: Bitmap) {
        try {
            showToast("Text padh raha hoon...")
            val text = OcrProcessor.extractText(bitmap)
            if (text.isBlank()) { showToast("Screen pe koi English text nahi mila."); return }

            showToast("Translate ho raha hai...")
            val apiKey = GeminiApiKeyStore.get(this)
            if (apiKey.isEmpty()) { showToast("Gemini API key nahi hai! App kholo aur key daalo."); return }

            val translated = GeminiTranslator(apiKey).translate(text)

            handler.post { showFloatingResult(translated) }
        } catch (e: Exception) {
            showToast("Error: " + (e.message ?: "Unknown").take(80))
        }
    }

    // ── Small floating result card ──────────────────────────────────
    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    private fun showFloatingResult(hinglishText: String) {
        dismissResult()

        val view = LayoutInflater.from(this).inflate(R.layout.floating_result, null)
        view.findViewById<TextView>(R.id.tvResultHinglish).text = hinglishText

        view.findViewById<ImageButton>(R.id.ibCloseResult).setOnClickListener { dismissResult() }

        view.findViewById<android.widget.Button>(R.id.btnResultCopy).setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Hinglish", hinglishText))
            showToast("Copied!")
        }

        val metrics = resources.displayMetrics
        val widthPx = (300 * metrics.density).toInt()

        val params = WindowManager.LayoutParams(
            widthPx,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 80
        }

        // Make result card draggable too
        var ix = 0; var iy = 0; var tx = 0f; var ty = 0f; var drag = false
        view.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { ix = params.x; iy = params.y; tx = e.rawX; ty = e.rawY; drag = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - tx).toInt(); val dy = (e.rawY - ty).toInt()
                    if (dx * dx + dy * dy > 25) drag = true
                    if (drag) { params.x = ix + dx; params.y = iy + dy; windowManager.updateViewLayout(view, params) }
                    true
                }
                MotionEvent.ACTION_UP -> { if (drag) true else false }
                else -> false
            }
        }

        windowManager.addView(view, params)
        resultView = view
        resultParams = params
    }

    private fun dismissResult() {
        resultView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        resultView = null
        resultParams = null
    }

    private fun showToast(msg: String) {
        handler.post { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        dismissResult()
        floatingView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Manga Translator", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Manga Hinglish Translator active"; setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1,
            Intent(this, FloatingOverlayService::class.java).apply { action = "STOP" }, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Manga Hinglish Translator")
            .setContentText("Purple button active — tap karke translate karo!")
            .setSmallIcon(R.drawable.ic_translate)
            .setContentIntent(open).addAction(0, "Stop", stop)
            .setOngoing(true).setPriority(NotificationCompat.PRIORITY_LOW).build()
    }
}
