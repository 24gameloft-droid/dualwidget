package com.mydev.dualwidget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import org.json.JSONArray

class PhotoWidget : AppWidgetProvider() {
    companion object {
        const val ACTION = "com.mydev.dualwidget.PHOTO_NEXT"
        const val PREFS = "dw_photo"
        const val INTERVAL = 5 * 60 * 1000L

        fun update(ctx: Context, mgr: AppWidgetManager, id: Int) {
            val prefs = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val json = prefs.getString("photos", "[]") ?: "[]"
            val idx = prefs.getInt("idx", 0)
            val views = RemoteViews(ctx.packageName, R.layout.photo_widget)
            try {
                val arr = JSONArray(json)
                if (arr.length() > 0) {
                    views.setViewVisibility(R.id.photo_hint, View.GONE)
                    views.setViewVisibility(R.id.photo_img, View.VISIBLE)
                    val uri = Uri.parse(arr.getString(idx % arr.length()))
                    val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                    if (bmp != null) views.setImageViewBitmap(R.id.photo_img, bmp)
                    else { views.setViewVisibility(R.id.photo_hint, View.VISIBLE); views.setViewVisibility(R.id.photo_img, View.GONE) }
                } else {
                    views.setViewVisibility(R.id.photo_hint, View.VISIBLE)
                    views.setViewVisibility(R.id.photo_img, View.GONE)
                }
            } catch (e: Exception) {
                views.setViewVisibility(R.id.photo_hint, View.VISIBLE)
                views.setViewVisibility(R.id.photo_img, View.GONE)
            }
            val intent = Intent(ctx, PhotoWidget::class.java).setAction(ACTION)
            val pi = PendingIntent.getBroadcast(ctx, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.photo_root, pi)
            mgr.updateAppWidget(id, views)
        }

        fun schedule(ctx: Context) {
            val intent = Intent(ctx, PhotoWidget::class.java).setAction(ACTION)
            val pi = PendingIntent.getBroadcast(ctx, 9999, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
                .setRepeating(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + INTERVAL, INTERVAL, pi)
        }
    }

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(ctx, mgr, it) }
        schedule(ctx)
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        if (intent.action == ACTION) {
            val prefs = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val arr = try { JSONArray(prefs.getString("photos", "[]")) } catch (e: Exception) { JSONArray() }
            if (arr.length() > 0) {
                val next = (prefs.getInt("idx", 0) + 1) % arr.length()
                prefs.edit().putInt("idx", next).apply()
            }
            val mgr = AppWidgetManager.getInstance(ctx)
            mgr.getAppWidgetIds(ComponentName(ctx, PhotoWidget::class.java)).forEach { update(ctx, mgr, it) }
        }
    }
}
