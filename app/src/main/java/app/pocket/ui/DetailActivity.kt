package app.pocket.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import app.pocket.R
import app.pocket.data.EXPENSE
import app.pocket.data.Fmt
import app.pocket.data.Store
import app.pocket.databinding.ActivityDetailBinding
import java.math.BigDecimal

/** Records of one account or category, with a summary and an edit action. */
class DetailActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_KIND = "kind"
        const val EXTRA_ID = "id"
        const val KIND_ACCOUNT = 0
        const val KIND_CATEGORY = 1
    }

    private lateinit var b: ActivityDetailBinding
    private lateinit var adapter: RecordsAdapter
    private val kind get() = intent.getIntExtra(EXTRA_KIND, KIND_ACCOUNT)
    private val id get() = intent.getLongExtra(EXTRA_ID, 0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.root.padForSystemBars()
        b.toolbar.setNavigationOnClickListener { finish() }
        val edit = b.toolbar.menu.add(Menu.NONE, 1, 0, "Edit").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        b.toolbar.menu.findItem(1).icon = ContextCompat.getDrawable(this, R.drawable.ic_edit)!!.mutate()
            .also { it.setTint(attrColor(androidx.appcompat.R.attr.colorPrimary)) }
        b.toolbar.setOnMenuItemClickListener { onEdit(); true }

        adapter = RecordsAdapter(
            onClick = { showRecordCard(this, it) { load() } },
            onLong = { showRecordCard(this, it) { load() } },
        )
        b.rv.layoutManager = LinearLayoutManager(this)
        b.rv.adapter = adapter
    }

    override fun onResume() { super.onResume(); load() }

    private fun onEdit() {
        if (kind == KIND_ACCOUNT) Store.accounts().firstOrNull { it.id == id }?.let { Editors.account(this, it) { load() } }
        else Store.categories().firstOrNull { it.id == id }?.let { Editors.category(this, it.type, it) { load() } }
    }

    private fun load() {
        val recs = if (kind == KIND_ACCOUNT) Store.recordsOfAccount(id) else Store.recordsOfCategory(id)
        if (kind == KIND_ACCOUNT) {
            val acc = Store.accounts().firstOrNull { it.id == id } ?: return finish()
            b.toolbar.title = acc.name
            b.tvLabel.text = "Account balance:"
            b.tvValue.text = Fmt.money(acc.balance)
            b.tvValue.setTextColor(attrColor(if (acc.balance.signum() < 0) R.attr.pkNegative else R.attr.pkPositive))
            b.tvNote.text = "Initially: ${Fmt.money(acc.initial)}"
            b.emptyText.text = "No record in this account"
        } else {
            val cat = Store.categories().firstOrNull { it.id == id } ?: return finish()
            b.toolbar.title = cat.name
            val total = recs.fold(BigDecimal.ZERO) { a, r -> a + r.amount }
            b.tvLabel.text = "Records: All time"
            b.tvValue.text = Fmt.money(total)
            b.tvValue.setTextColor(attrColor(if (cat.type == EXPENSE) R.attr.pkNegative else R.attr.pkPositive))
            b.tvNote.text = "${recs.size} record${if (recs.size == 1) "" else "s"}"
            b.emptyText.text = "No record in this category"
        }
        adapter.submit(groupByDay(recs), Store.accounts().associateBy { it.id }, Store.categories().associateBy { it.id })
        b.emptyText.visibility = if (recs.isEmpty()) View.VISIBLE else View.GONE
    }
}
