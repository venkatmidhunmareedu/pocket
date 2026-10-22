package app.pocket.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import app.pocket.data.Ai
import app.pocket.databinding.ActivityAiSettingsBinding

/** Base URL, API key and model of the OpenAI-compatible provider used by the chat. */
class AiSettingsActivity : AppCompatActivity() {
    private lateinit var b: ActivityAiSettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityAiSettingsBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.root.padForSystemBars()
        b.toolbar.setNavigationOnClickListener { finish() }
        b.etBase.setText(Ai.baseUrl)
        b.etKey.setText(Ai.apiKey)
        b.etModel.setText(Ai.model)

        b.btnSave.setOnClickListener { save(); finish() }
        b.btnTest.setOnClickListener {
            val base = b.etBase.text.toString().trim()
            if (base.isEmpty()) return@setOnClickListener run { b.tvStatus.text = "Enter a base URL first." }
            b.tvStatus.text = "Testing…"
            Ai.models(base, b.etKey.text.toString().trim()) { ids, err ->
                if (isDestroyed) return@models
                if (ids == null) b.tvStatus.text = "Could not connect: $err"
                else {
                    if (b.etModel.text.isNullOrBlank() && ids.isNotEmpty()) b.etModel.setText(ids.first())
                    b.tvStatus.text = "Connected. ${ids.size} models: " + ids.take(8).joinToString(", ") + if (ids.size > 8) ", …" else ""
                }
            }
        }
    }

    private fun save() {
        Ai.baseUrl = b.etBase.text.toString()
        Ai.apiKey = b.etKey.text.toString()
        Ai.model = b.etModel.text.toString()
    }
}
