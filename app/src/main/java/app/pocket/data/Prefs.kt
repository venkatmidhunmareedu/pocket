package app.pocket.data

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import java.util.Currency
import java.util.Locale

/** Keys match the preference XML so PreferenceFragmentCompat and the rest of the app share one file. */
object Prefs {
    const val FILE = "pocket_prefs"
    const val KEY_UI_MODE = "key_ui_mode"
    const val KEY_THEME = "key_theme"
    const val KEY_CURRENCY = "key_currency_sign"
    const val KEY_POSITION = "key_currency_position"
    const val KEY_DECIMALS = "key_decimal_place"
    const val KEY_NOTES = "key_note_visibility"
    const val KEY_REMINDER = "key_reminder_on"
    private const val KEY_PERIOD = "period"
    private const val KEY_BACKUP_DIR = "backup_dir"
    private const val KEY_WATCHED = "watched_apps"
    private const val KEY_INTRO = "intro_done"
    private const val KEY_PASS = "pass_hash"

    // PhonePe, Google Pay, Amazon, Paytm, BHIM
    private val DEFAULT_WATCHED = setOf(
        "com.phonepe.app", "com.google.android.apps.nbu.paisa.user",
        "in.amazon.mShop.android.shopping", "net.one97.paytm", "in.org.npci.upiapp",
    )

    private lateinit var p: SharedPreferences

    fun init(ctx: Context) {
        if (!::p.isInitialized) p = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    }

    val currencySymbol: String
        get() {
            val code = p.getString(KEY_CURRENCY, "INR")!!
            return runCatching { Currency.getInstance(code).getSymbol(Locale.US) }.getOrDefault(code)
        }
    /** Style resource of the chosen colour palette (light/dark variants are picked by the system night qualifier). */
    val themeRes: Int
        get() = when (p.getString(KEY_THEME, "original")) {
            "classy" -> app.pocket.R.style.Theme_Pocket_Classy
            "darker" -> app.pocket.R.style.Theme_Pocket_Darker
            "nature" -> app.pocket.R.style.Theme_Pocket_Nature
            "rosy" -> app.pocket.R.style.Theme_Pocket_Rosy
            "sky" -> app.pocket.R.style.Theme_Pocket_Sky
            else -> app.pocket.R.style.Theme_Pocket
        }

    val currencyPosition: String get() = p.getString(KEY_POSITION, "start")!!
    val decimals: Int get() = p.getString(KEY_DECIMALS, "2")!!.toIntOrNull() ?: 2
    val showNotes: Boolean get() = p.getBoolean(KEY_NOTES, true)
    val reminderOn: Boolean get() = p.getBoolean(KEY_REMINDER, false)

    val nightMode: Int
        get() = when (p.getString(KEY_UI_MODE, "s")) {
            "l" -> AppCompatDelegate.MODE_NIGHT_NO
            "d" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }

    var period: Period
        get() = Period.entries.getOrElse(p.getInt(KEY_PERIOD, Period.MONTH.ordinal)) { Period.MONTH }
        set(v) = p.edit { putInt(KEY_PERIOD, v.ordinal) }

    var backupDir: String?
        get() = p.getString(KEY_BACKUP_DIR, null)
        set(v) = p.edit { putString(KEY_BACKUP_DIR, v) }

    var watchedApps: Set<String>
        get() = p.getStringSet(KEY_WATCHED, DEFAULT_WATCHED)!!
        set(v) = p.edit { putStringSet(KEY_WATCHED, v) }

    var currencyCode: String
        get() = p.getString(KEY_CURRENCY, "INR")!!
        set(v) = p.edit { putString(KEY_CURRENCY, v) }

    var reminderEnabled: Boolean
        get() = reminderOn
        set(v) = p.edit { putBoolean(KEY_REMINDER, v) }

    var carryOver: Boolean
        get() = p.getBoolean("carry_over", false)
        set(v) = p.edit { putBoolean("carry_over", v) }

    var oldToNew: Boolean
        get() = p.getBoolean("old_to_new", false)
        set(v) = p.edit { putBoolean("old_to_new", v) }

    var introDone: Boolean
        get() = p.getBoolean(KEY_INTRO, false)
        set(v) = p.edit { putBoolean(KEY_INTRO, v) }

    /** "salt:hash" of the 4-digit passcode, or null when the lock is off. */
    var passSecret: String?
        get() = p.getString(KEY_PASS, null)
        set(v) = p.edit { if (v == null) remove(KEY_PASS) else putString(KEY_PASS, v) }

    /** Resets settings but keeps the intro flag so the onboarding does not reappear. */
    fun resetAll() { val intro = introDone; p.edit { clear() }; introDone = intro }
}
