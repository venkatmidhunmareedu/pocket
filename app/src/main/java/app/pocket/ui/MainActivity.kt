package app.pocket.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.activity.viewModels
import app.pocket.R
import app.pocket.data.Fmt
import app.pocket.data.Period
import app.pocket.data.Prefs
import app.pocket.databinding.ActivityMainBinding
import app.pocket.databinding.DialogDisplayBinding
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding
    private val vm: MainViewModel by viewModels()
    private var tab = R.id.nav_records
    private val nav: NavigationBarView by lazy { findViewById(R.id.bottom_nav) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Prefs.introDone) {
            startActivity(Intent(this, IntroActivity::class.java))
            finish()
            return
        }
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        // the payment "Add to Pocket?" notification is silently dropped without this
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {}.launch(android.Manifest.permission.POST_NOTIFICATIONS)

        findViewById<View>(R.id.content_root)?.let { v -> // landscape: keep the status-bar strip fixed so scrolled content never slides under it
            ViewCompat.setOnApplyWindowInsetsListener(v) { view, insets ->
                val i = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                view.setPadding(i.left, i.top, i.right, i.bottom)
                WindowInsetsCompat.CONSUMED
            }
        }
        b.toolbar.inflateMenu(R.menu.toolbar)
        b.toolbar.setOnMenuItemClickListener {
            if (it.itemId == R.id.action_search) { startActivity(Intent(this, SearchActivity::class.java)); true } else false
        }
        b.toolbar.setNavigationOnClickListener { b.drawer.open() }

        val h = b.headerInc
        h.btnPrev.setOnClickListener { if (tab == R.id.nav_budgets) vm.shiftBudget(-1) else vm.shift(-1) }
        h.btnNext.setOnClickListener { if (tab == R.id.nav_budgets) vm.shiftBudget(1) else vm.shift(1) }
        h.btnOptions.setOnClickListener { chooseView() }
        vm.window.observe(this) { updateHeader() }
        vm.totals.observe(this) { updateHeader() }
        vm.budgetAnchor.observe(this) { updateHeader() }
        vm.budgetTotals.observe(this) { updateHeader() }
        vm.allTime.observe(this) { updateHeader() }

        nav.setOnItemSelectedListener { show(it.itemId); true }
        b.fab.setOnClickListener { (supportFragmentManager.findFragmentById(R.id.fragment_container) as? FabHost)?.onFab() }
        b.sideNav.setNavigationItemSelectedListener {
            b.drawer.close()
            when (it.itemId) {
                R.id.side_chat -> startActivity(Intent(this, ChatActivity::class.java))
                R.id.side_preferences -> startActivity(Intent(this, SettingsActivity::class.java))
                R.id.side_backup -> startActivity(Intent(this, BackupRestoreActivity::class.java))
                R.id.side_reset -> startActivity(Intent(this, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_KIND, SettingsActivity.KIND_RESET))
                R.id.side_export -> startActivity(Intent(this, ExportActivity::class.java))
                R.id.side_help -> startActivity(Intent(this, HelpActivity::class.java))
                R.id.side_feedback -> feedback()
                R.id.side_watcher -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                else -> Toast.makeText(this, "Coming in a later phase.", Toast.LENGTH_SHORT).show()
            }
            true
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (b.drawer.isOpen) b.drawer.close()
                else if (nav.selectedItemId != R.id.nav_records) nav.selectedItemId = R.id.nav_records
                else finish()
            }
        })

        chrome(nav.selectedItemId)
        if (savedInstanceState == null) show(nav.selectedItemId)
    }

    private fun show(id: Int) {
        val f: Fragment = when (id) {
            R.id.nav_accounts -> AccountsFragment()
            R.id.nav_categories -> CategoriesFragment()
            R.id.nav_analysis -> AnalysisFragment()
            R.id.nav_budgets -> BudgetsFragment()
            else -> RecordsFragment()
        }
        supportFragmentManager.commit { replace(R.id.fragment_container, f) }
        chrome(id)
    }

    /** The header is on every tab; the + button is only on Records (Accounts/Categories have an inline Add button). */
    private fun chrome(id: Int) {
        tab = id
        b.headerInc.header.visibility = View.VISIBLE
        b.fab.visibility = if (id == R.id.nav_records) View.VISIBLE else View.GONE
        updateHeader()
    }

    private fun updateHeader() {
        val h = b.headerInc
        fun nav(prev: Int, options: Int) {
            h.btnPrev.visibility = prev; h.btnNext.visibility = prev; h.btnOptions.visibility = options
        }
        if (tab == R.id.nav_budgets) {
            val (limit, spent) = vm.budgetTotals.value!!
            nav(View.VISIBLE, View.INVISIBLE)
            h.tvPeriod.text = Period.MONTH.label(vm.budgetAnchor.value!!)
            h.tvCap1.text = "Budget"; h.tvCap2.text = "Spent"
            h.col3.visibility = View.GONE
            h.tvExpense.text = Fmt.money(limit)
            h.tvExpense.setTextColor(attrColor(com.google.android.material.R.attr.colorOnSurface))
            h.tvIncome.text = Fmt.money(spent)
            h.tvIncome.setTextColor(attrColor(if (limit.signum() > 0 && spent > limit) R.attr.pkNegative else R.attr.pkPositive))
        } else if (tab == R.id.nav_accounts || tab == R.id.nav_categories) {
            val (balance, expense, income) = vm.allTime.value!!
            nav(View.GONE, View.GONE)
            h.tvPeriod.text = "[ All Accounts ${Fmt.money(balance)} ]"
            h.tvCap1.text = "Expense so far"; h.tvCap2.text = "Income so far"
            h.col3.visibility = View.GONE
            h.tvExpense.text = Fmt.money(expense)
            h.tvExpense.setTextColor(attrColor(R.attr.pkNegative))
            h.tvIncome.text = Fmt.money(income)
            h.tvIncome.setTextColor(attrColor(R.attr.pkPositive))
        } else {
            val w = vm.window.value!!
            val t = vm.totals.value!!
            val total = t.income - t.expense
            nav(View.VISIBLE, View.VISIBLE)
            h.tvPeriod.text = w.period.label(w.anchor)
            h.tvCap1.setText(R.string.expense); h.tvCap2.setText(R.string.income)
            h.col3.visibility = View.VISIBLE
            h.tvExpense.text = Fmt.money(t.expense)
            h.tvIncome.text = Fmt.money(t.income)
            h.tvTotal.text = Fmt.money(total)
            h.tvExpense.setTextColor(attrColor(R.attr.pkNegative))
            h.tvIncome.setTextColor(attrColor(R.attr.pkPositive))
            h.tvTotal.setTextColor(attrColor(if (total.signum() < 0) R.attr.pkNegative else R.attr.pkPositive))
        }
    }

    private fun feedback() {
        MaterialAlertDialogBuilder(this).setTitle("Feedback")
            .setMessage("Having trouble using Pocket? Please check out the Help section first.\n\nFor further queries or suggestions, you can write an email. Note that some device information may be included in the email to recognize the issue.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Contact via email") { _, _ ->
                val v = packageManager.getPackageInfo(packageName, 0).versionName
                val body = "\n\n--\nPocket $v\nAndroid ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})\n${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
                try {
                    startActivity(
                        Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:"))
                            .putExtra(Intent.EXTRA_SUBJECT, "Pocket feedback").putExtra(Intent.EXTRA_TEXT, body)
                    )
                } catch (e: android.content.ActivityNotFoundException) {
                    Toast.makeText(this, "No email app found", Toast.LENGTH_SHORT).show()
                }
            }.show()
    }

    private fun chooseView() {
        val d = DialogDisplayBinding.inflate(layoutInflater)
        val periods = Period.entries
        val current = vm.window.value!!.period
        val ids = periods.map { p ->
            android.widget.RadioButton(this).apply { id = View.generateViewId(); text = p.title; isChecked = p == current }
                .also { d.rgMode.addView(it) }.id
        }
        val records = tab == R.id.nav_records
        d.tvSort.visibility = if (records) View.VISIBLE else View.GONE
        d.rgSort.visibility = if (records) View.VISIBLE else View.GONE
        d.rgSort.check(if (Prefs.oldToNew) d.rbOld.id else d.rbNew.id)
        MaterialAlertDialogBuilder(this).setTitle("Display options").setView(d.root)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("OK") { _, _ ->
                Prefs.oldToNew = d.rgSort.checkedRadioButtonId == d.rbOld.id
                val picked = periods[ids.indexOf(d.rgMode.checkedRadioButtonId).coerceAtLeast(0)]
                vm.setPeriod(picked) // also re-emits the window so lists reload with the new sort
            }.show()
    }
}
