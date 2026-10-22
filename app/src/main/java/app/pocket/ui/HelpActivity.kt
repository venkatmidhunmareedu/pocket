package app.pocket.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import app.pocket.databinding.ActivityHelpBinding
import app.pocket.databinding.ItemHelpBinding

/** Expandable help topics describing how Pocket works. */
class HelpActivity : AppCompatActivity() {
    private val topics = listOf(
        "Learn the basics" to "Pocket has three building blocks.\n\n● Categories label what money was for (Food, Salary…).\n● Accounts are where money lives (Cash, Card, Savings…).\n● Records are single expenses, incomes or transfers.\n\nAdd a record every time money moves and Pocket shows totals, charts and budgets for you.",
        "Add a record" to "Tap the + button on the Records tab. Choose Expense, Income or Transfer, pick the account and category, then type the amount on the calculator (+, −, ×, ÷ and = work). Add a note if you like, change the date or time at the bottom, and tap ✓ to save.",
        "Transfers" to "A transfer moves money from one account to another, for example Savings to Cash. It changes both balances but is not counted as an expense or income. The two accounts must be different.",
        "Edit, duplicate or delete a record" to "Tap a record to edit it. Long-press it for Edit, Duplicate (a new record prefilled with the same details, time set to now) or Delete.",
        "Accounts" to "An account's balance is its initial amount plus income, minus expenses, plus or minus transfers. Tap an account to see its records, and use the pencil to rename it, change its icon or initial amount. Deleting an account also deletes the records that used it.",
        "Categories" to "Expense and income categories are listed separately on the Categories tab. Tap one to see its records and edit it. Deleting a category also deletes its records and budgets.",
        "Budgets" to "Open the Budgets tab and tap Set budget next to an expense category to give it a limit for the month shown. The bar fills as you spend, and turns red when you go over. Copy budget brings limits from an earlier month. Turn on Carry over to add last month's unspent amount to this month's limit.",
        "Analysis" to "Analysis breaks the period at the top into categories (Expense overview, Income overview), shows how spending flows over time (Expense flow, Income flow) and summarises each account (Account analysis). Change the period with the controls beside the date.",
        "Backup & Restore" to "Pick a folder under Backup & Restore, then Backup now creates a .mbak file in it. Restore now lists the backups in that folder; restoring replaces everything currently in Pocket. Keep copies somewhere safe, such as your computer or cloud storage. Use a folder that only contains Pocket backups.",
        "Export a report" to "Management ▸ Export Report lets you choose a date range and share the records as a CSV file. Exported files are for spreadsheets and cannot be restored into Pocket.",
        "Passcode" to "Preferences ▸ Passcode protection sets a 4-digit passcode that Pocket asks for when you open it. If you forget it you cannot get in, so keep a backup.",
        "Payment watcher" to "Pocket can watch your payment apps (PhonePe, Google Pay, Amazon Pay, Paytm, BHIM or any you choose). After a payment succeeds it asks you to add a record with a note, and keeps asking until you save one. Turn it on in Accessibility settings and pick the apps under Preferences ▸ Watched payment apps. Pocket only reads screen text inside the apps you pick, and never stores it.",
        "Reminders" to "Remind everyday sends a notification in the morning and evening asking you to add your expenses. Turn it on in Preferences.",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val b = ActivityHelpBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.root.padForSystemBars()
        b.toolbar.setNavigationOnClickListener { finish() }
        topics.forEach { (title, body) ->
            val row = ItemHelpBinding.inflate(layoutInflater, b.list, false)
            row.tvTitle.text = title
            row.tvBody.text = body
            row.head.setOnClickListener {
                val open = row.tvBody.visibility != View.VISIBLE
                row.tvBody.visibility = if (open) View.VISIBLE else View.GONE
                row.ivArrow.rotation = if (open) 180f else 0f
            }
            b.list.addView(row.root)
        }
    }
}
