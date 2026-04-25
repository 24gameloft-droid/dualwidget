package com.mydev.dualwidget

import android.app.Notification
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

class NotifListener : NotificationListenerService() {

    private val handler = Handler(Looper.getMainLooper())
    private var overlayView: android.view.View? = null
    private lateinit var wm: WindowManager

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text  = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val appName = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(sbn.packageName, 0)
            ).toString()
        } catch (e: Exception) { sbn.packageName }

        val prefs = getSharedPreferences("dw_notif", Context.MODE_PRIVATE)
        val alpha  = prefs.getInt("alpha", 210)
        val r      = prefs.getInt("r", 20)
        val g      = prefs.getInt("g", 20)
        val b      = prefs.getInt("b", 30)
        val secs   = prefs.getInt("secs", 5)
        val bgColor = (alpha shl 24) or (r shl 16) or (g shl 8) or b

        handler.post {
            removeOverlay()
            val canDraw = android.provider.Settings.canDrawOverlays(this)
            if (!canDraw) return@post

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                x = 8; y = 80
            }

            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(bgColor)
                setPadding(24, 14, 24, 14)
            }

            val tvApp = TextView(this).apply {
                text = appName
                textSize = 10f
                setTextColor(Color.argb(180,255,255,255))
            }
            val tvTitle = TextView(this).apply {
                text = title
                textSize = 13f
                setTextColor(Color.WHITE)
                setTypeface(null, android.graphics.Typeface.BOLD)
                maxLines = 1
            }
            val tvText = TextView(this).apply {
                text = text
                textSize = 11f
                setTextColor(Color.argb(220,255,255,255))
                maxLines = 2
            }

            if (appName.isNotEmpty()) container.addView(tvApp)
            if (title.isNotEmpty())   container.addView(tvTitle)
            if (text.isNotEmpty())    container.addView(tvText)

            if (container.childCount == 0) return@post

            try {
                wm.addView(container, params)
                overlayView = container
                handler.postDelayed({ removeOverlay() }, secs * 1000L)
            } catch (e: Exception) {}
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {}

    private fun removeOverlay() {
        overlayView?.let {
            try { wm.removeView(it) } catch (e: Exception) {}
            overlayView = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
    }
}
