package app.pocket.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.math.BigDecimal

/**
 * SQLite access. Table/column names match the MyMoney 6.6 schema so its data can be imported later.
 * ponytail: synchronous queries on the caller's thread; fine for personal-size data, move to a background
 * executor if lists get into the tens of thousands.
 */
object Store {
    /** Called after any data change (used to refresh the home-screen widget). */
    var onChange: (() -> Unit)? = null
    private var quiet = false
    private fun changed() { if (!quiet) onChange?.invoke() }

    private lateinit var helper: Helper
    private val db: SQLiteDatabase get() = helper.writableDatabase

    fun init(ctx: Context) {
        if (!::helper.isInitialized) helper = Helper(ctx.applicationContext)
    }

    private class Helper(ctx: Context) : SQLiteOpenHelper(ctx, "Pocket.db", null, 4) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("create table table_record (rec_id long primary key, rec_time long, rec_type int, rec_amount text, rec_note text, rec_acc_id long, rec_cat_id long, rec_from_id long, rec_to_id long)")
            db.execSQL("create table table_account (acc_id long primary key, acc_name text, acc_amount text, acc_initial text, acc_icon int)")
            db.execSQL("create table table_category (cat_id long primary key, cat_name text, cat_type int, cat_icon int)")
            db.execSQL("create table table_budget (bug_id long primary key, bug_cat_id long, bug_limit text, bug_time long)")
            chatTables(db)
            payeeTable(db)
            seed(db)
        }

        override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
            // v1 was the unseeded skeleton schema; seed it once if it has no accounts/categories yet.
            val empty = db.rawQuery("select (select count(*) from table_account) + (select count(*) from table_category)", null)
                .use { it.moveToFirst() && it.getInt(0) == 0 }
            if (old < 2 && empty) seed(db)
            if (old < 3) chatTables(db)
            if (old < 4) payeeTable(db)
        }

        private fun payeeTable(db: SQLiteDatabase) =
            db.execSQL("create table if not exists table_payee (payee_key text primary key, payee_cat_id long)")

        private fun chatTables(db: SQLiteDatabase) {
            db.execSQL("create table if not exists table_chat (chat_id long primary key, chat_title text, chat_time long)")
            db.execSQL("create table if not exists table_chat_msg (msg_id long primary key, msg_chat_id long, msg_role text, msg_text text, msg_time long)")
        }
    }

    private fun seed(db: SQLiteDatabase) {
        var id = System.currentTimeMillis()
        fun acc(name: String, icon: Int) = putAccount(db, Account(++id, name, BigDecimal.ZERO, icon))
        fun cat(name: String, type: Int, icon: Int) = putCategory(db, Category(++id, name, type, icon))
        acc("Cash", 301); acc("Card", 302); acc("Savings", 303)
        listOf("Food" to 5, "Shopping" to 9, "Transportation" to 19, "Bills" to 13, "Entertainment" to 4, "Health" to 6,
            "Home" to 7, "Education" to 18, "Clothing" to 3, "Telephone" to 12, "Car" to 1, "Sport" to 10,
            "Social" to 17, "Beauty" to 16, "Electronics" to 15, "Insurance" to 8, "Tax" to 11, "Baby" to 14)
            .forEach { (n, i) -> cat(n, EXPENSE, i) }
        listOf("Salary" to 107, "Awards" to 101, "Grants" to 103, "Sale" to 108, "Rental" to 106,
            "Refunds" to 105, "Coupons" to 102, "Lottery" to 104)
            .forEach { (n, i) -> cat(n, INCOME, i) }
    }

    private fun bd(s: String?) = s?.toBigDecimalOrNull() ?: BigDecimal.ZERO

    // ---- records ----
    private fun records(where: String?, args: Array<String>?): List<Record> =
        db.rawQuery(
            "select rec_id, rec_time, rec_type, rec_amount, rec_note, rec_acc_id, rec_cat_id, rec_from_id, rec_to_id " +
                "from table_record" + (where?.let { " where $it" } ?: "") + " order by rec_time desc",
            args,
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    Record(c.getLong(0), c.getLong(1), c.getInt(2), bd(c.getString(3)), c.getString(4) ?: "",
                        c.getLong(5), c.getLong(6), c.getLong(7), c.getLong(8))
                )
            }
        }

    /** from <= time < to, newest first. */
    fun records(from: Long, to: Long) = records("rec_time >= ? and rec_time < ?", arrayOf("$from", "$to"))
    fun allRecords() = records(null, null)
    fun record(id: Long) = records("rec_id = ?", arrayOf("$id")).firstOrNull()
    fun recordsOfAccount(id: Long) = records("rec_acc_id = ? or rec_from_id = ? or rec_to_id = ?", arrayOf("$id", "$id", "$id"))
    fun recordsOfCategory(id: Long) = records("rec_cat_id = ?", arrayOf("$id"))

    fun save(r: Record) {
        val v = ContentValues().apply {
            put("rec_id", r.id); put("rec_time", r.time); put("rec_type", r.type)
            put("rec_amount", r.amount.toPlainString()); put("rec_note", r.note)
            put("rec_acc_id", r.accId); put("rec_cat_id", r.catId)
            put("rec_from_id", r.fromId); put("rec_to_id", r.toId)
        }
        db.insertWithOnConflict("table_record", null, v, SQLiteDatabase.CONFLICT_REPLACE)
        changed()
    }

    fun deleteRecord(id: Long) { db.delete("table_record", "rec_id = ?", arrayOf("$id")); changed() }

    fun deleteAllRecords() { db.delete("table_record", null, null); changed() }

    /** Matches note, category name or account name (case-insensitive). */
    fun search(q: String): List<Record> {
        val needle = q.trim().lowercase()
        if (needle.isEmpty()) return emptyList()
        val accs = accounts().associate { it.id to it.name.lowercase() }
        val cats = categories().associate { it.id to it.name.lowercase() }
        return allRecords().filter {
            it.note.lowercase().contains(needle) || cats[it.catId]?.contains(needle) == true ||
                accs[it.accId]?.contains(needle) == true || accs[it.fromId]?.contains(needle) == true ||
                accs[it.toId]?.contains(needle) == true
        }
    }

    // ---- AI chats (Pocket-only tables; not part of the MyMoney schema or the .mbak backup) ----
    fun chats(): List<Chat> =
        db.rawQuery("select chat_id, chat_title, chat_time from table_chat order by chat_time desc", null).use { c ->
            buildList { while (c.moveToNext()) add(Chat(c.getLong(0), c.getString(1) ?: "", c.getLong(2))) }
        }

    fun saveChat(c: Chat) {
        val v = ContentValues().apply { put("chat_id", c.id); put("chat_title", c.title); put("chat_time", c.time) }
        db.insertWithOnConflict("table_chat", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteChat(id: Long) {
        db.delete("table_chat_msg", "msg_chat_id = ?", arrayOf("$id"))
        db.delete("table_chat", "chat_id = ?", arrayOf("$id"))
    }

    fun msgs(chatId: Long): List<Msg> =
        db.rawQuery("select msg_id, msg_chat_id, msg_role, msg_text, msg_time from table_chat_msg where msg_chat_id = ? order by msg_time, msg_id", arrayOf("$chatId")).use { c ->
            buildList { while (c.moveToNext()) add(Msg(c.getLong(0), c.getLong(1), c.getString(2) ?: "", c.getString(3) ?: "", c.getLong(4))) }
        }

    fun deleteMsg(id: Long) { db.delete("table_chat_msg", "msg_id = ?", arrayOf("$id")) }

    fun saveMsg(m: Msg) {
        val v = ContentValues().apply {
            put("msg_id", m.id); put("msg_chat_id", m.chatId); put("msg_role", m.role); put("msg_text", m.text); put("msg_time", m.time)
        }
        db.insertWithOnConflict("table_chat_msg", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // ---- learned payees: the category the user last chose for a payment app's payee name (Pocket-only table) ----
    fun payeeCategory(key: String): Long? =
        db.rawQuery("select payee_cat_id from table_payee where payee_key = ?", arrayOf(key)).use { if (it.moveToFirst()) it.getLong(0) else null }

    fun learnedPayees(): List<Pair<String, Long>> =
        db.rawQuery("select payee_key, payee_cat_id from table_payee", null).use { c -> buildList { while (c.moveToNext()) add(c.getString(0) to c.getLong(1)) } }

    fun learnPayee(key: String, catId: Long) {
        val v = ContentValues().apply { put("payee_key", key); put("payee_cat_id", catId) }
        db.insertWithOnConflict("table_payee", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // ---- accounts ----
    private fun putAccount(db: SQLiteDatabase, a: Account) {
        val v = ContentValues().apply {
            put("acc_id", a.id); put("acc_name", a.name)
            put("acc_amount", a.initial.toPlainString()) // ponytail: stored for MyMoney compatibility, real balance is derived
            put("acc_initial", a.initial.toPlainString()); put("acc_icon", a.icon)
        }
        db.insertWithOnConflict("table_account", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun saveAccount(a: Account) { putAccount(db, a); changed() }

    fun accounts(): List<Account> {
        val bal = HashMap<Long, BigDecimal>()
        fun add(id: Long, d: BigDecimal) { bal[id] = (bal[id] ?: BigDecimal.ZERO) + d }
        for (r in allRecords()) when (r.type) {
            INCOME -> add(r.accId, r.amount)
            EXPENSE -> add(r.accId, r.amount.negate())
            TRANSFER -> { add(r.fromId, r.amount.negate()); add(r.toId, r.amount) }
        }
        return db.rawQuery("select acc_id, acc_name, acc_initial, acc_icon from table_account order by acc_id", null).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val ini = bd(c.getString(2))
                    add(Account(c.getLong(0), c.getString(1) ?: "", ini, c.getInt(3), ini + (bal[c.getLong(0)] ?: BigDecimal.ZERO)))
                }
            }
        }
    }

    fun deleteAccount(id: Long) {
        db.delete("table_record", "rec_acc_id = ? or rec_from_id = ? or rec_to_id = ?", arrayOf("$id", "$id", "$id"))
        db.delete("table_account", "acc_id = ?", arrayOf("$id"))
        changed()
    }

    // ---- categories ----
    private fun putCategory(db: SQLiteDatabase, c: Category) {
        val v = ContentValues().apply {
            put("cat_id", c.id); put("cat_name", c.name); put("cat_type", c.type); put("cat_icon", c.icon)
        }
        db.insertWithOnConflict("table_category", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun saveCategory(c: Category) = putCategory(db, c)

    fun categories(type: Int? = null): List<Category> =
        db.rawQuery(
            "select cat_id, cat_name, cat_type, cat_icon from table_category" +
                (type?.let { " where cat_type = $it" } ?: "") + " order by cat_id", null,
        ).use { c ->
            buildList { while (c.moveToNext()) add(Category(c.getLong(0), c.getString(1) ?: "", c.getInt(2), c.getInt(3))) }
        }

    fun deleteCategory(id: Long) {
        db.delete("table_record", "rec_cat_id = ?", arrayOf("$id"))
        db.delete("table_payee", "payee_cat_id = ?", arrayOf("$id"))
        db.delete("table_budget", "bug_cat_id = ?", arrayOf("$id"))
        db.delete("table_category", "cat_id = ?", arrayOf("$id"))
        changed()
    }

    // ---- budgets ----
    fun budgets(): List<Budget> =
        db.rawQuery("select bug_id, bug_cat_id, bug_limit, bug_time from table_budget", null).use { c ->
            buildList { while (c.moveToNext()) add(Budget(c.getLong(0), c.getLong(1), bd(c.getString(2)), c.getLong(3))) }
        }

    fun budgetsOf(monthStart: Long): List<Budget> {
        val end = Period.MONTH.end(monthStart)
        return budgets().filter { it.time >= monthStart && it.time < end }
    }

    fun saveBudget(b: Budget) {
        val v = ContentValues().apply {
            put("bug_id", b.id); put("bug_cat_id", b.catId); put("bug_limit", b.limit.toPlainString()); put("bug_time", b.time)
        }
        db.insertWithOnConflict("table_budget", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteBudget(id: Long) { db.delete("table_budget", "bug_id = ?", arrayOf("$id")) }

    /** Replaces every budget of month [to] with copies of month [from]'s. */
    fun copyBudgets(from: Long, to: Long) {
        db.delete("table_budget", "bug_time = ?", arrayOf("$to"))
        var id = System.currentTimeMillis()
        for (b in budgetsOf(from)) saveBudget(Budget(++id, b.catId, b.limit, to))
    }

    /** Atomically replaces all data (used by restore). */
    fun replaceAll(accs: List<Account>, cats: List<Category>, recs: List<Record>, buds: List<Budget>) {
        quiet = true
        db.beginTransaction()
        try {
            deleteEverything()
            accs.forEach { putAccount(db, it) }
            cats.forEach { putCategory(db, it) }
            recs.forEach { save(it) }
            buds.forEach { saveBudget(it) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            quiet = false
            changed()
        }
    }

    /** Back to the first-run state: default accounts and categories, nothing else. */
    fun resetAll() {
        quiet = true
        db.beginTransaction()
        try {
            deleteEverything()
            db.delete("table_chat_msg", null, null); db.delete("table_chat", null, null)
            seed(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            quiet = false
            changed()
        }
    }

    fun deleteEverything() {
        for (t in listOf("table_record", "table_account", "table_category", "table_budget", "table_payee")) db.delete(t, null, null)
    }
}
