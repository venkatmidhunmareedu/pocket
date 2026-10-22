package app.pocket

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.text.format.DateUtils
import android.widget.RemoteViews
import app.pocket.data.EXPENSE
import app.pocket.data.Fmt
import app.pocket.data.INCOME
import app.pocket.data.Store
import app.pocket.data.TRANSFER
import app.pocket.ui.InputActivity
import app.pocket.ui.MainActivity
import java.math.BigDecimal

/** Home-screen widget: total balance (or time since last record) plus one-tap add expense / income / transfer. */
object Widget {
    data class Cfg(val total: Boolean = true, val dark: Boolean = false, val smart: Boolean = true, val pass: Boolean = false)

    // ponytail: per-widget options in SharedPreferences; fine for a handful of widgets.
    private fun sp(ctx: Context) = ctx.getSharedPreferences("widgets", Context.MODE_PRIVATE)

    fun load(ctx: Context, id: Int): Cfg = sp(ctx).let {
        Cfg(it.getBoolean("${id}_total", true), it.getBoolean("${id}_dark", false), it.getBoolean("${id}_smart", true), it.getBoolean("${id}_pass", false))
    }

    fun save(ctx: Context, id: Int, c: Cfg) {
        sp(ctx).edit().putBoolean("${id}_total", c.total).putBoolean("${id}_dark", c.dark)
            .putBoolean("${id}_smart", c.smart).putBoolean("${id}_pass", c.pass).apply()
    }

    fun remove(ctx: Context, id: Int) {
        sp(ctx).edit().remove("${id}_total").remove("${id}_dark").remove("${id}_smart").remove("${id}_pass").apply()
    }

    private fun add(ctx: Context, id: Int, type: Int, cfg: Cfg, n: Int) = PendingIntent.getActivity(
        ctx, id * 10 + n,
        Intent(ctx, InputActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(InputActivity.EXTRA_TYPE, type)
            .putExtra(InputActivity.EXTRA_SMART, cfg.smart)
            .putExtra(InputActivity.EXTRA_REQUIRE_PASS, cfg.pass),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun views(ctx: Context, id: Int, cfg: Cfg = load(ctx, id)): RemoteViews {
        val rv = RemoteViews(ctx.packageName, R.layout.aw_action_widget)
        val fg = if (cfg.dark) 0xFFFFF59D.toInt() else 0xFF004D40.toInt()
        val sub = if (cfg.dark) 0xFFADA98B.toInt() else 0xFF669383.toInt()
        val line = if (cfg.dark) 0x33FFFFFF else 0x33000000
        rv.setInt(R.id.aw_root, "setBackgroundResource", if (cfg.dark) R.drawable.bg_aw_dark else R.drawable.bg_aw_light)
        if (cfg.total) {
            rv.setTextViewText(R.id.aw_title, "All accounts")
            rv.setTextViewText(R.id.aw_balance, Fmt.money(Store.accounts().fold(BigDecimal.ZERO) { a, x -> a + x.balance }))
        } else {
            val last = Store.allRecords().maxByOrNull { it.id }
            rv.setTextViewText(R.id.aw_title, "Last added")
            rv.setTextViewText(
                R.id.aw_balance,
                last?.let { DateUtils.getRelativeTimeSpanString(it.id, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString() } ?: "No record yet",
            )
        }
        rv.setTextColor(R.id.aw_title, sub)
        rv.setTextColor(R.id.aw_balance, fg)
        for (v in listOf(R.id.aw_launch, R.id.aw_expense, R.id.aw_income, R.id.aw_transfer)) {
            rv.setColorStateList(v, "setImageTintList", ColorStateList.valueOf(fg))
        }
        for (v in listOf(R.id.aw_div1, R.id.aw_div2, R.id.aw_div3)) rv.setInt(v, "setBackgroundColor", line)
        rv.setOnClickPendingIntent(
            R.id.aw_launch,
            PendingIntent.getActivity(
                ctx, id * 10, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        rv.setOnClickPendingIntent(R.id.aw_expense, add(ctx, id, EXPENSE, cfg, 1))
        rv.setOnClickPendingIntent(R.id.aw_income, add(ctx, id, INCOME, cfg, 2))
        rv.setOnClickPendingIntent(R.id.aw_transfer, add(ctx, id, TRANSFER, cfg, 3))
        return rv
    }

    fun refreshAll(ctx: Context) {
        val m = AppWidgetManager.getInstance(ctx)
        for (id in m.getAppWidgetIds(ComponentName(ctx, ActionWidgetProvider::class.java))) m.updateAppWidget(id, views(ctx, id))
    }
}

class ActionWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) mgr.updateAppWidget(id, Widget.views(ctx, id))
    }

    override fun onDeleted(ctx: Context, ids: IntArray) { for (id in ids) Widget.remove(ctx, id) }
}
