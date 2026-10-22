package app.pocket.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.pocket.R
import app.pocket.data.Icons
import app.pocket.databinding.ItemEntryBinding
import app.pocket.databinding.ItemFooterBinding
import app.pocket.databinding.ItemSectionBinding
import com.google.android.material.color.MaterialColors

fun Context.attrColor(attr: Int): Int = MaterialColors.getColor(this, attr, Color.MAGENTA)

sealed interface Row {
    data class Section(val text: String) : Row

    /** [badge] is a category/account icon id drawn as a coloured badge; otherwise [icon] is a plain tinted glyph. */
    data class Item(
        val id: Long,
        val title: String,
        val icon: Int = 0,
        val sub: String? = null,
        val end: String? = null,
        val endColor: Int = 0,
        val tint: Int = 0,
        val badge: Int = 0,
        val more: Boolean = false,
    ) : Row

    data class Footer(val text: String) : Row
}

/** Generic icon + title list used by Accounts, Categories and the chooser sheets. */
class RowAdapter(
    private val onClick: (Row.Item) -> Unit,
    private val onLong: ((Row.Item) -> Unit)? = null,
    private val onMore: ((Row.Item, View) -> Unit)? = null,
    private val onFooter: (() -> Unit)? = null,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var rows: List<Row> = emptyList()

    fun submit(r: List<Row>) { rows = r; notifyDataSetChanged() }

    override fun getItemCount() = rows.size
    override fun getItemViewType(position: Int) = when (rows[position]) { is Row.Section -> 0; is Row.Item -> 1; is Row.Footer -> 2 }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return when (viewType) {
            0 -> object : RecyclerView.ViewHolder(ItemSectionBinding.inflate(inf, parent, false).root) {}
            2 -> FooterVH(ItemFooterBinding.inflate(inf, parent, false))
            else -> EntryVH(ItemEntryBinding.inflate(inf, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val r = rows[position]) {
            is Row.Section -> (holder.itemView as android.widget.TextView).text = r.text
            is Row.Item -> (holder as EntryVH).bind(r)
            is Row.Footer -> (holder as FooterVH).bind(r)
        }
    }

    private inner class FooterVH(val b: ItemFooterBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(r: Row.Footer) { b.btn.text = r.text; b.btn.setOnClickListener { onFooter?.invoke() } }
    }

    private inner class EntryVH(val b: ItemEntryBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(r: Row.Item) {
            val ctx = b.root.context
            val dp = ctx.resources.displayMetrics.density
            if (r.badge != 0) {
                b.ivIcon.setImageDrawable(Icons.badge(ctx, r.badge))
                b.ivIcon.imageTintList = null
                b.ivIcon.setPadding((4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt())
            } else {
                b.ivIcon.setImageResource(r.icon)
                b.ivIcon.imageTintList = ColorStateList.valueOf(if (r.tint != 0) r.tint else ctx.attrColor(androidx.appcompat.R.attr.colorPrimary))
                b.ivIcon.setPadding((11 * dp).toInt(), (11 * dp).toInt(), (11 * dp).toInt(), (11 * dp).toInt())
            }
            b.tvTitle.text = r.title
            b.tvSub.visibility = if (r.sub == null) View.GONE else View.VISIBLE
            b.tvSub.text = r.sub
            b.tvEnd.text = r.end
            if (r.endColor != 0) b.tvEnd.setTextColor(r.endColor)
            b.btnMore.visibility = if (r.more && onMore != null) View.VISIBLE else View.GONE
            b.btnMore.setOnClickListener { onMore?.invoke(r, it) }
            b.root.setOnClickListener { onClick(r) }
            b.root.setOnLongClickListener { onLong?.invoke(r); onLong != null }
        }
    }
}

fun addRow(title: String) = Row.Item(-1, title, R.drawable.ic_add)
