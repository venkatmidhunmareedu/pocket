package app.pocket.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import app.pocket.R
import app.pocket.Reminder
import app.pocket.data.Prefs
import app.pocket.databinding.ActivityIntroBinding
import app.pocket.databinding.ItemGuideBinding
import com.google.android.material.materialswitch.MaterialSwitch

/** First-run flow: welcome + quick guide, currency, daily reminder. */
class IntroActivity : AppCompatActivity() {
    private lateinit var b: ActivityIntroBinding
    private var step = 0
    private var currency = Prefs.currencyCode
    private var warn: View? = null

    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        warn?.visibility = if (granted) View.GONE else View.VISIBLE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityIntroBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.root.padForSystemBars()
        b.btnBack.setOnClickListener { show(step - 1) }
        b.btnNext.setOnClickListener {
            if (step == 1) Prefs.currencyCode = currency
            if (step == 2) finishIntro() else show(step + 1)
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (step > 0) show(step - 1) else finishAffinity() }
        })
        show(0)
    }

    private fun show(i: Int) {
        step = i
        b.page.removeAllViews()
        val inf = LayoutInflater.from(this)
        when (i) {
            0 -> guide(inf)
            1 -> currencyPage(inf)
            else -> notificationPage(inf)
        }
        b.btnBack.visibility = if (i == 0) View.INVISIBLE else View.VISIBLE
        b.btnNext.text = if (i == 2) "Start" else "Next"
    }

    private fun guide(inf: LayoutInflater) {
        val page = inf.inflate(R.layout.intro_guide, b.page, false)
        val list = page.findViewById<android.widget.LinearLayout>(R.id.guide_list)
        listOf(
            Triple(R.drawable.ic_label, "Category", "Categories are labels for organizing expenses and income. Pocket comes with common ones, and you can add more any time."),
            Triple(R.drawable.ic_account_balance_wallet, "Account", "An account is a source of money, like a wallet, a card or savings. Start with the included accounts or add your own."),
            Triple(R.drawable.ic_receipt_long, "Record", "Add one record for every expense or income. Take a record when you pay for something, and add your earnings too."),
        ).forEach { (icon, title, text) ->
            val row = ItemGuideBinding.inflate(inf, list, false)
            row.iv.setImageResource(icon); row.tvTitle.text = title; row.tvText.text = text
            list.addView(row.root)
        }
        b.page.addView(page)
    }

    private fun currencyPage(inf: LayoutInflater) {
        val page = inf.inflate(R.layout.intro_currency, b.page, false)
        val names = resources.getStringArray(R.array.entries_currency)
        val codes = resources.getStringArray(R.array.values_currency)
        val list = page.findViewById<ListView>(R.id.list)
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, names)
        val at = codes.indexOf(currency).coerceAtLeast(0)
        list.setItemChecked(at, true)
        list.setSelection(at)
        list.setOnItemClickListener { _, _, pos, _ -> currency = codes[pos] }
        b.page.addView(page)
    }

    private fun notificationPage(inf: LayoutInflater) {
        val page = inf.inflate(R.layout.intro_notification, b.page, false)
        warn = page.findViewById(R.id.tv_warn)
        val sw = page.findViewById<MaterialSwitch>(R.id.switch_notification)
        sw.isChecked = Prefs.reminderOn
        sw.setOnCheckedChangeListener { _, on ->
            Prefs.reminderEnabled = on
            Reminder.apply(this)
            if (on && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else warn?.visibility = View.GONE
        }
        b.page.addView(page)
    }

    private fun finishIntro() {
        Prefs.introDone = true
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
