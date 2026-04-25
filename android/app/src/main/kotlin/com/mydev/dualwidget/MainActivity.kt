package com.mydev.dualwidget

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : FlutterActivity() {
    private val CH = "com.mydev.dualwidget/ch"
    private val PICK = 6001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, arrayOf(perm), 6000)
    }

    override fun configureFlutterEngine(fe: FlutterEngine) {
        super.configureFlutterEngine(fe)
        val pp = getSharedPreferences(PhotoWidget.PREFS, Context.MODE_PRIVATE)
        val fp = getSharedPreferences("dw_folder", Context.MODE_PRIVATE)

        MethodChannel(fe.dartExecutor.binaryMessenger, CH).setMethodCallHandler { call, result ->
            when (call.method) {
                "init" -> {
                    val albums = JSONArray()
                    try {
                        val cur = contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            arrayOf(MediaStore.Images.Media.BUCKET_ID, MediaStore.Images.Media.BUCKET_DISPLAY_NAME, MediaStore.Images.Media._ID),
                            null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC")
                        val seen = mutableSetOf<String>()
                        cur?.use {
                            val biCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
                            val bnCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                            val idCol = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                            while (it.moveToNext()) {
                                val bid = it.getString(biCol) ?: continue
                                if (seen.add(bid)) {
                                    albums.put(JSONObject().apply {
                                        put("id", bid)
                                        put("name", it.getString(bnCol) ?: "Unknown")
                                        put("thumb", "${MediaStore.Images.Media.EXTERNAL_CONTENT_URI}/${it.getLong(idCol)}")
                                    })
                                }
                            }
                        }
                    } catch (e: Exception) {}

                    val pm = packageManager
                    val appList = mutableListOf<Pair<String,String>>()
                    val i = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
                    for (ri in pm.queryIntentActivities(i, 0)) try {
                        val info = ri.activityInfo.applicationInfo
                        appList.add(pm.getApplicationLabel(info).toString() to info.packageName)
                    } catch (e: Exception) {}
                    appList.sortBy { it.first }
                    val apps = JSONArray()
                    for ((n, p) in appList) apps.put(JSONObject().apply { put("n", n); put("p", p) })

                    result.success(JSONObject().apply {
                        put("albums", albums)
                        put("apps", apps)
                        put("folders", JSONArray(fp.getString("folders", "[]")))
                        put("photoCount", try { JSONArray(pp.getString("photos", "[]")).length() } catch (e: Exception) { 0 })
                        put("fr", fp.getInt("r", 26)); put("fg", fp.getInt("g", 26))
                        put("fb", fp.getInt("b", 46)); put("fa", fp.getInt("alpha", 200))
                    }.toString())
                }
                "selectAlbum" -> {
                    val bid = call.argument<String>("id") ?: ""
                    val uris = JSONArray()
                    try {
                        val cur = contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            arrayOf(MediaStore.Images.Media._ID),
                            "${MediaStore.Images.Media.BUCKET_ID}=?", arrayOf(bid),
                            "${MediaStore.Images.Media.DATE_ADDED} DESC")
                        cur?.use {
                            val idCol = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                            while (it.moveToNext()) uris.put("${MediaStore.Images.Media.EXTERNAL_CONTENT_URI}/${it.getLong(idCol)}")
                        }
                    } catch (e: Exception) {}
                    pp.edit().putString("photos", uris.toString()).putInt("idx", 0).apply()
                    val mgr = AppWidgetManager.getInstance(this)
                    mgr.getAppWidgetIds(ComponentName(this, PhotoWidget::class.java)).forEach { PhotoWidget.update(this, mgr, it) }
                    PhotoWidget.schedule(this)
                    result.success(uris.length())
                }
                "pickPhotos" -> { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type="image/*"; putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true); addCategory(Intent.CATEGORY_OPENABLE) }, PICK); result.success(true) }
                "saveFolders" -> {
                    fp.edit()
                        .putString("folders", call.argument<String>("folders") ?: "[]")
                        .putInt("alpha", call.argument<Int>("a") ?: 200)
                        .putInt("r", call.argument<Int>("r") ?: 26)
                        .putInt("g", call.argument<Int>("g") ?: 26)
                        .putInt("b", call.argument<Int>("b") ?: 46)
                        .apply()
                    val mgr = AppWidgetManager.getInstance(this)
                    mgr.getAppWidgetIds(ComponentName(this, FolderWidget::class.java)).forEach { FolderWidget.update(this, mgr, it) }
                    result.success(true)
                }
                "pinPhoto" -> { pin(PhotoWidget::class.java); result.success(true) }
                "pinFolder" -> { pin(FolderWidget::class.java); result.success(true) }
                "pinCalc" -> { pin(CalcWidget::class.java); result.success(true) }
                "saveNotif" -> {
                    val np = getSharedPreferences("dw_notif", Context.MODE_PRIVATE)
                    np.edit()
                        .putInt("alpha", call.argument<Int>("a") ?: 210)
                        .putInt("r", call.argument<Int>("r") ?: 20)
                        .putInt("g", call.argument<Int>("g") ?: 20)
                        .putInt("b", call.argument<Int>("b") ?: 30)
                        .putInt("secs", call.argument<Int>("secs") ?: 5)
                        .apply()
                    result.success(true)
                }
                "requestOverlay" -> {
                    if (!android.provider.Settings.canDrawOverlays(this)) {
                        startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.parse("package:$packageName")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                    result.success(true)
                }
                "requestNotifAccess" -> {
                    startActivity(android.content.Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                    result.success(true)
                }
                else -> result.notImplemented()
            }
        }
    }

    private fun <T> pin(cls: Class<T>) {
        val mgr = AppWidgetManager.getInstance(this)
        if (mgr.isRequestPinAppWidgetSupported) mgr.requestPinAppWidget(ComponentName(this, cls), null, null)
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == PICK && res == RESULT_OK && data != null) {
            val pp = getSharedPreferences(PhotoWidget.PREFS, Context.MODE_PRIVATE)
            val uris = JSONArray()
            data.clipData?.let { cd -> for (i in 0 until cd.itemCount) { val u=cd.getItemAt(i).uri; try{contentResolver.takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION)}catch(e:Exception){}; uris.put(u.toString()) } }
                ?: data.data?.let { u -> try{contentResolver.takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION)}catch(e:Exception){}; uris.put(u.toString()) }
            val existing = try { JSONArray(pp.getString("photos","[]")) } catch(e:Exception){ JSONArray() }
            for (i in 0 until existing.length()) uris.put(existing.getString(i))
            pp.edit().putString("photos", uris.toString()).putInt("idx", 0).apply()
            val mgr = AppWidgetManager.getInstance(this)
            mgr.getAppWidgetIds(ComponentName(this, PhotoWidget::class.java)).forEach { PhotoWidget.update(this, mgr, it) }
            PhotoWidget.schedule(this)
        }
    }
}
