package com.mydev.dualwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import org.json.JSONArray

class FolderWidget : AppWidgetProvider() {
    companion object {
        fun update(ctx: Context, mgr: AppWidgetManager, id: Int) {
            val prefs = ctx.applicationContext.getSharedPreferences("dw_folder", Context.MODE_PRIVATE)
            val alpha = prefs.getInt("alpha", 200)
            val r = prefs.getInt("r", 26)
            val g = prefs.getInt("g", 26)
            val b = prefs.getInt("b", 46)
            val views = RemoteViews(ctx.packageName, R.layout.folder_widget)
            views.setInt(R.id.folder_root, "setBackgroundColor", (alpha shl 24) or (r shl 16) or (g shl 8) or b)
            val svc = Intent(ctx, FolderService::class.java).putExtra("wid", id)
            views.setRemoteAdapter(R.id.folder_list, svc)
            val launch = Intent(ctx, FolderWidget::class.java).setAction("com.mydev.dualwidget.APP_LAUNCH")
            val pi = PendingIntent.getBroadcast(ctx, id, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            views.setPendingIntentTemplate(R.id.folder_list, pi)
            mgr.updateAppWidget(id, views)
            mgr.notifyAppWidgetViewDataChanged(id, R.id.folder_list)
        }
    }

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) { ids.forEach { update(ctx, mgr, it) } }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        if (intent.action == "com.mydev.dualwidget.APP_LAUNCH") {
            val pkg = intent.getStringExtra("pkg") ?: return
            ctx.packageManager.getLaunchIntentForPackage(pkg)?.let { launch ->
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                ctx.startActivity(launch)
            }
        }
    }

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, opts: Bundle) {
        update(ctx, mgr, id)
    }
}
