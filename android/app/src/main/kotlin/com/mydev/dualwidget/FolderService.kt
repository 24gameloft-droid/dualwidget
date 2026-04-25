package com.mydev.dualwidget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import org.json.JSONArray

class FolderService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent) =
        Factory(applicationContext, intent.getIntExtra("wid", -1))
}

class Factory(private val ctx: Context, private val wid: Int) : RemoteViewsService.RemoteViewsFactory {
    private data class AppItem(val pkg: String, val label: String)
    private val items = mutableListOf<AppItem>()

    override fun onCreate() { load() }
    override fun onDataSetChanged() { load() }
    override fun onDestroy() { items.clear() }

    private fun load() {
        items.clear()
        try {
            val prefs = ctx.getSharedPreferences("dw_folder", Context.MODE_PRIVATE)
            val fJson = prefs.getString("folders", "[]") ?: "[]"
            val fidx = prefs.getInt("wid_$wid", 0)
            val folders = JSONArray(fJson)
            if (folders.length() > 0) {
                val idx = if (fidx < folders.length()) fidx else 0
                val apps = folders.getJSONObject(idx).optJSONArray("apps") ?: JSONArray()
                val pm = ctx.packageManager
                for (i in 0 until apps.length()) {
                    val pkg = apps.getString(i)
                    val label = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { pkg }
                    items.add(AppItem(pkg, label))
                }
            }
        } catch (e: Exception) {}
    }

    override fun getCount() = items.size
    override fun getLoadingView() = null
    override fun getViewTypeCount() = 1
    override fun getItemId(pos: Int) = pos.toLong()
    override fun hasStableIds() = true

    override fun getViewAt(pos: Int): RemoteViews {
        val rv = RemoteViews(ctx.packageName, R.layout.list_item)
        if (pos >= items.size) return rv
        val item = items[pos]
        try {
            val icon = ctx.packageManager.getApplicationIcon(item.pkg)
            rv.setImageViewBitmap(R.id.item_icon, toBmp(icon))
        } catch (e: Exception) {}
        rv.setOnClickFillInIntent(R.id.item_root, Intent().putExtra("pkg", item.pkg))
        return rv
    }

    private fun toBmp(d: Drawable): Bitmap {
        if (d is BitmapDrawable && d.bitmap != null) return d.bitmap
        val w = if (d.intrinsicWidth > 0) d.intrinsicWidth else 96
        val h = if (d.intrinsicHeight > 0) d.intrinsicHeight else 96
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, w, h); d.draw(Canvas(bmp)); return bmp
    }
}
