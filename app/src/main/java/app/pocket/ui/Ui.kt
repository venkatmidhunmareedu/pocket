package app.pocket.ui

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** Keeps content clear of the system bars and keyboard (edge-to-edge is enforced at targetSdk 35+). */
fun View.padForSystemBars() {
    val l = paddingLeft
    val t = paddingTop
    val r = paddingRight
    val bt = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val i = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
        v.setPadding(l + i.left, t + i.top, r + i.right, bt + i.bottom)
        WindowInsetsCompat.CONSUMED
    }
}
