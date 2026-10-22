package app.pocket.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import app.pocket.data.EXPENSE
import app.pocket.data.Fmt
import app.pocket.data.INCOME
import app.pocket.data.Record
import app.pocket.data.Store
import app.pocket.data.TRANSFER
import app.pocket.databinding.ActivityExportBinding
import com.google.android.material.datepicker.MaterialDatePicker
import java.io.File
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Exports records in a date range as CSV and shares the file. */
class ExportActivity : AppCompatActivity() {
    private lateinit var b: ActivityExportBinding
    private var from = 0L // start of first day
    private var to = 0L   // start of last day (inclusive)
    private val day = SimpleDateFormat("d MMM yy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityExportBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.root.padForSystemBars()
        b.toolbar.setNavigationOnClickListener { finish() }

        val c = Calendar.getInstance()
        to = startOfDay(c.timeInMillis)
        c.set(Calendar.DAY_OF_MONTH, 1)
        from = startOfDay(c.timeInMillis)

        b.btnFrom.setOnClickListener { pick(from) { from = it; if (from > to) to = from; refresh() } }
        b.btnTo.setOnClickListener { pick(to) { to = it; if (to < from) from = to; refresh() } }
        b.btnCsv.setOnClickListener { export() }
        b.btnPdf.setOnClickListener { exportPdf() }
        refresh()
    }

    private fun startOfDay(t: Long) = Calendar.getInstance().apply {
        timeInMillis = t; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun endExclusive() = Calendar.getInstance().apply { timeInMillis = to; add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis

    private fun pick(current: Long, onPick: (Long) -> Unit) {
        val l = Calendar.getInstance().apply { timeInMillis = current }
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear(); set(l[Calendar.YEAR], l[Calendar.MONTH], l[Calendar.DAY_OF_MONTH])
        }
        val p = MaterialDatePicker.Builder.datePicker().setSelection(utc.timeInMillis).build()
        p.addOnPositiveButtonClickListener { sel ->
            val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = sel }
            val local = Calendar.getInstance().apply { clear(); set(u[Calendar.YEAR], u[Calendar.MONTH], u[Calendar.DAY_OF_MONTH]) }
            onPick(local.timeInMillis)
        }
        p.show(supportFragmentManager, "date")
    }

    private fun records(): List<Record> = Store.records(from, endExclusive())

    private fun refresh() {
        b.btnFrom.text = day.format(Date(from))
        b.btnTo.text = day.format(Date(to))
        val recs = records()
        val exp = recs.filter { it.type == EXPENSE }
        val inc = recs.filter { it.type == INCOME }
        fun sum(l: List<Record>) = l.fold(BigDecimal.ZERO) { a, r -> a + r.amount }
        b.tvSummary.text = buildString {
            append("Records: ${recs.size}\n")
            append("Expense in this period: ${Fmt.money(sum(exp))} in ${exp.map { it.catId }.toSet().size} categories.\n")
            append("Income in this period: ${Fmt.money(sum(inc))} in ${inc.map { it.catId }.toSet().size} categories.")
        }
    }

    private fun share(file: File, mime: String) {
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                "Share via",
            )
        )
    }

    private fun exportPdf() {
        val recs = records().sortedBy { it.time }
        if (recs.isEmpty()) {
            Toast.makeText(this, "No records found. Choose a different date range containing at least one record.", Toast.LENGTH_LONG).show()
            return
        }
        val dir = File(cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val f = SimpleDateFormat("yyyyMMdd", Locale.US)
        val file = File(dir, "Pocket_${f.format(Date(from))}_${f.format(Date(to))}.pdf")
        PdfReport.write(file, "${day.format(Date(from))} – ${day.format(Date(to))}", recs)
        share(file, "application/pdf")
    }

    private fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""

    private fun export() {
        val recs = records().sortedBy { it.time }
        if (recs.isEmpty()) {
            Toast.makeText(this, "No records found. Choose a different date range containing at least one record.", Toast.LENGTH_LONG).show()
            return
        }
        val accs = Store.accounts().associate { it.id to it.name }
        val cats = Store.categories().associate { it.id to it.name }
        val d = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val t = SimpleDateFormat("HH:mm", Locale.US)
        val csv = StringBuilder("﻿Date,Time,Type,Category,Account,From,To,Amount,Note\n")
        for (r in recs) {
            val type = when (r.type) { EXPENSE -> "Expense"; INCOME -> "Income"; else -> "Transfer" }
            val row = listOf(
                d.format(Date(r.time)), t.format(Date(r.time)), type,
                if (r.type == TRANSFER) "" else cats[r.catId].orEmpty(),
                if (r.type == TRANSFER) "" else accs[r.accId].orEmpty(),
                if (r.type == TRANSFER) accs[r.fromId].orEmpty() else "",
                if (r.type == TRANSFER) accs[r.toId].orEmpty() else "",
                r.amount.toPlainString(), r.note,
            )
            csv.append(row.joinToString(",") { q(it) }).append('\n')
        }
        val dir = File(cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val name = "Pocket_${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(from))}_${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(to))}.csv"
        val file = File(dir, name).apply { writeText(csv.toString()) }
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/csv").putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                "Share via",
            )
        )
    }
}
