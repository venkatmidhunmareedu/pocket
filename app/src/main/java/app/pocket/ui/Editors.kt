package app.pocket.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.pocket.R
import app.pocket.data.Account
import app.pocket.data.Budget
import app.pocket.data.Category
import app.pocket.data.Fmt
import app.pocket.data.Icons
import app.pocket.data.Store
import app.pocket.data.EXPENSE
import app.pocket.data.INCOME
import app.pocket.databinding.DialogEditorBinding
import app.pocket.databinding.ItemIconBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.math.BigDecimal

/** Create/edit dialogs for accounts and categories, plus the account/category chooser sheets. */
object Editors {

    private class IconAdapter(
        val ids: List<Int>,
        var selected: Int,
        val onPick: (Int) -> Unit,
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = ids.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            object : RecyclerView.ViewHolder(ItemIconBinding.inflate(LayoutInflater.from(parent.context), parent, false).root) {}

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val id = ids[position]
            val iv = holder.itemView as ImageView
            iv.setImageDrawable(Icons.badge(iv.context, id))
            val dp = iv.resources.displayMetrics.density
            val pad = ((if (id == selected) 1 else 7) * dp).toInt()
            iv.setPadding(pad, pad, pad, pad)
            iv.setOnClickListener { selected = id; onPick(id); notifyDataSetChanged() }
        }
    }

    private fun confirm(a: AppCompatActivity, msg: String, then: () -> Unit) {
        MaterialAlertDialogBuilder(a).setMessage(msg).setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ -> then() }.show()
    }

    fun deleteAccount(a: AppCompatActivity, acc: Account, onDone: () -> Unit) =
        confirm(a, "Deleting this account will also delete all records with this account. Are you sure?") {
            Store.deleteAccount(acc.id)
            Toast.makeText(a, "Account deleted.", Toast.LENGTH_SHORT).show()
            onDone()
        }

    fun deleteCategory(a: AppCompatActivity, cat: Category, onDone: () -> Unit) =
        confirm(a, "Deleting this category will also delete all records and budgets for this category. Are you sure?") {
            Store.deleteCategory(cat.id)
            Toast.makeText(a, "Category deleted.", Toast.LENGTH_SHORT).show()
            onDone()
        }

    fun account(a: AppCompatActivity, existing: Account?, onDone: () -> Unit) {
        val b = DialogEditorBinding.inflate(LayoutInflater.from(a))
        var icon = existing?.icon ?: Icons.accountIds.first()
        b.tilInitial.visibility = View.VISIBLE
        b.etName.setText(existing?.name)
        b.etInitial.setText(existing?.let { Fmt.plain(it.initial) })
        b.ivPreview.setImageDrawable(Icons.badge(a, icon))
        b.rvIcons.layoutManager = GridLayoutManager(a, 5)
        b.rvIcons.adapter = IconAdapter(Icons.accountIds, icon) { icon = it; b.ivPreview.setImageDrawable(Icons.badge(a, it)) }
        val d = MaterialAlertDialogBuilder(a)
            .setTitle(if (existing == null) "Add new account" else "Edit account")
            .setView(b.root)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
        if (existing != null) d.setNeutralButton("Delete", null)
        val dlg = d.show()
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = b.etName.text.toString().trim()
            if (name.isEmpty()) { b.etName.error = "Enter a name"; return@setOnClickListener }
            val initial = b.etInitial.text.toString().trim().ifEmpty { "0" }.toBigDecimalOrNull()
            if (initial == null) { b.etInitial.error = "Invalid amount"; return@setOnClickListener }
            Store.saveAccount(Account(existing?.id ?: System.currentTimeMillis(), name, initial, icon))
            Toast.makeText(a, if (existing == null) "Account added." else "Account updated.", Toast.LENGTH_SHORT).show()
            dlg.dismiss(); onDone()
        }
        existing?.let { acc ->
            dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                confirm(a, "Deleting this account will also delete all records with this account. Are you sure?") {
                    Store.deleteAccount(acc.id)
                    Toast.makeText(a, "Account deleted.", Toast.LENGTH_SHORT).show()
                    dlg.dismiss(); onDone()
                }
            }
        }
    }

    /** [type] is used only for new categories. */
    fun category(a: AppCompatActivity, type: Int, existing: Category?, onDone: () -> Unit) {
        val b = DialogEditorBinding.inflate(LayoutInflater.from(a))
        val catType = existing?.type ?: type
        var icon = existing?.icon ?: Icons.categoryIds.first()
        b.etName.setText(existing?.name)
        b.ivPreview.setImageDrawable(Icons.badge(a, icon))
        b.rvIcons.layoutManager = GridLayoutManager(a, 5)
        b.rvIcons.adapter = IconAdapter(Icons.categoryIds, icon) { icon = it; b.ivPreview.setImageDrawable(Icons.badge(a, it)) }
        val title = when {
            existing != null -> "Edit category"
            catType == INCOME -> "Add income category"
            else -> "Add expense category"
        }
        val d = MaterialAlertDialogBuilder(a).setTitle(title).setView(b.root)
            .setNegativeButton("Cancel", null).setPositiveButton("Save", null)
        if (existing != null) d.setNeutralButton("Delete", null)
        val dlg = d.show()
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = b.etName.text.toString().trim()
            if (name.isEmpty()) { b.etName.error = "Enter a name"; return@setOnClickListener }
            Store.saveCategory(Category(existing?.id ?: System.currentTimeMillis(), name, catType, icon))
            Toast.makeText(a, if (existing == null) "Category added." else "Category updated.", Toast.LENGTH_SHORT).show()
            dlg.dismiss(); onDone()
        }
        existing?.let { cat ->
            dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                confirm(a, "Deleting this category will also delete all records and budgets for this category. Are you sure?") {
                    Store.deleteCategory(cat.id)
                    Toast.makeText(a, "Category deleted.", Toast.LENGTH_SHORT).show()
                    dlg.dismiss(); onDone()
                }
            }
        }
    }

    /** Set or change a category's budget limit for [month]; offers removal when one exists. */
    fun limit(a: AppCompatActivity, cat: Category, existing: Budget?, month: Long, onDone: () -> Unit) {
        val et = android.widget.EditText(a).apply {
            hint = "Limit"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            existing?.let { setText(Fmt.plain(it.limit)) }
        }
        val box = android.widget.FrameLayout(a).apply { setPadding(64, 16, 64, 0); addView(et) }
        val d = MaterialAlertDialogBuilder(a)
            .setTitle(if (existing == null) "Set a limit" else "Change limit")
            .setMessage(cat.name)
            .setView(box)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Set", null)
        val dlg = d.show()
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val v = et.text.toString().trim().toBigDecimalOrNull()
            if (v == null || v.signum() <= 0) { et.error = "Limit cannot be zero! Set a valid amount."; return@setOnClickListener }
            Store.saveBudget(Budget(existing?.id ?: System.currentTimeMillis(), cat.id, v, month))
            Toast.makeText(a, if (existing == null) "Budget added." else "Budget updated.", Toast.LENGTH_SHORT).show()
            dlg.dismiss(); onDone()
        }
    }

    // ---- chooser sheets ----

    private fun sheet(a: AppCompatActivity, title: String, rows: List<Row>, onItem: (Long) -> Unit, onAdd: () -> Unit) {
        val sheet = BottomSheetDialog(a)
        val rv = RecyclerView(a)
        rv.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(a)
        val adapter = RowAdapter({ item ->
            sheet.dismiss()
            if (item.id == -1L) onAdd() else onItem(item.id)
        })
        adapter.submit(listOf<Row>(Row.Section(title)) + rows + addRow("Add new"))
        rv.adapter = adapter
        rv.setPadding(0, 0, 0, 24)
        sheet.setContentView(rv)
        sheet.show()
    }

    fun pickAccount(a: AppCompatActivity, title: String, onPick: (Account) -> Unit) {
        val accs = Store.accounts()
        val rows = accs.map { Row.Item(it.id, it.name, badge = it.icon, end = Fmt.money(it.balance)) }
        sheet(a, title, rows, { id -> accs.firstOrNull { it.id == id }?.let(onPick) }) {
            account(a, null) { Store.accounts().lastOrNull()?.let(onPick) }
        }
    }

    fun pickCategory(a: AppCompatActivity, type: Int, onPick: (Category) -> Unit) {
        val cats = Store.categories(type)
        val rows = cats.map { Row.Item(it.id, it.name, badge = it.icon) }
        val title = if (type == INCOME) "Select an income category" else "Select an expense category"
        sheet(a, title, rows, { id -> cats.firstOrNull { it.id == id }?.let(onPick) }) {
            category(a, if (type == INCOME) INCOME else EXPENSE, null) { Store.categories(type).lastOrNull()?.let(onPick) }
        }
    }
}
