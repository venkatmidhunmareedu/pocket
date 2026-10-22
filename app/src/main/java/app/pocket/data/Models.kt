package app.pocket.data

import java.math.BigDecimal

const val INCOME = 1
const val EXPENSE = 2
const val TRANSFER = 3

/** [balance] is derived (initial + records), never stored; see Store.accounts(). */
data class Account(
    val id: Long,
    val name: String,
    val initial: BigDecimal,
    val icon: Int,
    val balance: BigDecimal = initial,
)

data class Category(val id: Long, val name: String, val type: Int, val icon: Int)

data class Budget(val id: Long, val catId: Long, val limit: BigDecimal, val time: Long)

data class Record(
    val id: Long = System.currentTimeMillis(),
    val time: Long = id,
    val type: Int = EXPENSE,
    val amount: BigDecimal = BigDecimal.ZERO,
    val note: String = "",
    val accId: Long = 0,
    val catId: Long = 0,
    val fromId: Long = 0,
    val toId: Long = 0,
)

/** An AI chat. [Msg.role] is "user", "assistant", or "chart" (a locally drawn trend card, never sent to the model). */
data class Chat(val id: Long, val title: String, val time: Long)

data class Msg(val id: Long, val chatId: Long, val role: String, val text: String, val time: Long)
