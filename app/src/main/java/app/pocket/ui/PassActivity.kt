package app.pocket.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import app.pocket.R
import app.pocket.databinding.ActivityPassBinding
import com.google.android.material.button.MaterialButton

/** ENTER unlocks the app, VERIFY only checks the current passcode, SET asks twice and saves a new one. */
class PassActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_ENTER = 0
        const val MODE_VERIFY = 1
        const val MODE_SET = 2
    }

    private lateinit var b: ActivityPassBinding
    private var mode = MODE_ENTER
    private var first: String? = null
    private val pin = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPassBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.root.padForSystemBars()
        mode = intent.getIntExtra(EXTRA_MODE, MODE_ENTER)
        setTitleFor()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (mode == MODE_ENTER) finishAffinity() else finish() }
        })
        buildKeypad()
        showDots()
    }

    private fun setTitleFor() {
        b.tvTitle.text = when {
            mode == MODE_SET && first != null -> "Re-enter Passcode to confirm"
            mode == MODE_SET -> "Set a 4-digit Passcode"
            else -> "Enter Passcode"
        }
        b.tvMsg.text = ""
    }

    private fun buildKeypad() {
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫")
        val inf = LayoutInflater.from(this)
        keys.forEachIndexed { i, k ->
            if (k.isEmpty()) return@forEachIndexed
            val btn = inf.inflate(R.layout.key_digit, b.keypad, false) as MaterialButton
            btn.text = k
            btn.layoutParams = GridLayout.LayoutParams(GridLayout.spec(i / 3, 1f), GridLayout.spec(i % 3, 1f)).apply {
                width = 0; height = 0; setMargins(8, 8, 8, 8)
            }
            btn.setOnClickListener { if (k == "⌫") backspace() else press(k[0]) }
            b.keypad.addView(btn)
        }
    }

    private fun showDots() {
        for (i in 0 until b.dots.childCount) {
            (b.dots.getChildAt(i) as ImageView).setImageResource(
                if (i < pin.length) R.drawable.ic_radio_button_checked else R.drawable.ic_radio_button_unchecked
            )
        }
    }

    private fun backspace() { if (pin.isNotEmpty()) pin.setLength(pin.length - 1); showDots() }

    private fun press(d: Char) {
        if (pin.length >= 4) return
        pin.append(d)
        showDots()
        if (pin.length == 4) handle(pin.toString())
    }

    private fun reset() { pin.setLength(0); showDots() }

    private fun handle(entered: String) {
        when (mode) {
            MODE_SET -> {
                val f = first
                if (f == null) { first = entered; reset(); setTitleFor() }
                else if (f == entered) {
                    Lock.set(entered)
                    Toast.makeText(this, "Passcode set successful.", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK); finish()
                } else {
                    Toast.makeText(this, "Passcode not saved.", Toast.LENGTH_SHORT).show()
                    first = null; reset(); setTitleFor()
                }
            }
            else -> if (Lock.check(entered)) {
                if (mode == MODE_ENTER) Lock.unlocked = true
                setResult(RESULT_OK); finish()
            } else {
                b.tvMsg.text = "Incorrect Passcode, try again"
                reset()
            }
        }
    }
}
