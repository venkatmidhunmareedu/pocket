package app.pocket

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import app.pocket.data.EXPENSE
import app.pocket.data.Prefs
import app.pocket.data.Store
import app.pocket.data.Suggest
import app.pocket.ui.InputActivity

/**
 * Watches the payment apps you chose. A UPI app never tells other apps "payment done", so this reads the text on
 * screen: it ARMS when it sees a payment in progress (UPI PIN entry, "Processing"/"Paying"), and FIRES when a clear
 * success message appears. Then it posts a Yes/No notification; nothing opens by itself.
 * Browsing history ("Paid to …") never fires because that wording only counts once armed.
 * ponytail: text matching is per-wording, not per-app; add the exact success text of any app that is missed.
 */
class PaymentWatcher : AccessibilityService() {
    // Wording that only appears on a finished payment.
    private val success = Regex(
        "payment (successful|completed|done)|transaction (successful|completed)|paid successfully|money sent|payment received by|(₹\\s?[\\d,.]+)\\s+paid",
        RegexOption.IGNORE_CASE,
    )
    // Wording that only appears while paying.
    private val inProgress = Regex("enter (your )?upi pin|upi pin|processing|paying|please wait|do not (press back|close)|securely", RegexOption.IGNORE_CASE)
    private val payee = Regex("""(?:paid to|paying|sent to|payment to|to:)\s*([\p{L}][\p{L}.&'-]*(?![\p{N}@])(?: [\p{L}][\p{L}.&'-]*(?![\p{N}@])){0,3})""", RegexOption.IGNORE_CASE)
    private val rupee = Regex("""₹\s?([\d,]+(?:\.\d{1,2})?)""")
    private var armedUntil = 0L
    private var armedAmount: String? = null
    private var scanning = false
    private var fired = false
    private var lastKey = ""
    private var lastAt = 0L

    private fun startScanning() {
        scanning = true
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(SCAN_CHANNEL, "Scanning", NotificationManager.IMPORTANCE_LOW))
        nm.notify(
            SCAN_ID,
            NotificationCompat.Builder(this, SCAN_CHANNEL).setSmallIcon(R.drawable.ic_receipt_long)
                .setContentTitle("Scanning for a transaction success record")
                .addAction(0, "Stop scanning", PendingIntent.getBroadcast(this, 0, Intent(this, DismissReceiver::class.java).putExtra("id", SCAN_ID).putExtra("mute", true), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
                .setOngoing(true).setTimeoutAfter(5 * 60_000).build(), // timeout in case we never see the app close
        )
    }

    private fun stopScanning() {
        scanning = false
        getSystemService(NotificationManager::class.java).cancel(SCAN_ID)
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        val pkg = e.packageName?.toString()
        if (pkg !in Prefs.watchedApps) {
            // leaving the payment app ends the "scanning" notice (keyboard / system overlays don't count as leaving)
            if ((scanning || muted) && pkg != null && pkg != "com.android.systemui" && !pkg.contains("inputmethod")) { muted = false; stopScanning() }
            return
        }
        if (muted) return // "Stop" was tapped: no scanning until the payment app is closed
        if (!scanning) startScanning()
        val now = SystemClock.elapsedRealtime()
        val text = rootInActiveWindow?.let { texts(it) }?.replace(Regex("\\s+"), " ") ?: return
        if (inProgress.containsMatchIn(text)) {
            armedUntil = now + 3 * 60_000
            rupee.find(text)?.let { armedAmount = it.groupValues[1].replace(",", "") }
        }
        // One payment = at most one distinct amount; a history list has several.
        // ponytail: a past payment's detail page looks like a fresh success screen, so it triggers too (kept on purpose: same in every app).
        val amounts = rupee.findAll(text).map { it.groupValues[1].replace(",", "") }.toSet()
        val single = success.containsMatchIn(text) && amounts.size <= 1
        if (!single) { fired = false; return }
        if (fired) return // same success screen, still showing
        val amount = amounts.firstOrNull() ?: armedAmount.takeIf { now < armedUntil }
        val key = "$pkg $amount"
        if (key == lastKey && now - lastAt < 30_000) return // the screen redrew while loading
        fired = true; lastKey = key; lastAt = now
        armedUntil = 0
        ask(amount, payee.find(text)?.groupValues?.get(1)?.trim()?.replace(Regex("^[A-Z]{1,3} (?=\\S)"), "")) // drop the avatar initials some apps print before the name
    }

    /** Posts "Add ₹x · payee · Category?" with Add / No actions. Needs notification permission (silently dropped without it). */
    private fun ask(amount: String?, payee: String?) {
        stopScanning()
        val cat = payee?.let { Suggest.category(it, Store.allRecords(), Store.categories(EXPENSE), Store.payeeCategory(Suggest.key(it)), Store.learnedPayees()) } ?: 0L
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Detected payments", NotificationManager.IMPORTANCE_HIGH))
        val catName = Store.categories().firstOrNull { it.id == cat }?.name
        val text = listOfNotNull(amount?.let { "₹$it" }, payee, catName).joinToString(" · ").ifEmpty { "Payment completed" }
        val id = (System.currentTimeMillis() / 1000).toInt() // unique per payment, so notifications stack
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val add = PendingIntent.getActivity(
            this, id,
            Intent(this, InputActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(InputActivity.EXTRA_AMOUNT, amount).putExtra(InputActivity.EXTRA_NOTE, payee)
                .putExtra(InputActivity.EXTRA_CAT, cat).putExtra(InputActivity.EXTRA_PAYEE, payee),
            flags,
        )
        val no = PendingIntent.getBroadcast(this, id, Intent(this, DismissReceiver::class.java).putExtra("id", id), flags)
        nm.notify(
            id,
            NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_receipt_long)
                .setContentTitle("Payment detected. Add to Pocket?").setContentText(text)
                .setContentIntent(add).addAction(0, "Add", add).addAction(0, "No", no).setAutoCancel(true).build(),
        )
    }

    private fun texts(n: AccessibilityNodeInfo, sb: StringBuilder = StringBuilder()): String {
        n.text?.let { sb.append(it).append(' ') }
        n.contentDescription?.let { sb.append(it).append(' ') }
        for (i in 0 until n.childCount) n.getChild(i)?.let { texts(it, sb) }
        return sb.toString()
    }

    override fun onInterrupt() {}

    companion object {
        const val CHANNEL = "payment"
        const val SCAN_CHANNEL = "scanning"
        const val SCAN_ID = 3
        @Volatile var muted = false // scanning switched off for this visit to the payment app
    }
}

/** The "No" button on a payment notification, and "Stop scanning" on the scanning one (mute). */
class DismissReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.getBooleanExtra("mute", false)) PaymentWatcher.muted = true
        ctx.getSystemService(NotificationManager::class.java).cancel(intent.getIntExtra("id", 0))
    }
}
