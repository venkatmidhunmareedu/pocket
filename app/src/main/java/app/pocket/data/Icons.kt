package app.pocket.data

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import app.pocket.R
import app.pocket.ui.BadgeDrawable

/** Icon ids follow the MyMoney scheme (1-19 expense, 101-109 income, 121-175 extra, 201-232 generic, 301-328 accounts). */
object Icons {
    val categoryIds: List<Int> = (1..19) + (101..108) + (121..175) + (201..232)
    val accountIds: List<Int> = (301..312) + (321..328)

    private val cache = HashMap<Int, Int>()

    fun res(ctx: Context, id: Int): Int = cache.getOrPut(id) {
        ctx.resources.getIdentifier("ic_i$id", "drawable", ctx.packageName).takeIf { it != 0 } ?: R.drawable.ic_label
    }

    private val palette = intArrayOf(
        0xFFC62828.toInt(), 0xFFAD1457.toInt(), 0xFF6A1B9A.toInt(), 0xFF4527A0.toInt(), 0xFF283593.toInt(),
        0xFF1565C0.toInt(), 0xFF00838F.toInt(), 0xFF00695C.toInt(), 0xFF2E7D32.toInt(), 0xFF558B2F.toInt(),
        0xFF9E9D24.toInt(), 0xFFEF6C00.toInt(), 0xFFD84315.toInt(), 0xFF5D4037.toInt(), 0xFF37474F.toInt(),
    )

    // Hand-picked colours for the everyday categories/accounts; everything else rotates through the palette.
    private val fixed = mapOf(
        1 to 0xFF6A1B9A, 2 to 0xFF455A64, 3 to 0xFFEF6C00, 4 to 0xFF4527A0, 5 to 0xFFC62828, 6 to 0xFFD84315,
        7 to 0xFFAD1457, 8 to 0xFFEF6C00, 9 to 0xFF1565C0, 10 to 0xFF2E7D32, 11 to 0xFFD84315, 12 to 0xFF9E9D24,
        13 to 0xFF546E7A, 14 to 0xFF5D4037, 15 to 0xFF00695C, 16 to 0xFFAD1457, 17 to 0xFF2E7D32, 18 to 0xFF283593,
        19 to 0xFF1A237E, 20 to 0xFF546E7A,
        101 to 0xFF1565C0, 102 to 0xFFC62828, 103 to 0xFF00695C, 104 to 0xFFD50000, 105 to 0xFF2E7D32,
        106 to 0xFF6A1B9A, 107 to 0xFFAD1457, 108 to 0xFF558B2F, 109 to 0xFF546E7A,
        301 to 0xFF2E7D32, 302 to 0xFF1565C0, 303 to 0xFFAD1457, 304 to 0xFFC62828, 305 to 0xFF1A237E,
        306 to 0xFFEF6C00, 307 to 0xFF5D4037, 308 to 0xFF37474F, 309 to 0xFF0D47A1, 310 to 0xFFF57F17,
        311 to 0xFF00838F, 312 to 0xFF6D4C41,
    ).mapValues { it.value.toInt() }

    fun color(id: Int): Int = fixed[id] ?: palette[(id * 7) % palette.size]

    /** Round coloured badge with the icon's glyph, for category and account icons. */
    fun badge(ctx: Context, id: Int): Drawable = BadgeDrawable(ctx, res(ctx, id), color(id))

    /** Same style for a one-off glyph (e.g. transfers). */
    fun badge(ctx: Context, @DrawableRes glyph: Int, color: Int): Drawable = BadgeDrawable(ctx, glyph, color)
}
