package app.pocket.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.pocket.R
import app.pocket.data.Ai
import app.pocket.data.Chat
import app.pocket.data.EXPENSE
import app.pocket.data.Fmt
import app.pocket.data.Msg
import app.pocket.data.Period
import app.pocket.data.Store
import app.pocket.databinding.ActivityChatBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Chat with an OpenAI-compatible model about your records: streaming replies, saved chat history (drawer), rename and
 * delete, stop, copy. "Show my spending trend" is drawn locally from the database, no model call.
 */
class ChatActivity : AppCompatActivity() {
    private lateinit var b: ActivityChatBinding
    private val msgs = mutableListOf<Msg>()
    private lateinit var adapter: ChatAdapter
    private lateinit var chats: ChatListAdapter
    private var chatId = 0L
    private var cancel: (() -> Unit)? = null
    private val streaming get() = cancel != null
    private val buf = StringBuilder()

    private val trendAsk = "Show my spending trend"
    private val suggestions = listOf(trendAsk, "Where did I spend the most this month?", "Compare this month with last month", "Any unusual expenses lately?")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityChatBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.content.padForSystemBars()
        b.sheet.padForSystemBars()
        val night = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).apply { isAppearanceLightStatusBars = !night; isAppearanceLightNavigationBars = !night }

        adapter = ChatAdapter(msgs, { streaming }, onCopy = { copy(it) }, onShare = { share(it) }, onRetry = { regenerate() })
        b.rv.layoutManager = LinearLayoutManager(this)
        b.rv.adapter = adapter
        b.rv.itemAnimator = null

        chats = ChatListAdapter(
            onOpen = { open(it.id); b.drawer.close() },
            onMenu = { v, c -> chatMenu(v, c) },
        )
        b.rvChats.layoutManager = LinearLayoutManager(this)
        b.rvChats.adapter = chats
        b.etFilter.doAfterTextChanged { reloadChats() }

        b.btnMenu.setOnClickListener { reloadChats(); b.drawer.open() }
        b.btnNew.setOnClickListener { newChat() }
        b.btnBack.setOnClickListener { finish() }
        b.btnNewChat.setOnClickListener { newChat(); b.drawer.close() }
        b.btnMore.setOnClickListener { moreMenu(it) }
        b.btnPlus.setOnClickListener { plusMenu(it) }
        b.btnModel.setOnClickListener { startActivity(Intent(this, AiSettingsActivity::class.java)) }
        b.btnSearch.setOnClickListener {
            val show = b.etFilter.visibility != View.VISIBLE
            b.etFilter.visibility = if (show) View.VISIBLE else View.GONE
            if (!show) b.etFilter.setText("")
        }
        b.btnConnect.setOnClickListener { startActivity(Intent(this, AiSettingsActivity::class.java)) }
        b.btnSend.setOnClickListener { if (streaming) stop() else send(b.etMsg.text.toString()) }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (b.drawer.isOpen) b.drawer.close() else finish() }
        })

        suggestions.forEach { s ->
            val btn = layoutInflater.inflate(R.layout.item_suggestion, b.suggestions, false) as TextView
            btn.text = s
            btn.setOnClickListener { send(s) }
            b.suggestions.addView(btn)
        }
        if (savedInstanceState != null) open(savedInstanceState.getLong("chat"))
        refresh()
    }

    override fun onResume() { super.onResume(); updateEmpty() }

    override fun onSaveInstanceState(out: Bundle) { super.onSaveInstanceState(out); out.putLong("chat", chatId) }

    override fun onDestroy() {
        if (streaming) stop()
        super.onDestroy()
    }

    // ---- sending ----
    private fun send(raw: String) {
        val text = raw.trim()
        if (text.isEmpty() || streaming) return
        if (text != trendAsk && !Ai.ready) {
            Toast.makeText(this, "Set up an AI provider first", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, AiSettingsActivity::class.java))
            return
        }
        val now = System.currentTimeMillis()
        if (chatId == 0L) { chatId = now; Store.saveChat(Chat(chatId, text.take(40), now)) }
        b.etMsg.setText("")
        add("user", text)
        if (text == trendAsk) { add("chart", ""); touch(); return refresh() }
        msgs += Msg(nextId(), chatId, "assistant", "", System.currentTimeMillis())
        buf.setLength(0)
        val history = msgs.dropLast(1).filter { it.role != "chart" }.takeLast(20).map { it.role to it.text }
        cancel = Ai.stream(
            history,
            onDelta = { buf.append(it); msgs[msgs.lastIndex] = msgs.last().copy(text = buf.toString()); adapter.notifyItemChanged(msgs.lastIndex); scrollEnd() },
            onDone = { finishReply(null) },
            onError = { finishReply(it) },
        )
        touch()
        refresh()
    }

    /** Drops the last reply and asks again with the same history. */
    private fun regenerate() {
        if (streaming) return
        val last = msgs.lastOrNull() ?: return
        if (last.role != "assistant") return
        msgs.removeAt(msgs.lastIndex)
        Store.deleteMsg(last.id)
        val ask = msgs.lastOrNull { it.role == "user" } ?: return refresh()
        msgs.removeAll { it.id == ask.id }
        Store.deleteMsg(ask.id)
        send(ask.text)
    }

    private fun share(text: String) =
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))

    private fun moreMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add("Back to Pocket")
            menu.add("AI provider settings")
            if (chatId != 0L) { menu.add("Rename chat"); menu.add("Delete chat") }
            setOnMenuItemClickListener {
                val c = Store.chats().firstOrNull { c -> c.id == chatId }
                when (it.title) {
                    "Back to Pocket" -> finish()
                    "AI provider settings" -> startActivity(Intent(this@ChatActivity, AiSettingsActivity::class.java))
                    "Rename chat" -> c?.let { c -> rename(c) }
                    else -> c?.let { c -> delete(c) }
                }
                true
            }
        }.show()
    }

    private fun plusMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            suggestions.forEach { menu.add(it) }
            setOnMenuItemClickListener { send(it.title.toString()); true }
        }.show()
    }

    private fun stop() { cancel?.invoke(); finishReply(null) }

    /** Saves whatever arrived (a stopped reply keeps its partial text) and drops an empty placeholder. */
    private fun finishReply(error: String?) {
        cancel = null
        val last = msgs.lastOrNull()
        if (last != null && last.role == "assistant") {
            if (last.text.isBlank()) msgs.removeAt(msgs.lastIndex) else Store.saveMsg(last)
        }
        touch()
        refresh()
        if (error != null) Snackbar.make(b.rv, error, Snackbar.LENGTH_LONG)
            .setAction("Provider") { startActivity(Intent(this, AiSettingsActivity::class.java)) }.show()
    }

    private fun nextId() = maxOf(System.currentTimeMillis(), (msgs.lastOrNull()?.id ?: 0) + 1)

    private fun add(role: String, text: String) {
        val m = Msg(nextId(), chatId, role, text, System.currentTimeMillis())
        msgs += m
        Store.saveMsg(m)
    }

    private fun touch() {
        val c = Store.chats().firstOrNull { it.id == chatId } ?: return
        Store.saveChat(c.copy(time = System.currentTimeMillis()))
    }

    // ---- chats ----
    private fun newChat() {
        if (streaming) stop()
        chatId = 0L; msgs.clear(); b.etMsg.setText("")
        refresh()
    }

    private fun open(id: Long) {
        if (streaming) stop()
        val c = Store.chats().firstOrNull { it.id == id } ?: return newChat()
        chatId = c.id
        msgs.clear(); msgs += Store.msgs(id)
        refresh()
        scrollEnd()
    }

    private fun reloadChats() {
        val q = b.etFilter.text.toString().trim().lowercase()
        chats.submit(Store.chats().filter { q.isEmpty() || it.title.lowercase().contains(q) }, chatId)
    }

    private fun chatMenu(anchor: View, c: Chat) {
        PopupMenu(this, anchor).apply {
            menu.add("Rename"); menu.add("Delete")
            setOnMenuItemClickListener {
                if (it.title == "Rename") rename(c) else delete(c)
                true
            }
        }.show()
    }

    private fun rename(c: Chat) {
        val et = EditText(this).apply { setText(c.title); setSelection(text.length); setPadding(48, 32, 48, 16) }
        MaterialAlertDialogBuilder(this).setTitle("Rename chat").setView(et)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val t = et.text.toString().trim()
                if (t.isNotEmpty()) { Store.saveChat(c.copy(title = t)); reloadChats(); refresh() }
            }.show()
    }

    private fun delete(c: Chat) {
        MaterialAlertDialogBuilder(this).setTitle("Delete chat?").setMessage("\"${c.title}\" will be removed from this phone.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                Store.deleteChat(c.id)
                if (c.id == chatId) newChat()
                reloadChats()
            }.show()
    }

    // ---- ui ----
    private fun refresh() {
        adapter.notifyDataSetChanged()
        b.btnSend.setImageResource(if (streaming) R.drawable.ic_stop else R.drawable.ic_arrow_upward)
        b.btnSend.contentDescription = if (streaming) "Stop" else "Send"
        updateEmpty()
        scrollEnd()
    }

    private fun updateEmpty() {
        b.empty.visibility = if (msgs.isEmpty()) View.VISIBLE else View.GONE
        b.btnConnect.visibility = if (Ai.ready) View.GONE else View.VISIBLE
        b.btnModel.text = Ai.model.ifBlank { "Choose model" }
        b.tvGreeting.text = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; in 17..21 -> "Good evening"; else -> "Up late?"
        }
        b.tvSub.text = if (Ai.ready) "Ask about your spending, budgets and accounts." else "Add a base URL, API key and model to start chatting."
    }

    private fun scrollEnd() { if (msgs.isNotEmpty()) b.rv.scrollToPosition(msgs.lastIndex) }

    private fun copy(text: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("reply", text))
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
    }
}

