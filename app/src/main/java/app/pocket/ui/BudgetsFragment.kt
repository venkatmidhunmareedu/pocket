package app.pocket.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.pocket.R
import app.pocket.data.Budget
import app.pocket.data.Budgets
import app.pocket.data.Prefs
import app.pocket.data.Category
import app.pocket.data.EXPENSE
import app.pocket.data.Fmt
import app.pocket.data.Icons
import app.pocket.data.Period
import app.pocket.data.Store
import app.pocket.databinding.FragmentListBinding
import app.pocket.databinding.ItemBudgetBinding
import app.pocket.databinding.ItemDayBinding
import app.pocket.databinding.ItemEntryBinding
import app.pocket.databinding.ItemHintBinding
import app.pocket.databinding.ItemSwitchBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.math.BigDecimal
import java.math.RoundingMode

sealed interface BRow {
    data class Head(val text: String) : BRow
    data class Hint(val text: String) : BRow
    data class Bud(val b: Budget, val cat: Category?, val spent: BigDecimal, val carry: BigDecimal) : BRow
    data class Free(val cat: Category) : BRow
    data object Copy : BRow
    data object CarryToggle : BRow
}

/** Per-category monthly spending limits for the month chosen in the header. */
class BudgetsFragment : Fragment() {
    private val vm: MainViewModel by activityViewModels()
    private var _b: FragmentListBinding? = null
    private val b get() = _b!!
    private val adapter = Adapter()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentListBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        b.rv.layoutManager = LinearLayoutManager(requireContext())
        b.rv.adapter = adapter
        b.emptyIcon.setImageResource(R.drawable.ic_calculate)
        vm.budgetAnchor.observe(viewLifecycleOwner) { load() }
    }

    override fun onResume() { super.onResume(); load() }

    private fun month() = Period.MONTH.start(vm.budgetAnchor.value ?: System.currentTimeMillis())

    private fun load() {
        val m = month()
        val cats = Store.categories(EXPENSE)
        val catMap = cats.associateBy { it.id }
        val buds = Store.budgetsOf(m).filter { catMap.containsKey(it.catId) }.sortedBy { b -> cats.indexOfFirst { it.id == b.catId } }
        val spent = Store.records(m, Period.MONTH.end(m)).filter { it.type == EXPENSE }.groupBy { it.catId }
            .mapValues { (_, l) -> l.fold(BigDecimal.ZERO) { a, r -> a + r.amount } }
        val carries = buds.associate { it.id to Budgets.carry(it.catId, m) }
        val totalLimit = buds.fold(BigDecimal.ZERO) { a, x -> a + x.limit + (carries[x.id] ?: BigDecimal.ZERO) }
        val totalSpent = buds.fold(BigDecimal.ZERO) { a, x -> a + (spent[x.catId] ?: BigDecimal.ZERO) }
        vm.budgetTotals.value = totalLimit to totalSpent

        val rows = ArrayList<BRow>()
        rows += BRow.CarryToggle
        if (buds.isEmpty()) {
            rows += BRow.Head("Monthly budgets: ${Fmt.money(BigDecimal.ZERO)}")
            rows += BRow.Hint("Currently, no budget is applied for this month. Set budget-limits for this month, or copy your budget-limits from past months.")
        } else {
            rows += BRow.Head("Monthly budgets: ${Fmt.money(totalLimit)}")
            buds.forEach { rows += BRow.Bud(it, catMap[it.catId], spent[it.catId] ?: BigDecimal.ZERO, carries[it.id] ?: BigDecimal.ZERO) }
        }
        val free = cats.filter { c -> buds.none { it.catId == c.id } }
        if (cats.isEmpty()) rows += BRow.Hint("No category found. Add some categories in order to apply budget.")
        else if (free.isNotEmpty()) {
            rows += BRow.Head("Not budgeted this month")
            free.forEach { rows += BRow.Free(it) }
        }
        rows += BRow.Copy
        adapter.rows = rows
        adapter.notifyDataSetChanged()
    }

    private fun changeLimit(cat: Category, existing: Budget?) =
        Editors.limit(hostActivity(), cat, existing, month()) { load() }

    private fun removeBudget(bud: Budget) {
        MaterialAlertDialogBuilder(requireContext()).setTitle("Remove this budget?")
            .setMessage("Budget over this category will be removed for this month, are you sure?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Remove") { _, _ ->
                Store.deleteBudget(bud.id)
                Toast.makeText(requireContext(), "Budget removed.", Toast.LENGTH_SHORT).show()
                load()
            }.show()
    }

    private fun copyBudget() {
        val ctx = requireContext()
        val here = month()
        val sources = (1..24).map { Period.MONTH.shift(here, -it) }.map { Period.MONTH.start(it) }
            .filter { Store.budgetsOf(it).isNotEmpty() }
        if (sources.isEmpty()) {
            MaterialAlertDialogBuilder(ctx).setTitle("Copy budget")
                .setMessage("Please select a month that has at least one budget to copy.")
                .setPositiveButton("OK", null).show()
            return
        }
        MaterialAlertDialogBuilder(ctx).setTitle("Select a previous month you want to copy from:")
            .setItems(sources.map { Period.MONTH.label(it) }.toTypedArray()) { _, i ->
                MaterialAlertDialogBuilder(ctx).setTitle("Caution")
                    .setMessage("Copying will overwrite all previously applied budget-limits for this month.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Copy") { _, _ ->
                        Store.copyBudgets(sources[i], here)
                        Toast.makeText(ctx, "Budget copied successfully", Toast.LENGTH_SHORT).show()
                        load()
                    }.show()
            }.show()
    }

    private inner class Adapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        var rows: List<BRow> = emptyList()
        override fun getItemCount() = rows.size
        override fun getItemViewType(position: Int) = when (rows[position]) {
            is BRow.Head -> 0; is BRow.Hint -> 1; is BRow.Bud -> 2; is BRow.Free -> 3; BRow.Copy -> 4; BRow.CarryToggle -> 5
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inf = LayoutInflater.from(parent.context)
            return when (viewType) {
                0 -> object : RecyclerView.ViewHolder(ItemDayBinding.inflate(inf, parent, false).root) {}
                1 -> object : RecyclerView.ViewHolder(ItemHintBinding.inflate(inf, parent, false).root) {}
                2 -> BudVH(ItemBudgetBinding.inflate(inf, parent, false))
                5 -> object : RecyclerView.ViewHolder(ItemSwitchBinding.inflate(inf, parent, false).root) {}
                else -> EntryVH(ItemEntryBinding.inflate(inf, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val r = rows[position]) {
                is BRow.Head -> holder.itemView.findViewById<TextView>(R.id.tv_date).text = r.text
                is BRow.Hint -> (holder.itemView as TextView).text = r.text
                is BRow.Bud -> (holder as BudVH).bind(r)
                is BRow.Free -> (holder as EntryVH).bind(r.cat.name, Icons.badge(requireContext(), r.cat.icon), false, "Set budget") { changeLimit(r.cat, null) }
                BRow.Copy -> (holder as EntryVH).bind("Copy budget", androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.ic_content_copy)!!, true, null) { copyBudget() }
                BRow.CarryToggle -> holder.itemView.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.sw).also { sw ->
                    sw.setOnCheckedChangeListener(null)
                    sw.isChecked = Prefs.carryOver
                    sw.setOnCheckedChangeListener { _, on -> Prefs.carryOver = on; load() }
                }
            }
        }
    }

    private inner class EntryVH(val e: ItemEntryBinding) : RecyclerView.ViewHolder(e.root) {
        fun bind(title: String, icon: android.graphics.drawable.Drawable, tinted: Boolean, end: String?, click: () -> Unit) {
            val dp = requireContext().resources.displayMetrics.density
            e.ivIcon.setImageDrawable(icon)
            e.ivIcon.imageTintList = if (tinted) android.content.res.ColorStateList.valueOf(requireContext().attrColor(androidx.appcompat.R.attr.colorPrimary)) else null
            val pad = ((if (tinted) 11 else 4) * dp).toInt()
            e.ivIcon.setPadding(pad, pad, pad, pad)
            e.tvTitle.text = title
            e.tvEnd.text = end
            e.tvEnd.setTextColor(requireContext().attrColor(androidx.appcompat.R.attr.colorPrimary))
            e.root.setOnClickListener { click() }
        }
    }

    private inner class BudVH(val v: ItemBudgetBinding) : RecyclerView.ViewHolder(v.root) {
        fun bind(r: BRow.Bud) {
            val ctx = requireContext()
            val limit = r.b.limit + r.carry
            val over = r.spent > limit
            val ratio = if (limit.signum() == 0) BigDecimal.ONE else r.spent.divide(limit, 4, RoundingMode.HALF_UP)
            v.ivIcon.setImageDrawable(Icons.badge(ctx, r.cat?.icon ?: 0))
            v.tvTitle.text = r.cat?.name ?: "Uncategorized"
            v.progress.setProgress(minOf(1000, (ratio.toFloat() * 1000).toInt()), false)
            v.progress.setIndicatorColor(ctx.attrColor(if (over) R.attr.pkNegative else androidx.appcompat.R.attr.colorPrimary))
            v.tvSpent.text = "Spent: ${Fmt.money(r.spent)}"
            v.tvLimit.text = "Limit: ${Fmt.money(limit)}"
            val expired = r.b.time < Period.MONTH.start(System.currentTimeMillis())
            v.tvStatus.text = (if (r.carry.signum() > 0) "Carry over: ${Fmt.money(r.carry)}   " else "") +
                (if (over) "*Limit exceeded" else "Remaining: ${Fmt.money(limit - r.spent)}") +
                if (expired) "   *Budget expired" else ""
            v.tvStatus.setTextColor(ctx.attrColor(if (over) R.attr.pkNegative else R.attr.pkDisabled))
            v.root.setOnClickListener { r.cat?.let { changeLimit(it, r.b) } }
            v.btnOption.setOnClickListener { anchor ->
                PopupMenu(ctx, anchor, android.view.Gravity.END).apply {
                    menu.add("Change limit"); menu.add("Remove budget")
                    setOnMenuItemClickListener {
                        if (it.title == "Change limit") r.cat?.let { c -> changeLimit(c, r.b) } else removeBudget(r.b)
                        true
                    }
                }.show()
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
