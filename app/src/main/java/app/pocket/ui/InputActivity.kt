package app.pocket.ui

import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.widget.GridLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import app.pocket.R
import app.pocket.data.EXPENSE
import app.pocket.data.Fmt
import app.pocket.data.INCOME
import app.pocket.data.Icons
import app.pocket.data.Prefs
import app.pocket.data.Record
import app.pocket.data.Store
import app.pocket.data.Suggest
import app.pocket.data.TRANSFER
import app.pocket.databinding.ActivityInputBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Calendar.DAY_OF_MONTH
import java.util.Calendar.HOUR_OF_DAY
import java.util.Calendar.MINUTE
import java.util.Calendar.MONTH
import java.util.Calendar.YEAR
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Add/edit a record. Extras: EXTRA_ID (edit), EXTRA_DUPLICATE_OF, or prefill via EXTRA_TYPE/EXTRA_AMOUNT/EXTRA_NOTE.
 */
class InputActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_ID = "id"
        const val EXTRA_DUPLICATE_OF = "dup"
        const val EXTRA_TYPE = "type"
        const val EXTRA_AMOUNT = "amount"
        const val EXTRA_NOTE = "note"
        const val EXTRA_PAYEE = "payee" // payee detected by the payment watcher; the category chosen is remembered for it
        const val EXTRA_CAT = "cat" // suggested category id
        const val EXTRA_SMART = "smart" // from the widget: just finish after saving instead of opening the app
        const val EXTRA_REQUIRE_PASS = "require_pass" // from the widget: always ask for the passcode
    }

    private lateinit var b: ActivityInputBinding
    private val calc = Calc()
    private var editId = 0L
    private var type = EXPENSE
    private var recTime = System.currentTimeMillis()
    private var accId = 0L
    private var catId = 0L
    private var fromId = 0L
    private var toId = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityInputBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.root.padForSystemBars()

        if (savedInstanceState == null && intent.getBooleanExtra(EXTRA_REQUIRE_PASS, false)) Lock.unlocked = false // Lock covers us with PassActivity on start
        if (savedInstanceState != null) restore(savedInstanceState) else loadInitial()

        buildKeypad()
        b.tIncome.setOnClickListener { setType(INCOME) }
        b.tExpense.setOnClickListener { setType(EXPENSE) }
        b.tTransfer.setOnClickListener { setType(TRANSFER) }

        b.btnCancel.setOnClickListener { finish() }
        b.btnSave.setOnClickListener { save() }
        b.btnBackspace.setOnClickListener { calc.backspace(); showDisplay() }
        b.btnBackspace.setOnLongClickListener { calc.clear(); showDisplay(); true }
        b.btnA.setOnClickListener {
            Editors.pickAccount(this, if (type == TRANSFER) "Transfer from" else "Select an account") {
                if (type == TRANSFER) fromId = it.id else accId = it.id
                refresh()
            }
        }
        b.btnB.setOnClickListener {
            if (type == TRANSFER) Editors.pickAccount(this, "Transfer to") { toId = it.id; refresh() }
            else Editors.pickCategory(this, type) { catId = it.id; refresh() }
        }
        b.tvDate.setOnClickListener { pickDate() }
        b.tvTime.setOnClickListener { pickTime() }
        showDisplay()
        refresh()
    }

    private fun loadInitial() {
        val srcId = intent.getLongExtra(EXTRA_ID, 0).takeIf { it != 0L } ?: intent.getLongExtra(EXTRA_DUPLICATE_OF, 0)
        val src = if (srcId != 0L) Store.record(srcId) else null
        if (src != null) {
            type = src.type; accId = src.accId; catId = src.catId; fromId = src.fromId; toId = src.toId
            b.etNote.setText(src.note)
            calc.set(Fmt.plain(src.amount))
            if (intent.hasExtra(EXTRA_ID)) { editId = src.id; recTime = src.time }
            return
        }
        type = intent.getIntExtra(EXTRA_TYPE, EXPENSE)
        intent.getStringExtra(EXTRA_AMOUNT)?.let { calc.set(it) }
        intent.getStringExtra(EXTRA_NOTE)?.let { b.etNote.setText(it) }
        val accs = Store.accounts()
        accId = accs.firstOrNull()?.id ?: 0
        fromId = accId
        toId = accs.getOrNull(1)?.id ?: 0
        catId = intent.getLongExtra(EXTRA_CAT, 0).takeIf { id -> Store.categories(type).any { it.id == id } }
            ?: Store.categories(type).firstOrNull()?.id ?: 0
    }

    private fun setType(t: Int) {
        if (t == type) return
        type = t
        if (t != TRANSFER) catId = Store.categories(t).firstOrNull()?.id ?: 0
        refresh()
    }

    private fun refresh() {
        val accs = Store.accounts().associateBy { it.id }
        val cats = Store.categories().associateBy { it.id }
        fun set(btn: MaterialButton, name: String?, icon: Int?, hint: String) {
            btn.text = name ?: hint
            if (icon != null) {
                btn.icon = Icons.badge(this, icon)
                btn.iconTint = null
                btn.iconSize = (28 * resources.displayMetrics.density).toInt()
            } else {
                btn.setIconResource(R.drawable.ic_add)
                btn.iconTint = btn.textColors
                btn.iconSize = (24 * resources.displayMetrics.density).toInt()
            }
        }
        if (type == TRANSFER) {
            set(b.btnA, accs[fromId]?.name, accs[fromId]?.icon, "From")
            set(b.btnB, accs[toId]?.name, accs[toId]?.icon, "To")
        } else {
            set(b.btnA, accs[accId]?.name, accs[accId]?.icon, "Account")
            set(b.btnB, cats[catId]?.name, cats[catId]?.icon, "Category")
        }
        b.capA.text = if (type == TRANSFER) "From" else "Account"
        b.capB.text = if (type == TRANSFER) "To" else "Category"
        val on = ContextCompat.getColor(this, R.color.pk_on_surface)
        val off = attrColor(R.attr.pkDisabled)
        val check = ContextCompat.getDrawable(this, R.drawable.ic_check_circle)!!.mutate().also { it.setTint(attrColor(androidx.appcompat.R.attr.colorPrimary)) }
        for ((tv, t) in listOf(b.tIncome to INCOME, b.tExpense to EXPENSE, b.tTransfer to TRANSFER)) {
            val sel = t == type
            tv.setTextColor(if (sel) attrColor(com.google.android.material.R.attr.colorOnSurface) else off)
            tv.setCompoundDrawablesRelativeWithIntrinsicBounds(if (sel) check else null, null, null, null)
        }
        b.tvDate.text = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(recTime))
        b.tvTime.text = DateFormat.getTimeFormat(this).format(Date(recTime))
    }

    private fun showDisplay() {
        b.tvDisplay.text = calc.text.ifEmpty { "0" }.replace('*', '×').replace('/', '÷').replace('-', '−')
    }

    private fun buildKeypad() {
        val keys = listOf("+", "7", "8", "9", "−", "4", "5", "6", "×", "1", "2", "3", "÷", "0", ".", "=")
        val inf = LayoutInflater.from(this)
        keys.forEachIndexed { i, k ->
            val isOp = k in "+−×÷=" 
            val btn = inf.inflate(if (isOp) R.layout.key_op else R.layout.key_digit, b.keypad, false) as MaterialButton
            btn.text = k
            btn.layoutParams = GridLayout.LayoutParams(GridLayout.spec(i / 4, 1f), GridLayout.spec(i % 4, 1f)).apply {
                width = 0; height = 0; setMargins(5, 5, 5, 5)
            }
            btn.setOnClickListener {
                when (k) {
                    "." -> calc.dot()
                    "+" -> calc.op('+')
                    "−" -> calc.op('-')
                    "×" -> calc.op('*')
                    "÷" -> calc.op('/')
                    "=" -> calc.equals()
                    else -> calc.digit(k[0])
                }
                showDisplay()
            }
            b.keypad.addView(btn)
        }
    }

    private fun pickDate() {
        val c = Calendar.getInstance().apply { timeInMillis = recTime }
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { clear(); set(c[YEAR], c[MONTH], c[DAY_OF_MONTH]) }
        val p = MaterialDatePicker.Builder.datePicker().setSelection(utc.timeInMillis).build()
        p.addOnPositiveButtonClickListener { sel ->
            val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = sel }
            c.set(u[YEAR], u[MONTH], u[DAY_OF_MONTH])
            recTime = c.timeInMillis
            refresh()
        }
        p.show(supportFragmentManager, "date")
    }

    private fun pickTime() {
        val c = Calendar.getInstance().apply { timeInMillis = recTime }
        val p = MaterialTimePicker.Builder()
            .setTimeFormat(if (DateFormat.is24HourFormat(this)) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H)
            .setHour(c[HOUR_OF_DAY]).setMinute(c[MINUTE]).build()
        p.addOnPositiveButtonClickListener {
            c.set(HOUR_OF_DAY, p.hour); c.set(MINUTE, p.minute)
            recTime = c.timeInMillis
            refresh()
        }
        p.show(supportFragmentManager, "time")
    }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        out.putInt("type", type); out.putLong("time", recTime); out.putLong("edit", editId)
        out.putLong("acc", accId); out.putLong("cat", catId); out.putLong("from", fromId); out.putLong("to", toId)
        out.putString("calc", calc.text)
    }

    private fun restore(b0: Bundle) {
        type = b0.getInt("type", EXPENSE); recTime = b0.getLong("time", recTime); editId = b0.getLong("edit")
        accId = b0.getLong("acc"); catId = b0.getLong("cat"); fromId = b0.getLong("from"); toId = b0.getLong("to")
        calc.set(b0.getString("calc") ?: "")
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    private fun save() {
        val raw = calc.evaluate()
        if (raw == null) return toast("Invalid amount")
        if (raw.signum() == 0) return toast("Amount cannot be zero.")
        if (raw.signum() < 0) toast("Amount is negative, but positive value will be saved…")
        val amount = raw.abs().setScale(Prefs.decimals, RoundingMode.HALF_UP)
        if (amount.signum() == 0) return toast("Amount cannot be zero.")
        val note = b.etNote.text.toString().trim()
        if (type == TRANSFER) {
            if (fromId == 0L || toId == 0L) return toast("Transfer accounts not selected")
            if (fromId == toId) return toast("Transfer accounts are same, choose different accounts")
        } else {
            if (accId == 0L) return toast("No account selected")
            if (catId == 0L) return toast("No category selected")
        }
        val id = if (editId != 0L) editId else System.currentTimeMillis()
        val rec = if (type == TRANSFER) Record(id, recTime, type, amount, note, 0, 0, fromId, toId)
        else Record(id, recTime, type, amount, note, accId, catId, 0, 0)
        Store.save(rec)
        if (type == EXPENSE) intent.getStringExtra(EXTRA_PAYEE)?.let { Suggest.key(it) }?.takeIf { it.isNotEmpty() }?.let { Store.learnPayee(it, catId) }
        toast(if (editId != 0L) "Record updated." else "Record added.")
        if (!intent.getBooleanExtra(EXTRA_SMART, true)) startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
