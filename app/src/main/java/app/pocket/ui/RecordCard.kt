package app.pocket.ui

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import app.pocket.R
import app.pocket.data.EXPENSE
import app.pocket.data.Fmt
import app.pocket.data.INCOME
import app.pocket.data.Icons
import app.pocket.data.Record
import app.pocket.data.Store
import app.pocket.data.TRANSFER
import app.pocket.databinding.DialogRecordBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Read-only detail card shown when a record is tapped; Edit/Duplicate/Delete live here, like MyMoney. */
fun showRecordCard(a: AppCompatActivity, r: Record, onChanged: () -> Unit) {
    val b = DialogRecordBinding.inflate(LayoutInflater.from(a))
    val dlg = Dialog(a)
    dlg.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
    dlg.setContentView(b.root)

    val color = a.attrColor(when (r.type) { EXPENSE -> R.attr.pkNegative; INCOME -> R.attr.pkPositive; else -> R.attr.pkNeutral })
    // darken the type colour slightly so the light text stays readable on the pastel dark-mode tones
    val bg = GradientDrawable().apply {
        setColor(Color.rgb((Color.red(color) * 0.62f).toInt(), (Color.green(color) * 0.62f).toInt(), (Color.blue(color) * 0.62f).toInt()))
        cornerRadius = 28f * a.resources.displayMetrics.density / 2
    }
    b.card.background = bg

    val cats = Store.categories().associateBy { it.id }
    val accs = Store.accounts().associateBy { it.id }
    b.tvTitle.text = if (r.type == TRANSFER) "Transfer" else cats[r.catId]?.name ?: "Uncategorized"
    b.tvAmount.text = Fmt.money(r.amount)
    b.tvTime.text = SimpleDateFormat("MMM d, yyyy\nh:mm a", Locale.getDefault()).format(Date(r.time))

    fun chip(tv: TextView, name: String?, icon: Int?) {
        tv.text = name ?: "—"
        if (icon != null) {
            val d = Icons.badge(a, icon)
            val s = (28 * a.resources.displayMetrics.density).toInt()
            d.setBounds(0, 0, s, s)
            tv.setCompoundDrawablesRelative(d, null, null, null)
        }
    }
    if (r.type == TRANSFER) {
        b.tvALabel.text = "From:"; b.tvBLabel.text = "To:"
        chip(b.tvA, accs[r.fromId]?.name, accs[r.fromId]?.icon)
        chip(b.tvB, accs[r.toId]?.name, accs[r.toId]?.icon)
    } else {
        b.tvALabel.text = "Account:"; b.tvBLabel.text = "Category:"
        chip(b.tvA, accs[r.accId]?.name, accs[r.accId]?.icon)
        chip(b.tvB, cats[r.catId]?.name, cats[r.catId]?.icon)
    }
    if (r.note.isBlank()) { b.tvNote.text = "No notes"; b.tvNote.alpha = 0.5f } else b.tvNote.text = r.note

    b.btnClose.setOnClickListener { dlg.dismiss() }
    b.btnEdit.setOnClickListener {
        dlg.dismiss()
        a.startActivity(Intent(a, InputActivity::class.java).putExtra(InputActivity.EXTRA_ID, r.id))
    }
    b.btnDuplicate.setOnClickListener {
        dlg.dismiss()
        Toast.makeText(a, "New record prefilled with selected details—time set to now. Edit and save.", Toast.LENGTH_LONG).show()
        a.startActivity(Intent(a, InputActivity::class.java).putExtra(InputActivity.EXTRA_DUPLICATE_OF, r.id))
    }
    b.btnDelete.setOnClickListener {
        MaterialAlertDialogBuilder(a).setTitle("Delete this record?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                Store.deleteRecord(r.id)
                Toast.makeText(a, "Record deleted.", Toast.LENGTH_SHORT).show()
                dlg.dismiss()
                onChanged()
            }.show()
    }
    dlg.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    dlg.window?.setLayout((a.resources.displayMetrics.widthPixels * 0.92f).toInt().coerceAtMost((460 * a.resources.displayMetrics.density).toInt()), ViewGroup.LayoutParams.WRAP_CONTENT)
    dlg.show()
}
