package com.manga.hinglish.translator

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.manga.hinglish.translator.databinding.ActivityTranslationResultBinding

class TranslationResultActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ORIGINAL = "extra_original"
        const val EXTRA_TRANSLATED = "extra_translated"
    }

    private lateinit var binding: ActivityTranslationResultBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTranslationResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val original = intent.getStringExtra(EXTRA_ORIGINAL) ?: ""
        val translated = intent.getStringExtra(EXTRA_TRANSLATED) ?: ""

        binding.tvOriginal.text = original
        binding.tvTranslated.text = translated

        binding.btnCopyOriginal.setOnClickListener {
            copyToClipboard("Original Text", original)
        }

        binding.btnCopyTranslated.setOnClickListener {
            copyToClipboard("Hinglish Translation", translated)
        }

        binding.btnCopyBoth.setOnClickListener {
            val combined = "【Original】\n$original\n\n【Hinglish】\n$translated"
            copyToClipboard("Full Translation", combined)
        }

        binding.btnClose.setOnClickListener {
            finish()
        }

        // Tap outside overlay to dismiss
        binding.root.setOnClickListener {
            finish()
        }
        binding.cardContent.setOnClickListener {
            // Consume touch so card doesn't close when tapped
        }
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(this, "Copied!", Toast.LENGTH_SHORT).show()
    }
}
