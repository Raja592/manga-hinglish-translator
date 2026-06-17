package com.manga.hinglish.translator

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager

class MangaAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: MangaAccessibilityService? = null
            private set

        fun isEnabled(context: Context): Boolean {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val pkg = context.packageName
            val cls = MangaAccessibilityService::class.java.name
            return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { it.resolveInfo.serviceInfo.run { packageName == pkg && name == cls } }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    /**
     * Takes a screenshot silently — NO system dialog needed.
     * Callback fires on main thread. Requires Android 11+.
     */
    fun captureScreen(onResult: (Bitmap?) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        val hw = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
                        screenshot.hardwareBuffer.close()
                        // ML Kit needs a software bitmap
                        val soft = hw?.copy(Bitmap.Config.ARGB_8888, false)
                        hw?.recycle()
                        onResult(soft)
                    }
                    override fun onFailure(errorCode: Int) {
                        onResult(null)
                    }
                }
            )
        } else {
            onResult(null) // Android < 11 not supported
        }
    }
}
