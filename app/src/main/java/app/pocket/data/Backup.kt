package app.pocket.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.math.BigDecimal

/** Pocket's own backup file (.mbak extension kept for familiarity; MyMoney's .mbak is encrypted and not readable). */
object Backup {
    class Corrupt(cause: Throwable? = null) : Exception("Backup file is corrupted", cause)

    private const val APP = "pocket"
    private const val VERSION = 1

    private fun <T> JSONArray.mapObjects(f: (JSONObject) -> T): List<T> = (0 until length()).map { f(getJSONObject(it)) }

    fun toJson(): String = JSONObject().apply {
        put("app", APP)
        put("version", VERSION)
        put("time", System.currentTimeMillis())
        put("accounts", JSONArray().also { a ->
            Store.accounts().forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("initial", it.initial.toPlainString()).put("icon", it.icon)) }
        })
        put("categories", JSONArray().also { a ->
            Store.categories().forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("type", it.type).put("icon", it.icon)) }
        })
        put("records", JSONArray().also { a ->
            Store.allRecords().forEach {
                a.put(JSONObject().put("id", it.id).put("time", it.time).put("type", it.type).put("amount", it.amount.toPlainString())
                    .put("note", it.note).put("acc", it.accId).put("cat", it.catId).put("from", it.fromId).put("to", it.toId))
            }
        })
        put("budgets", JSONArray().also { a ->
            Store.budgets().forEach { a.put(JSONObject().put("id", it.id).put("cat", it.catId).put("limit", it.limit.toPlainString()).put("time", it.time)) }
        })
    }.toString()

    /** Replaces all data with the backup's. Throws [Corrupt] (and changes nothing) if it can't be parsed. */
    fun restore(json: String) {
        try {
            val o = JSONObject(json)
            if (o.getString("app") != APP) throw Corrupt()
            val accs = o.getJSONArray("accounts").mapObjects { Account(it.getLong("id"), it.getString("name"), BigDecimal(it.getString("initial")), it.getInt("icon")) }
            val cats = o.getJSONArray("categories").mapObjects { Category(it.getLong("id"), it.getString("name"), it.getInt("type"), it.getInt("icon")) }
            val recs = o.getJSONArray("records").mapObjects {
                Record(it.getLong("id"), it.getLong("time"), it.getInt("type"), BigDecimal(it.getString("amount")), it.optString("note", ""),
                    it.getLong("acc"), it.getLong("cat"), it.getLong("from"), it.getLong("to"))
            }
            val buds = o.getJSONArray("budgets").mapObjects { Budget(it.getLong("id"), it.getLong("cat"), BigDecimal(it.getString("limit")), it.getLong("time")) }
            Store.replaceAll(accs, cats, recs, buds)
        } catch (e: JSONException) {
            throw Corrupt(e)
        } catch (e: NumberFormatException) {
            throw Corrupt(e)
        }
    }
}
