package app.pocket.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import app.pocket.data.Store
import app.pocket.databinding.ActivitySearchBinding

class SearchActivity : AppCompatActivity() {
    private lateinit var b: ActivitySearchBinding
    private lateinit var adapter: RecordsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.root.padForSystemBars()

        adapter = RecordsAdapter(
            onClick = { showRecordCard(this, it) { search() } },
            onLong = { showRecordCard(this, it) { search() } },
        )
        b.rv.layoutManager = LinearLayoutManager(this)
        b.rv.adapter = adapter
        b.btnBack.setOnClickListener { finish() }
        b.etQuery.doAfterTextChanged { search() }
    }

    override fun onResume() { super.onResume(); search() }

    private fun search() {
        val q = b.etQuery.text.toString()
        val res = Store.search(q)
        adapter.submit(groupByDay(res), Store.accounts().associateBy { it.id }, Store.categories().associateBy { it.id })
        b.tvHint.visibility = View.VISIBLE
        b.tvHint.text = when {
            q.isBlank() -> "Search records by notes, category name or account name"
            res.isEmpty() -> "No match found"
            else -> "${res.size} found"
        }
    }
}
