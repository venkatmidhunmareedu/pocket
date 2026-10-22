package app.pocket.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.pocket.R
import app.pocket.data.Account
import app.pocket.data.Category
import app.pocket.data.EXPENSE
import app.pocket.data.Fmt
import app.pocket.data.INCOME
import app.pocket.data.Icons
import app.pocket.data.Prefs
import app.pocket.data.Record
import app.pocket.data.Store
import app.pocket.data.TRANSFER
import app.pocket.databinding.FragmentListBinding
import app.pocket.databinding.ItemDayBinding
import app.pocket.databinding.ItemRecordBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface RecRow {
    data class Day(val text: String) : RecRow
    data class Rec(val r: Record) : RecRow
}

class RecordsAdapter(
    private val onClick: (Record) -> Unit,
    private val onLong: (Record) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var rows: List<RecRow> = emptyList()
    private var accs: Map<Long, Account> = emptyMap()
    private var cats: Map<Long, Category> = emptyMap()

    fun submit(rows: List<RecRow>, accs: Map<Long, Account>, cats: Map<Long, Category>) {
        this.rows = rows; this.accs = accs; this.cats = cats
        notifyDataSetChanged()
    }

    override fun getItemCount() = rows.size
    override fun getItemViewType(position: Int) = if (rows[position] is RecRow.Day) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return if (viewType == 0) DayVH(ItemDayBinding.inflate(inf, parent, false))
        else RecVH(ItemRecordBinding.inflate(inf, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val r = rows[position]) {
            is RecRow.Day -> (holder as DayVH).b.tvDate.text = r.text
            is RecRow.Rec -> (holder as RecVH).bind(r.r)
        }
    }

    private class DayVH(val b: ItemDayBinding) : RecyclerView.ViewHolder(b.root)

    private inner class RecVH(val b: ItemRecordBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(r: Record) {
            val ctx = b.root.context
            val color = ctx.attrColor(
                when (r.type) { EXPENSE -> R.attr.pkNegative; INCOME -> R.attr.pkPositive; else -> R.attr.pkNeutral }
            )
            val note = if (Prefs.showNotes && r.note.isNotBlank()) "  “ ${r.note.trim()} ”" else ""
            if (r.type == TRANSFER) {
                b.ivIcon.setImageDrawable(Icons.badge(ctx, R.drawable.ic_swap_horiz, 0xFF1565C0.toInt()))
                b.tvTitle.text = ctx.getString(R.string.transfer)
                b.ivAcc.visibility = View.GONE
                b.tvSub.text = "${accs[r.fromId]?.name ?: "?"}  ›  ${accs[r.toId]?.name ?: "?"}$note"
            } else {
                val cat = cats[r.catId]
                val acc = accs[r.accId]
                b.ivIcon.setImageDrawable(Icons.badge(ctx, cat?.icon ?: 0))
                b.tvTitle.text = cat?.name ?: "Uncategorized"
                b.ivAcc.visibility = View.VISIBLE
                b.ivAcc.setImageDrawable(Icons.badge(ctx, acc?.icon ?: 0))
                b.tvSub.text = (acc?.name ?: "?") + note
            }
            b.tvAmount.text = Fmt.money(r.amount)
            b.tvAmount.setTextColor(color)
            b.root.setOnClickListener { onClick(r) }
            b.root.setOnLongClickListener { onLong(r); true }
        }
    }
}

/** Groups records (already newest-first) under day headers. */
fun groupByDay(records: List<Record>): List<RecRow> {
    val f = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault())
    val out = ArrayList<RecRow>()
    var last = ""
    for (r in records) {
        val d = f.format(Date(r.time))
        if (d != last) { out += RecRow.Day(d); last = d }
        out += RecRow.Rec(r)
    }
    return out
}

fun Fragment.hostActivity() = requireActivity() as AppCompatActivity

/** Record actions shared by the Records tab and Search: edit / duplicate / delete. */
fun showRecordMenu(a: AppCompatActivity, r: Record, onChanged: () -> Unit) {
    MaterialAlertDialogBuilder(a).setItems(arrayOf("Edit", "Duplicate", "Delete")) { _, which ->
        when (which) {
            0 -> a.startActivity(Intent(a, InputActivity::class.java).putExtra(InputActivity.EXTRA_ID, r.id))
            1 -> {
                Toast.makeText(a, "New record prefilled with selected details—time set to now. Edit and save.", Toast.LENGTH_LONG).show()
                a.startActivity(Intent(a, InputActivity::class.java).putExtra(InputActivity.EXTRA_DUPLICATE_OF, r.id))
            }
            2 -> MaterialAlertDialogBuilder(a).setMessage("Delete this record?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete") { _, _ ->
                    Store.deleteRecord(r.id)
                    Toast.makeText(a, "Record deleted.", Toast.LENGTH_SHORT).show()
                    onChanged()
                }.show()
        }
    }.show()
}

class RecordsFragment : Fragment(), FabHost {
    private val vm: MainViewModel by activityViewModels()
    private var _b: FragmentListBinding? = null
    private val b get() = _b!!
    private lateinit var adapter: RecordsAdapter

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentListBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        adapter = RecordsAdapter(
            onClick = { showRecordCard(hostActivity(), it) { load() } },
            onLong = { showRecordCard(hostActivity(), it) { load() } },
        )
        b.rv.layoutManager = LinearLayoutManager(requireContext())
        b.rv.adapter = adapter
        vm.window.observe(viewLifecycleOwner) { load() }
    }

    override fun onResume() { super.onResume(); load() }

    private fun load() {
        val w = vm.window.value ?: return
        val recs = Store.records(w.period.start(w.anchor), w.period.end(w.anchor))
        adapter.submit(groupByDay(if (Prefs.oldToNew) recs.reversed() else recs), Store.accounts().associateBy { it.id }, Store.categories().associateBy { it.id })
        var exp = BigDecimal.ZERO
        var inc = BigDecimal.ZERO
        for (r in recs) when (r.type) { EXPENSE -> exp += r.amount; INCOME -> inc += r.amount }
        vm.totals.value = Totals(exp, inc)
        b.empty.visibility = if (recs.isEmpty()) View.VISIBLE else View.GONE
        b.emptyText.text = w.period.emptyMessage
    }

    override fun onFab() { startActivity(Intent(requireContext(), InputActivity::class.java)) }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

/** Implemented by tab fragments so the shared FAB can act on the current tab. */
interface FabHost { fun onFab() }
