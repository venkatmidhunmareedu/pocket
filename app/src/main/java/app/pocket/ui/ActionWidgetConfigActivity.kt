package app.pocket.ui

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import app.pocket.Widget
import app.pocket.databinding.ActivityWidgetConfigBinding

/** Shown when a widget is placed (and when reconfigured): choose what it shows and how adding behaves. */
class ActionWidgetConfigActivity : AppCompatActivity() {
    private lateinit var b: ActivityWidgetConfigBinding
    private var id = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        b = ActivityWidgetConfigBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.root.padForSystemBars()

        val c = Widget.load(this, id)
        b.swTotal.isChecked = c.total
        b.swDark.isChecked = c.dark
        b.swPass.isChecked = c.pass
        b.swSmart.isChecked = c.smart
        for (sw in listOf(b.swTotal, b.swDark, b.swPass, b.swSmart)) sw.setOnCheckedChangeListener { _, _ -> render() }
        b.btnCancel.setOnClickListener { finish() }
        b.btnOk.setOnClickListener {
            Widget.save(this, id, cfg())
            AppWidgetManager.getInstance(this).updateAppWidget(id, Widget.views(this, id))
            setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
            finish()
        }
        render()
    }

    private fun cfg() = Widget.Cfg(b.swTotal.isChecked, b.swDark.isChecked, b.swSmart.isChecked, b.swPass.isChecked)

    private fun render() {
        b.preview.removeAllViews()
        b.preview.addView(Widget.views(this, id, cfg()).apply(this, b.preview))
    }
}
