package app.pocket.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * OpenAI-compatible chat client plus the finance summary it is given. The provider settings live in their own prefs
 * file (pocket_ai) so backup_rules.xml can keep the API key out of Android's cloud backup.
 * ponytail: the model gets a text summary (12 monthly category totals + latest 80 records) instead of tool calls; works
 * with any provider, but very old history is only visible as monthly totals.
 */
object Ai {
    private lateinit var p: SharedPreferences
    private val main = Handler(Looper.getMainLooper())

    fun init(ctx: Context) {
        if (!::p.isInitialized) p = ctx.applicationContext.getSharedPreferences("pocket_ai", Context.MODE_PRIVATE)
    }

    var baseUrl: String
        get() = p.getString("base", "")!!
        set(v) = p.edit { putString("base", v.trim().trimEnd('/')) }
    var apiKey: String
        get() = p.getString("key", "")!!
        set(v) = p.edit { putString("key", v.trim()) }
    var model: String
        get() = p.getString("model", "")!!
        set(v) = p.edit { putString("model", v.trim()) }
    val ready: Boolean get() = baseUrl.isNotBlank() && model.isNotBlank()

    private fun open(path: String, method: String, base: String = baseUrl, key: String = apiKey): HttpURLConnection =
        (URL(base.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000; readTimeout = 120_000
            setRequestProperty("Content-Type", "application/json")
            if (key.isNotBlank()) setRequestProperty("Authorization", "Bearer $key")
        }

    private fun failure(c: HttpURLConnection): String {
        val body = runCatching { c.errorStream?.bufferedReader()?.readText() }.getOrNull().orEmpty()
        val msg = runCatching { JSONObject(body).getJSONObject("error").getString("message") }.getOrNull() ?: body.take(300)
        return "HTTP ${c.responseCode}" + if (msg.isNotBlank()) ": $msg" else ""
    }

    /** Lists the provider's model ids (also serves as the "test connection" call). Callbacks run on the main thread. */
    fun models(base: String, key: String, done: (List<String>?, String?) -> Unit) {
        Thread {
            val r = runCatching {
                val c = open("/models", "GET", base, key)
                if (c.responseCode !in 200..299) throw RuntimeException(failure(c))
                val data = JSONObject(c.inputStream.bufferedReader().readText()).getJSONArray("data")
                List(data.length()) { data.getJSONObject(it).getString("id") }.sorted()
            }
            main.post { done(r.getOrNull(), r.exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName }) }
        }.start()
    }

    /**
     * Streams a reply to [history] (role to text pairs). Callbacks run on the main thread; nothing is called after the
     * returned function (cancel) is invoked.
     */
    fun stream(history: List<Pair<String, String>>, onDelta: (String) -> Unit, onDone: () -> Unit, onError: (String) -> Unit): () -> Unit {
        val cancelled = AtomicBoolean(false)
        var conn: HttpURLConnection? = null
        fun post(f: () -> Unit) = main.post { if (!cancelled.get()) f() }
        Thread {
            try {
                val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", systemPrompt()))
                history.forEach { msgs.put(JSONObject().put("role", it.first).put("content", it.second)) }
                val body = JSONObject().put("model", model).put("stream", true).put("messages", msgs).toString()
                val c = open("/chat/completions", "POST").also { conn = it; it.doOutput = true }
                c.outputStream.use { it.write(body.toByteArray()) }
                if (c.responseCode !in 200..299) { post { onError(failure(c)) }; return@Thread }
                if (c.contentType.orEmpty().contains("event-stream")) {
                    c.inputStream.bufferedReader().useLines { lines ->
                        for (line in lines) {
                            if (cancelled.get() || !line.startsWith("data:")) continue
                            val d = line.removePrefix("data:").trim()
                            if (d == "[DONE]") break
                            val delta = runCatching { JSONObject(d).getJSONArray("choices").getJSONObject(0).getJSONObject("delta").optString("content", "") }.getOrDefault("")
                            if (delta.isNotEmpty() && delta != "null") post { onDelta(delta) }
                        }
                    }
                } else { // provider ignored stream=true and sent one JSON body
                    val text = JSONObject(c.inputStream.bufferedReader().readText()).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                    post { onDelta(text) }
                }
                post { onDone() }
            } catch (e: Exception) {
                post { onError(e.message ?: e.javaClass.simpleName) }
            }
        }.start()
        return { cancelled.set(true); conn?.disconnect() }
    }

    /** Everything the model may know about the user's money, as plain text. */
    fun systemPrompt(): String {
        val cats = Store.categories().associateBy { it.id }
        val accs = Store.accounts()
        val recs = Store.allRecords()
        val day = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val monthKey = SimpleDateFormat("yyyy-MM", Locale.US)
        val sb = StringBuilder()
        sb.append("You are the assistant inside Pocket, a personal expense tracker. Answer questions about the user's own transactions using ONLY the data below. ")
        sb.append("Be concise and friendly, use short markdown (bold, bullet lists, small tables) when it helps, and never invent transactions. ")
        sb.append("Currency: ${Prefs.currencySymbol}. Now: ${day.format(Date())}.\n\nACCOUNTS (current balance):\n")
        accs.forEach { sb.append("- ${it.name}: ${it.balance.toPlainString()}\n") }
        sb.append("\nMONTHLY TOTALS BY CATEGORY (last 12 months; E=expense, I=income):\n")
        val from = Calendar.getInstance().apply { add(Calendar.MONTH, -11); set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0) }.timeInMillis
        recs.filter { it.time >= from && it.type != TRANSFER }
            .groupBy { monthKey.format(Date(it.time)) }.toSortedMap().forEach { (m, rs) ->
                sb.append("$m: ")
                sb.append(rs.groupBy { (if (it.type == EXPENSE) "E " else "I ") + (cats[it.catId]?.name ?: "?") }
                    .map { (k, v) -> "$k ${v.sumOf { it.amount }.toPlainString()}" }.sorted().joinToString("; ")).append('\n')
            }
        sb.append("\nLATEST ${minOf(80, recs.size)} RECORDS (newest first; date | type | amount | category | account | note):\n")
        recs.take(80).forEach {
            val t = when (it.type) { INCOME -> "income"; EXPENSE -> "expense"; else -> "transfer" }
            sb.append("${day.format(Date(it.time))} | $t | ${it.amount.toPlainString()} | ${cats[it.catId]?.name ?: "-"} | ${accs.firstOrNull { a -> a.id == it.accId }?.name ?: "-"} | ${it.note}\n")
        }
        return sb.toString()
    }
}
