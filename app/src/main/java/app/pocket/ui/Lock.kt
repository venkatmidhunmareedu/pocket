package app.pocket.ui

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import app.pocket.data.Prefs
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * 4-digit passcode lock. The app locks a few seconds after the last activity stops, and any activity that starts
 * while locked is covered by PassActivity. ponytail: 4 digits is brute-forceable by someone holding the phone;
 * add attempt throttling if that matters.
 */
object Lock {
    private const val RELOCK_DELAY_MS = 3000L

    @Volatile var unlocked = false
    val enabled: Boolean get() = Prefs.passSecret != null

    private fun hash(salt: String, pin: String) =
        MessageDigest.getInstance("SHA-256").digest((salt + pin).toByteArray()).joinToString("") { "%02x".format(it) }

    fun set(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        Prefs.passSecret = "$salt:${hash(salt, pin)}"
        unlocked = true // the user just proved they know it
    }

    fun check(pin: String): Boolean {
        val (salt, h) = Prefs.passSecret?.split(":") ?: return true
        return hash(salt, pin) == h
    }

    fun clear() { Prefs.passSecret = null }

    fun install(app: Application) {
        val handler = Handler(Looper.getMainLooper())
        var started = 0
        val relock = Runnable { unlocked = false }
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(a: Activity, b: Bundle?) {
                a.setRecentsScreenshotEnabled(!enabled) // hide content in the recents screen while a passcode is set
            }

            override fun onActivityStarted(a: Activity) {
                started++
                handler.removeCallbacks(relock)
                if (enabled && !unlocked && a !is PassActivity) a.startActivity(Intent(a, PassActivity::class.java))
            }

            override fun onActivityStopped(a: Activity) {
                if (--started <= 0) handler.postDelayed(relock, RELOCK_DELAY_MS)
            }

            override fun onActivityResumed(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
    }
}
