package app.pocket

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.pocket.data.Prefs
import app.pocket.ui.MainActivity
import java.util.Calendar

/** Twice-daily "add your expenses" reminder (about 10:00 and 22:00), like the reference app. */
object Reminder {
    const val ACTION = "app.pocket.POST_REMINDER"
    private const val CHANNEL = "reminder"
    private const val HALF_DAY = 12L * 60 * 60 * 1000

    private fun pending(ctx: Context) = PendingIntent.getBroadcast(
        ctx, 101, Intent(ctx, ReminderReceiver::class.java).setAction(ACTION),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun apply(ctx: Context) = if (Prefs.reminderOn) schedule(ctx) else cancel(ctx)

    private fun schedule(ctx: Context) {
        val c = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 10); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        while (c.timeInMillis <= System.currentTimeMillis()) c.add(Calendar.HOUR_OF_DAY, 12)
        // ponytail: inexact repeating (Doze may shift it by minutes); use exact alarms only if that matters.
        ctx.getSystemService(AlarmManager::class.java).setInexactRepeating(AlarmManager.RTC, c.timeInMillis, HALF_DAY, pending(ctx))
    }

    private fun cancel(ctx: Context) = ctx.getSystemService(AlarmManager::class.java).cancel(pending(ctx))

    fun notify(ctx: Context) {
        if (!Prefs.reminderOn) return
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(ctx, 25, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        nm.notify(
            1,
            NotificationCompat.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_receipt_long)
                .setContentTitle("Add expenses").setContentText("Remember to add your expenses today")
                .setContentIntent(open).setAutoCancel(true).build(),
        )
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            Reminder.ACTION -> Reminder.notify(ctx)
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> Reminder.apply(ctx)
        }
    }
}