private class ChatAdapter(
    val items: List<Msg>,
    val streaming: () -> Boolean,
    val onCopy: (String) -> Unit,
    val onShare: (String) -> Unit,
    val onRetry: () -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private class Vh(v: View) : RecyclerView.ViewHolder(v)

    override fun getItemCount() = items.size
    override fun getItemViewType(position: Int) = when (items[position].role) { "user" -> 0; "chart" -> 2; else -> 1 }

    override fun onCreateViewHolder(parent: ViewGroup, type: Int): RecyclerView.ViewHolder =
        Vh(LayoutInflater.from(parent.context).inflate(
            when (type) { 0 -> R.layout.item_chat_user; 2 -> R.layout.item_chat_chart; else -> R.layout.item_chat_ai }, parent, false,
        ))

    override fun onBindViewHolder(h: RecyclerView.ViewHolder, i: Int) {
        val m = items[i]
        val v = h.itemView
        when (m.role) {
            "user" -> v.findViewById<TextView>(R.id.tv).text = m.text
            "chart" -> {
                val (bars, note) = trend()
                v.findViewById<BarChartView>(R.id.chart).bars = bars
                v.findViewById<TextView>(R.id.tv_note).text = note
            }
            else -> {
                val live = streaming() && i == items.lastIndex
                bindBody(v.findViewById(R.id.body), m.text)
                val last = i == items.lastIndex
                v.findViewById<View>(R.id.actions).visibility = if (live) View.GONE else View.VISIBLE
                v.findViewById<View>(R.id.tv_note).visibility = if (last && !live) View.VISIBLE else View.GONE
                v.findViewById<View>(R.id.btn_retry).visibility = if (last) View.VISIBLE else View.GONE
                v.findViewById<ImageButton>(R.id.btn_copy).setOnClickListener { onCopy(m.text) }
                v.findViewById<ImageButton>(R.id.btn_share).setOnClickListener { onShare(m.text) }
                v.findViewById<ImageButton>(R.id.btn_retry).setOnClickListener { onRetry() }
            }
        }
    }

    private fun bindBody(body: LinearLayout, text: String) {
        val ctx = body.context
        val dp = ctx.resources.displayMetrics.density
        val serif = androidx.core.content.res.ResourcesCompat.getFont(ctx, R.font.newsreader)
        val ink = ctx.getColor(R.color.chat_text)
        body.removeAllViews()
        fun tv(t: CharSequence, size: Float) = TextView(ctx).apply {
            this.text = t; textSize = size; typeface = serif; setTextColor(ink); setLineSpacing(0f, 1.3f); setTextIsSelectable(true)
        }
        if (text.isEmpty()) return body.addView(tv("…", 18f))
        for (blk in Md.blocks(text)) when (blk) {
            is Md.Block.Text -> body.addView(tv(blk.text, 18f))
            is Md.Block.Table -> {
                val grid = TableLayout(ctx).apply {
                    isStretchAllColumns = true // fill the card; wider tables scroll sideways
                    showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
                    dividerDrawable = ctx.getDrawable(R.drawable.divider_chat)
                }
                blk.rows.forEachIndexed { r, row ->
                    grid.addView(TableRow(ctx).apply {
                        row.forEach { cell ->
                            addView(tv(cell, 16f).apply {
                                setPadding((14 * dp).toInt(), (10 * dp).toInt(), (14 * dp).toInt(), (10 * dp).toInt())
                                if (r == 0) setTypeface(typeface, android.graphics.Typeface.BOLD)
                            })
                        }
                    })
                }
                body.addView(HorizontalScrollView(ctx).apply {
                    isHorizontalScrollBarEnabled = false
                    isFillViewport = true
                    background = ctx.getDrawable(R.drawable.bg_table)
                    addView(grid)
                }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = (8 * dp).toInt(); bottomMargin = (8 * dp).toInt() })
            }
        }
    }

    /** Expense per month for the last 6 months, plus a one-line comparison of the latest two. */
    private fun trend(): Pair<List<Pair<String, Float>>, String> {
        val label = SimpleDateFormat("MMM", Locale.getDefault())
        val starts = (5 downTo 0).map { Period.MONTH.start(Calendar.getInstance().apply { add(Calendar.MONTH, -it) }.timeInMillis) }
        val totals = starts.map { s -> Store.records(s, Period.MONTH.end(s)).filter { it.type == EXPENSE }.fold(BigDecimal.ZERO) { a, r -> a + r.amount } }
        val bars = starts.zip(totals).map { (s, t) -> label.format(Date(s)) to t.toFloat() }
        val now = totals.last(); val prev = totals[totals.size - 2]
        val note = if (prev.signum() > 0) {
            val pct = (now - prev).multiply(BigDecimal(100)).divide(prev, 0, java.math.RoundingMode.HALF_UP)
            "${bars.last().first}: ${Fmt.money(now)}, ${if (pct.signum() >= 0) "up" else "down"} ${pct.abs()}% from ${bars[bars.size - 2].first} (${Fmt.money(prev)})."
        } else "${bars.last().first}: ${Fmt.money(now)} spent so far."
        return bars to note
    }
}

private class ChatListAdapter(val onOpen: (Chat) -> Unit, val onMenu: (View, Chat) -> Unit) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var items = emptyList<Chat>()
    private var selected = 0L

    fun submit(list: List<Chat>, current: Long) { items = list; selected = current; notifyDataSetChanged() }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, type: Int): RecyclerView.ViewHolder =
        object : RecyclerView.ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_chat_row, parent, false)) {}

    override fun onBindViewHolder(h: RecyclerView.ViewHolder, i: Int) {
        val c = items[i]
        (h.itemView as TextView).apply {
            text = c.title
            setTypeface(null, if (c.id == selected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            setOnClickListener { onOpen(c) }
            setOnLongClickListener { onMenu(this, c); true }
        }
    }
}
