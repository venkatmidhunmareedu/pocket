package app.pocket

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import app.pocket.data.Ai
import app.pocket.data.Prefs
import app.pocket.data.Store
import app.pocket.ui.Lock

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Store.init(this)
        Ai.init(this)
        Store.onChange = { Widget.refreshAll(this) }
        AppCompatDelegate.setDefaultNightMode(Prefs.nightMode)
        Reminder.apply(this)
        Lock.install(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private val applied = java.util.WeakHashMap<Activity, Int>()

            override fun onActivityPreCreated(a: Activity, b: Bundle?) {
                if (a is androidx.appcompat.app.AppCompatActivity) { a.setTheme(Prefs.themeRes); applied[a] = Prefs.themeRes }
            }

            // a screen left behind the Preferences screen picks the new palette up when it comes back
            override fun onActivityResumed(a: Activity) {
                val t = applied[a]
                if (t != null && t != Prefs.themeRes) a.recreate()
            }

            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivityStarted(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivityStopped(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
    }
}
