package com.mydev.dualwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews

class CalcWidget : AppWidgetProvider() {

    companion object {
        const val ACTION = "com.mydev.dualwidget.CALC_BTN"
        const val PREFS  = "dw_calc"

        // مفاتيح ASCII آمنة بالكامل
        private val BTN_MAP = mapOf(
            R.id.btn_ac  to "AC",
            R.id.btn_pm  to "PM",
            R.id.btn_pct to "PCT",
            R.id.btn_div to "DIV",
            R.id.btn_7   to "7",
            R.id.btn_8   to "8",
            R.id.btn_9   to "9",
            R.id.btn_mul to "MUL",
            R.id.btn_4   to "4",
            R.id.btn_5   to "5",
            R.id.btn_6   to "6",
            R.id.btn_sub to "SUB",
            R.id.btn_1   to "1",
            R.id.btn_2   to "2",
            R.id.btn_3   to "3",
            R.id.btn_add to "ADD",
            R.id.btn_0   to "0",
            R.id.btn_dot to "DOT",
            R.id.btn_eq  to "EQ"
        )

        fun update(ctx: Context, mgr: AppWidgetManager, id: Int) {
            val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val display = p.getString("disp_$id", "0") ?: "0"
            val expr    = p.getString("expr_$id",  "")  ?: ""
            val views   = RemoteViews(ctx.packageName, R.layout.calc_widget)
            views.setTextViewText(R.id.calc_display, display)
            views.setTextViewText(R.id.calc_expr, expr)
            var reqCode = id * 100
            for ((rid, key) in BTN_MAP) {
                val pi = PendingIntent.getBroadcast(
                    ctx, reqCode++,
                    Intent(ctx, CalcWidget::class.java)
                        .setAction(ACTION)
                        .putExtra("btn", key)
                        .putExtra("wid", id),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
                views.setOnClickPendingIntent(rid, pi)
            }
            mgr.updateAppWidget(id, views)
        }

        fun press(ctx: Context, mgr: AppWidgetManager, id: Int, btn: String) {
            val p  = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val disp  = p.getString("disp_$id", "0") ?: "0"
            val op    = p.getString("op_$id",   "")  ?: ""
            val first = p.getString("first_$id","")  ?: ""
            val expr  = p.getString("expr_$id", "")  ?: ""
            val ed = p.edit()

            when (btn) {
                "AC" -> { ed.putString("disp_$id","0").putString("expr_$id","").putString("op_$id","").putString("first_$id","") }
                "PM" -> { val n = disp.toDoubleOrNull() ?: 0.0; ed.putString("disp_$id", fmt(-n)) }
                "PCT" -> { val n = disp.toDoubleOrNull() ?: 0.0; ed.putString("disp_$id", fmt(n / 100.0)) }
                "DIV","MUL","SUB","ADD" -> {
                    val sym = when(btn){ "DIV"->"/" "MUL"->"x" "SUB"->"-" else->"+" }
                    ed.putString("first_$id", disp).putString("op_$id", btn)
                      .putString("expr_$id", "$disp $sym").putString("disp_$id", "0")
                }
                "EQ" -> {
                    if (op.isNotEmpty() && first.isNotEmpty()) {
                        val a = first.toDoubleOrNull() ?: 0.0
                        val b = disp.toDoubleOrNull()  ?: 0.0
                        val res = when(op) {
                            "DIV" -> if (b != 0.0) a / b else Double.NaN
                            "MUL" -> a * b
                            "SUB" -> a - b
                            else  -> a + b
                        }
                        val sym = when(op){ "DIV"->"/" "MUL"->"x" "SUB"->"-" else->"+" }
                        ed.putString("disp_$id", fmt(res))
                          .putString("expr_$id", "$first $sym $disp =")
                          .putString("op_$id","").putString("first_$id","")
                    }
                }
                "DOT" -> { if (!disp.contains(".")) ed.putString("disp_$id", "$disp.") }
                else  -> {
                    val cur = if (disp == "0") btn else disp + btn
                    ed.putString("disp_$id", cur)
                }
            }
            ed.apply()
            update(ctx, mgr, id)
        }

        private fun fmt(v: Double): String {
            if (v.isNaN()) return "Error"
            val l = v.toLong()
            return if (v == l.toDouble()) l.toString() else "%.10g".format(v).trimEnd('0').trimEnd('.')
        }
    }

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(ctx, mgr, it) }
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        if (intent.action == ACTION) {
            val btn = intent.getStringExtra("btn") ?: return
            val id  = intent.getIntExtra("wid", -1)
            if (id == -1) return
            press(ctx, AppWidgetManager.getInstance(ctx), id, btn)
        }
    }

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, opts: Bundle) {
        update(ctx, mgr, id)
    }
}
