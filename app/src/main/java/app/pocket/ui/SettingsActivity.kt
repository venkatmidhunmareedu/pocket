package app.pocket.ui

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import app.pocket.R
import app.pocket.Reminder
import app.pocket.data.Prefs
import app.pocket.data.Store
import app.pocket.databinding.ActivitySettingsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Hosts either the Preferences or the Delete & Reset screen. */
class SettingsActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_KIND = "kind"
        const val KIND_PREFS = 0
        const val KIND_RESET = 1
        const val KIND_PASS = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.root.padForSystemBars()
        val kind = intent.getIntExtra(EXTRA_KIND, KIND_PREFS)
        b.toolbar.title = when (kind) { KIND_RESET -> "Delete & Reset"; KIND_PASS -> "Passcode protection"; else -> "Preferences" }
        b.toolbar.setNavigationOnClickListener { finish() }
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, when (kind) { KIND_RESET -> ResetFragment(); KIND_PASS -> PassPreferenceFragment(); else -> SettingsFragment() }).commit()
        }
    }
}

class SettingsFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            Toast.makeText(requireContext(), "Notifications are disabled in system settings. Pocket cannot show notifications until you enable them.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = Prefs.FILE
        setPreferencesFromResource(R.xml.preferences, rootKey)
        findPreference<Preference>("key_watcher_service")!!.setOnPreferenceClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); true
        }
        findPreference<Preference>("key_watched_apps")!!.setOnPreferenceClickListener { chooseWatchedApps(); true }
        findPreference<Preference>("key_pin_screen")!!.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_KIND, SettingsActivity.KIND_PASS)); true
        }
        val version = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName
        findPreference<Preference>("key_about")!!.title = "Pocket $version"
    }

    override fun onResume() {
        super.onResume()
        preferenceManager.sharedPreferences!!.registerOnSharedPreferenceChangeListener(this)
        findPreference<Preference>("key_watcher_service")!!.summary =
            if (watcherEnabled()) "On: payments in watched apps ask you for a note" else "Off: tap to turn on in Accessibility settings"
        refreshWatchedSummary()
    }

    override fun onPause() {
        super.onPause()
        preferenceManager.sharedPreferences!!.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences, key: String?) {
        when (key) {
            Prefs.KEY_UI_MODE -> AppCompatDelegate.setDefaultNightMode(Prefs.nightMode)
            Prefs.KEY_THEME -> requireActivity().recreate()
            Prefs.KEY_REMINDER -> {
                if (Prefs.reminderOn && Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                Reminder.apply(requireContext())
            }
        }
    }

    private fun watcherEnabled(): Boolean {
        val enabled = Settings.Secure.getString(requireContext().contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.contains("${requireContext().packageName}/app.pocket.PaymentWatcher")
    }

    private fun refreshWatchedSummary() {
        val n = Prefs.watchedApps.size
        findPreference<Preference>("key_watched_apps")!!.summary = "$n app${if (n == 1) "" else "s"} watched"
    }

    private fun chooseWatchedApps() {
        val pm = requireContext().packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val watched = Prefs.watchedApps
        val apps = pm.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { it.first != requireContext().packageName }
            .distinctBy { it.first }
            .sortedWith(compareByDescending<Pair<String, String>> { it.first in watched }.thenBy { it.second.lowercase() })
        val checked = BooleanArray(apps.size) { apps[it].first in watched }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Watched payment apps")
            .setMultiChoiceItems(apps.map { it.second }.toTypedArray(), checked) { _, i, on -> checked[i] = on }
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val picked = apps.indices.filter { checked[it] }.map { apps[it].first }.toSet()
                // keep watched packages that aren't launchable/installed right now
                Prefs.watchedApps = picked + watched.filter { w -> apps.none { it.first == w } }
                refreshWatchedSummary()
            }.show()
    }
}

class ResetFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.resets, rootKey)
        bind("key_delete_all_records", "All records will be deleted. Are you sure?", "Deleted successfully") { Store.deleteAllRecords() }
        bind("key_delete_all", "All records, categories, budgets and accounts will be deleted. Are you sure?", "Deleted successfully") { Store.deleteEverything() }
        bind("key_reset_all", "Everything will be deleted and app will go to its initial state. Are you sure?", "Reset successful") {
            Store.resetAll(); Prefs.resetAll(); AppCompatDelegate.setDefaultNightMode(Prefs.nightMode); Reminder.apply(requireContext())
        }
    }

    private fun bind(key: String, msg: String, done: String, action: () -> Unit) {
        findPreference<Preference>(key)!!.setOnPreferenceClickListener {
            MaterialAlertDialogBuilder(requireContext()).setTitle("Are you sure?").setMessage(msg)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete") { _, _ ->
                    action()
                    Toast.makeText(requireContext(), done, Toast.LENGTH_SHORT).show()
                }.show()
            true
        }
    }
}

class PassPreferenceFragment : PreferenceFragmentCompat() {
    private var after: (() -> Unit)? = null
    private val passResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == android.app.Activity.RESULT_OK) after?.invoke()
        after = null
        refresh()
    }

    private fun pass(mode: Int, then: () -> Unit) {
        after = then
        passResult.launch(Intent(requireContext(), PassActivity::class.java).putExtra(PassActivity.EXTRA_MODE, mode))
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pass_preferences, rootKey)
        findPreference<androidx.preference.SwitchPreferenceCompat>("key_pin_lock")!!.setOnPreferenceChangeListener { _, on ->
            if (on == true) {
                MaterialAlertDialogBuilder(requireContext()).setTitle("Caution")
                    .setMessage("Setting a 4-digit Passcode requires that you must remember the Passcode. If you forget, you cannot enter Pocket again and probably lose your data.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Set Passcode") { _, _ -> pass(PassActivity.MODE_SET) {} }.show()
            } else {
                pass(PassActivity.MODE_VERIFY) {
                    Lock.clear()
                    Toast.makeText(requireContext(), "Passcode removed", Toast.LENGTH_SHORT).show()
                }
            }
            false // the switch is updated from the real state in refresh()
        }
        findPreference<Preference>("key_change_pin")!!.setOnPreferenceClickListener {
            pass(PassActivity.MODE_VERIFY) { pass(PassActivity.MODE_SET) {} }; true
        }
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun refresh() {
        findPreference<androidx.preference.SwitchPreferenceCompat>("key_pin_lock")!!.isChecked = Lock.enabled
        findPreference<Preference>("key_change_pin")!!.isEnabled = Lock.enabled
    }
}
