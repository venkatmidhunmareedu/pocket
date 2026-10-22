package app.pocket.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import app.pocket.R
import app.pocket.data.EXPENSE
import app.pocket.data.Fmt
import app.pocket.data.INCOME
import app.pocket.data.Store
import app.pocket.databinding.FragmentListBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Base for the icon+title list tabs: subclasses provide rows, empty text, the FAB/footer action and the row menu. */
abstract class RowListFragment : Fragment(), FabHost {
    private var _b: FragmentListBinding? = null
    protected val b get() = _b!!
    private val vm: MainViewModel by activityViewModels()
    private lateinit var adapter: RowAdapter

    abstract val emptyIcon: Int
    abstract val emptyMessage: String
    abstract val footerText: String
    abstract fun rows(): List<Row>
    abstract fun onItem(id: Long)
    abstract fun onMenu(item: Row.Item, anchor: View)

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentListBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        adapter = RowAdapter({ onItem(it.id) }, null, { item, anchor -> onMenu(item, anchor) }, { onFab() })
        b.rv.layoutManager = LinearLayoutManager(requireContext())
        b.rv.adapter = adapter
        b.emptyIcon.setImageResource(emptyIcon)
        b.emptyText.text = emptyMessage
    }

    override fun onResume() { super.onResume(); reload() }

    protected fun reload() {
        val r = rows()
        val hasItems = r.any { it is Row.Item }
        adapter.submit(if (hasItems) r + Row.Footer(footerText) else r)
        b.empty.visibility = if (hasItems) View.GONE else View.VISIBLE
        vm.refreshAllTime()
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

class AccountsFragment : RowListFragment() {
    override val emptyIcon = R.drawable.ic_account_balance_wallet
    override val emptyMessage = "No account available. Add new accounts to manage your sources."
    override val footerText = "Add new account"

    override fun rows(): List<Row> {
        val ctx = requireContext()
        return Store.accounts().map {
            Row.Item(
                it.id, it.name, badge = it.icon,
                sub = "Initial: ${Fmt.money(it.initial)}",
                end = Fmt.money(it.balance),
                endColor = ctx.attrColor(if (it.balance.signum() < 0) R.attr.pkNegative else R.attr.pkPositive),
                more = true,
            )
        }
    }

    override fun onItem(id: Long) {
        startActivity(
            Intent(requireContext(), DetailActivity::class.java)
                .putExtra(DetailActivity.EXTRA_KIND, DetailActivity.KIND_ACCOUNT).putExtra(DetailActivity.EXTRA_ID, id)
        )
    }

    override fun onMenu(item: Row.Item, anchor: View) {
        val acc = Store.accounts().firstOrNull { it.id == item.id } ?: return
        PopupMenu(requireContext(), anchor, android.view.Gravity.END).apply {
            menu.add("Edit"); menu.add("Delete")
            setOnMenuItemClickListener {
                if (it.title == "Edit") Editors.account(hostActivity(), acc) { reload() } else Editors.deleteAccount(hostActivity(), acc) { reload() }
                true
            }
        }.show()
    }

    override fun onFab() { Editors.account(hostActivity(), null) { reload() } }
}

class CategoriesFragment : RowListFragment() {
    override val emptyIcon = R.drawable.ic_label
    override val emptyMessage = "No category available. Add some categories to record income and expenses."
    override val footerText = "Add new category"

    override fun rows(): List<Row> {
        fun items(type: Int) = Store.categories(type).map { Row.Item(it.id, it.name, badge = it.icon, more = true) }
        val inc = items(INCOME)
        val exp = items(EXPENSE)
        return buildList {
            if (inc.isNotEmpty()) { add(Row.Section("Income categories")); addAll(inc) }
            if (exp.isNotEmpty()) { add(Row.Section("Expense categories")); addAll(exp) }
        }
    }

    override fun onItem(id: Long) {
        startActivity(
            Intent(requireContext(), DetailActivity::class.java)
                .putExtra(DetailActivity.EXTRA_KIND, DetailActivity.KIND_CATEGORY).putExtra(DetailActivity.EXTRA_ID, id)
        )
    }

    override fun onMenu(item: Row.Item, anchor: View) {
        val cat = Store.categories().firstOrNull { it.id == item.id } ?: return
        PopupMenu(requireContext(), anchor, android.view.Gravity.END).apply {
            menu.add("Edit"); menu.add("Delete")
            setOnMenuItemClickListener {
                if (it.title == "Edit") Editors.category(hostActivity(), cat.type, cat) { reload() } else Editors.deleteCategory(hostActivity(), cat) { reload() }
                true
            }
        }.show()
    }

    override fun onFab() {
        val a = hostActivity()
        MaterialAlertDialogBuilder(a)
            .setItems(arrayOf("Expense category", "Income category")) { _, which ->
                Editors.category(a, if (which == 0) EXPENSE else INCOME, null) { reload() }
            }.show()
    }
}
