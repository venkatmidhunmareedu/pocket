package app.pocket.ui

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BulletSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan

/**
 * Tiny markdown renderer for chat replies: headings, bullets, **bold**, *italic*, `code`, ``` fences. [blocks] splits a
 * reply into text and tables so tables can be drawn as real grids. ponytail: no links, images or nested lists; add a
 * library (Markwon) if replies need them.
 */
object Md {
    sealed class Block {
        class Text(val text: CharSequence) : Block()
        class Table(val rows: List<List<CharSequence>>) : Block() // rows[0] is the header
    }

    fun blocks(src: String): List<Block> {
        val lines = src.trimEnd().split("\n")
        val out = mutableListOf<Block>()
        val buf = mutableListOf<String>()
        fun flush() { if (buf.any { it.isNotBlank() }) out += Block.Text(render(buf.joinToString("\n"))); buf.clear() }
        var i = 0
        while (i < lines.size) {
            if (lines[i].contains('|') && i + 1 < lines.size && lines[i + 1].contains('-') && tableRule.matches(lines[i + 1])) {
                flush()
                val rows = mutableListOf(cells(lines[i]))
                i += 2
                while (i < lines.size && lines[i].contains('|') && lines[i].isNotBlank()) rows += cells(lines[i++])
                out += Block.Table(rows)
            } else buf += lines[i++]
        }
        flush()
        return out
    }

    private fun cells(line: String): List<CharSequence> =
        line.trim().trim('|').split("|").map { SpannableStringBuilder().also { sb -> append(sb, it.trim()) } }

    private val inline = Regex("""\*\*(.+?)\*\*|`([^`]+)`|\*([^*\s][^*]*?)\*""")
    private val heading = Regex("""^\s*#{1,6}\s+(.*)""")
    private val bullet = Regex("""^(\s*)[-*]\s+(.*)""")
    private val tableRule = Regex("""^\s*\|?[\s:|-]*-[\s:|-]*\|?\s*$""")

    fun render(src: String): CharSequence {
        val out = SpannableStringBuilder()
        var code = false
        for (raw in src.trimEnd().split("\n")) {
            if (raw.trimStart().startsWith("```")) { code = !code; continue }
            val start = out.length
            if (code) {
                out.append(raw).append('\n')
                out.setSpan(TypefaceSpan("monospace"), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                continue
            }
            val h = heading.find(raw)
            val b = bullet.find(raw)
            when {
                h != null -> {
                    append(out, h.groupValues[1])
                    out.setSpan(StyleSpan(Typeface.BOLD), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    out.setSpan(RelativeSizeSpan(1.15f), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                b != null -> { // hanging bullet: wrapped lines line up under the text, not under the dot
                    append(out, b.groupValues[2])
                    out.setSpan(BulletSpan(36, 0xFF9C9A92.toInt(), 5), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                else -> append(out, raw)
            }
            out.append('\n')
        }
        while (out.isNotEmpty() && out.last() == '\n') out.delete(out.length - 1, out.length)
        return out
    }

    private fun append(out: SpannableStringBuilder, line: String) {
        var at = 0
        for (m in inline.findAll(line)) {
            out.append(line, at, m.range.first)
            val s = out.length
            when {
                m.groups[1] != null -> { out.append(m.groupValues[1]); out.setSpan(StyleSpan(Typeface.BOLD), s, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
                m.groups[2] != null -> { out.append(m.groupValues[2]); out.setSpan(TypefaceSpan("monospace"), s, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
                else -> { out.append(m.groupValues[3]); out.setSpan(StyleSpan(Typeface.ITALIC), s, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
            }
            at = m.range.last + 1
        }
        out.append(line, at, line.length)
    }
}
