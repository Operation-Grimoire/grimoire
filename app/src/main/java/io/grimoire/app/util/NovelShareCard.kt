package io.grimoire.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.grimoire.app.R
import java.io.File

/**
 * All the novel facts the share card renders. Built from the detail screen's state so the
 * renderer stays a pure Context + data -> image function.
 */
data class NovelShareData(
    val coverModel: Any?,
    val title: String,
    val author: String?,
    /** The user's own 1–10 rating, drawn large when present. */
    val userRating: Int?,
    val readChapters: Int,
    val totalChapters: Int,
    val percent: Int,
    /** Sum of [ChapterEntity.wordCount] over read chapters; 0 when none are counted. */
    val wordsRead: Int,
    /** Sum over all chapters; 0 when no chapter has a known word count. */
    val totalWords: Int,
)

/**
 * Renders a portrait "reading progress" card for a novel — cover floated over a gradient
 * sampled from the cover's own colors, with title, author and progress stats — and caches it
 * for an ACTION_SEND share. Everything is drawn with android.graphics so no Compose capture
 * is needed and the output is a fixed, share-friendly size regardless of screen density.
 */
object NovelShareCardRenderer {

    private const val W = 1080
    private const val H = 1620

    suspend fun render(context: Context, data: NovelShareData): Uri? = withContext(Dispatchers.IO) {
        val cover = data.coverModel?.let { loadBitmap(context, it) }
        val palette = cover?.let { extractPalette(it) } ?: DEFAULT_PALETTE
        val card = drawCard(context, data, cover, palette)
        cacheForShare(context, card, data.title, data.hashCode())
    }

    private suspend fun loadBitmap(context: Context, model: Any): Bitmap? {
        val request = ImageRequest.Builder(context)
            .data(model)
            .allowHardware(false) // need a software bitmap to read pixels / composite
            .build()
        return runCatching { context.imageLoader.execute(request).drawable?.toBitmap() }.getOrNull()
    }

    // --- Drawing ---

    private fun drawCard(
        context: Context,
        data: NovelShareData,
        cover: Bitmap?,
        palette: Palette,
    ): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // Gradient backdrop derived from the cover.
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, H.toFloat(),
                palette.top, palette.bottom, Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), bgPaint)

        val onColor = palette.onColor
        val onMuted = withAlpha(onColor, 0.72f)
        val margin = 80f

        // Cover, centered near the top with a soft drop shadow and rounded corners.
        val coverW = 640f
        val coverAspect = if (cover != null && cover.width > 0) {
            cover.height.toFloat() / cover.width.toFloat()
        } else 1.5f
        // Aspect cap keeps the tallest cover + a two-line title + author clear
        // of the bottom-anchored percent row.
        val coverH = coverW * coverAspect.coerceIn(1.2f, 1.38f)
        val coverLeft = (W - coverW) / 2f
        val coverTop = 80f
        val coverRect = RectF(coverLeft, coverTop, coverLeft + coverW, coverTop + coverH)

        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(Color.BLACK, 0.45f)
            setShadowLayer(48f, 0f, 24f, withAlpha(Color.BLACK, 0.55f))
        }
        canvas.drawRoundRect(coverRect, 28f, 28f, shadowPaint)

        if (cover != null) {
            canvas.save()
            val clip = android.graphics.Path().apply { addRoundRect(coverRect, 28f, 28f, android.graphics.Path.Direction.CW) }
            canvas.clipPath(clip)
            val src = android.graphics.Rect(0, 0, cover.width, cover.height)
            canvas.drawBitmap(cover, src, coverRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            canvas.restore()
        } else {
            val ph = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(onColor, 0.14f) }
            canvas.drawRoundRect(coverRect, 28f, 28f, ph)
        }

        // The title/author flow top-down under the cover; the progress block
        // (percent, bar, stat line) is anchored to the bottom instead of
        // flowing after them, so a title that wraps to a second line can never
        // push the stats into the footer (#318 — the "stacked" text).

        var y = coverRect.bottom + 48f

        // Title (up to two lines, ellipsized).
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = onColor
            textSize = 58f
            isFakeBoldText = true
        }
        val titleLayout = ellipsizedLayout(data.title, titlePaint, (W - margin * 2).toInt(), maxLines = 2)
        canvas.withTranslation(margin, y) { titleLayout.draw(this) }
        y += titleLayout.height + 12f

        // Author.
        if (!data.author.isNullOrBlank()) {
            val authorPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = onMuted
                textSize = 38f
            }
            val authorLayout = ellipsizedLayout("by ${data.author}", authorPaint, (W - margin * 2).toInt(), maxLines = 1)
            canvas.withTranslation(margin, y) { authorLayout.draw(this) }
        }

        // Stat line: chapters (+ words when available). Sized first so the
        // bar and percent row can stack above it.
        val statPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = onColor
            textSize = 40f
        }
        val chapterStat = context.getString(
            R.string.share_card_chapters,
            data.readChapters,
            data.totalChapters,
        )
        val stat = if (data.totalWords > 0) {
            context.getString(
                R.string.share_card_stats,
                chapterStat,
                context.getString(R.string.share_card_words_read, formatCount(data.wordsRead)),
            )
        } else {
            chapterStat
        }
        val statLayout = ellipsizedLayout(stat, statPaint, (W - margin * 2).toInt(), maxLines = 1)
        val statTop = H - 132f - statLayout.height

        // Progress bar above the stat line.
        val barLeft = margin
        val barRight = W - margin
        val barH = 22f
        val barTop = statTop - 24f - barH
        val track = RectF(barLeft, barTop, barRight, barTop + barH)
        canvas.drawRoundRect(track, barH / 2f, barH / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(onColor, 0.18f) })
        val fillW = (barRight - barLeft) * (data.percent / 100f)
        if (fillW > 0f) {
            val fill = RectF(barLeft, barTop, barLeft + fillW.coerceAtLeast(barH), barTop + barH)
            canvas.drawRoundRect(fill, barH / 2f, barH / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.accent })
        }

        // Big percentage row above the bar.
        val percentPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = onColor
            textSize = 130f
            isFakeBoldText = true
        }
        val percentTop = barTop - 16f - percentPaint.textSize
        val percentText = "${data.percent}%"
        canvas.drawText(percentText, margin, percentTop + percentPaint.textSize * 0.75f, percentPaint)

        val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = onMuted
            textSize = 34f
        }
        canvas.drawText(
            context.getString(R.string.share_card_read),
            margin + percentPaint.measureText(percentText) + 24f,
            percentTop + percentPaint.textSize * 0.65f,
            labelPaint,
        )

        // Big user rating on the right of the same row (only when the user has rated it).
        data.userRating?.let { rating ->
            val baseY = percentTop + percentPaint.textSize * 0.75f
            val starPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.accent; textSize = 84f }
            val numPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = onColor; textSize = 130f; isFakeBoldText = true }
            val ofPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = onMuted; textSize = 40f }
            val star = "★ "
            val num = rating.toString()
            val of = "/10"
            val total = starPaint.measureText(star) + numPaint.measureText(num) + ofPaint.measureText(of)
            var x = W - margin - total
            canvas.drawText(star, x, baseY, starPaint); x += starPaint.measureText(star)
            canvas.drawText(num, x, baseY, numPaint); x += numPaint.measureText(num)
            canvas.drawText(of, x, baseY, ofPaint)
        }

        canvas.withTranslation(margin, statTop) { statLayout.draw(this) }

        // Footer: app wordmark.
        val footPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = onMuted
            textSize = 32f
        }
        canvas.drawText("Grimoire", margin, H - 72f, footPaint)

        return bmp
    }

    private fun cacheForShare(context: Context, bitmap: Bitmap, baseName: String, contentKey: Int): Uri? = runCatching {
        val dir = File(context.cacheDir, "shared_images").apply { mkdirs() }
        val slug = baseName.ifBlank { "novel" }.replace(Regex("[^A-Za-z0-9._-]"), "_").take(50)
        // The file name must change whenever the rendered facts do: image loaders (Coil in
        // the preview, and whatever the receiving app uses) cache by URI, so re-writing the
        // same path would keep serving a card with stale progress.
        val name = "share_${slug}_${Integer.toHexString(contentKey)}.png"
        dir.listFiles { f -> f.name.startsWith("share_$slug") && f.name != name }?.forEach { it.delete() }
        val file = File(dir, name)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }.getOrNull()

    // --- Color extraction ---

    private data class Palette(val top: Int, val bottom: Int, val accent: Int, val onColor: Int)

    private val DEFAULT_PALETTE = Palette(
        top = Color.rgb(38, 42, 58),
        bottom = Color.rgb(18, 20, 30),
        accent = Color.rgb(120, 150, 255),
        onColor = Color.WHITE,
    )

    /**
     * Coarse color quantization: bucket the downscaled cover into a 4-bit-per-channel grid,
     * take the most populous bucket as the base tone and the most saturated well-populated
     * bucket as the accent. Cheap, dependency-free, and good enough for a backdrop.
     */
    private fun extractPalette(cover: Bitmap): Palette {
        val small = Bitmap.createScaledBitmap(cover, 48, 48, true)
        val counts = HashMap<Int, Int>()
        val sums = HashMap<Int, IntArray>() // key -> [r,g,b]
        for (yy in 0 until small.height) {
            for (xx in 0 until small.width) {
                val p = small.getPixel(xx, yy)
                if (Color.alpha(p) < 128) continue
                val r = Color.red(p); val g = Color.green(p); val b = Color.blue(p)
                val key = (r shr 4 shl 8) or (g shr 4 shl 4) or (b shr 4)
                counts[key] = (counts[key] ?: 0) + 1
                val s = sums.getOrPut(key) { IntArray(3) }
                s[0] += r; s[1] += g; s[2] += b
            }
        }
        if (counts.isEmpty()) return DEFAULT_PALETTE

        fun avg(key: Int): Int {
            val n = counts[key] ?: 1
            val s = sums[key]!!
            return Color.rgb(s[0] / n, s[1] / n, s[2] / n)
        }

        // Skip the near-white / near-black buckets that dominate most covers (page
        // backgrounds, borders) so the card takes its color from the actual artwork.
        val colorful = counts.entries.filter {
            val c = avg(it.key)
            saturation(c) > 0.15f && luminance(c) in 0.12f..0.9f
        }

        // Base tone drives the gradient: the most prominent *colored* bucket, weighted
        // slightly by saturation so a vivid accent wins over a large muted region.
        val baseKey = colorful.maxByOrNull { it.value * (0.5 + saturation(avg(it.key))) }?.key
            ?: counts.maxByOrNull { it.value }!!.key
        val base = avg(baseKey)

        // Accent (progress bar): the most saturated well-populated bucket.
        val minCount = (counts.values.sum() * 0.02).toInt().coerceAtLeast(2)
        val accentRaw = counts.entries
            .filter { it.value >= minCount }
            .maxByOrNull { saturation(avg(it.key)) * kotlin.math.sqrt(it.value.toDouble()) }
            ?.let { avg(it.key) } ?: base
        val accent = if (saturation(accentRaw) < 0.3f) lighten(base, 0.4f) else lighten(accentRaw, 0.15f)

        // Darken the base into a rich vertical gradient; darker at the bottom so the
        // (white) text always has contrast regardless of how light the cover is.
        val top = mix(base, Color.BLACK, 0.42f)
        val bottom = mix(base, Color.BLACK, 0.74f)
        val onColor = if (luminance(mix(top, bottom, 0.5f)) > 0.55f) Color.rgb(20, 20, 24) else Color.WHITE
        return Palette(top, bottom, accent, onColor)
    }

    private fun saturation(c: Int): Float {
        val r = Color.red(c) / 255f; val g = Color.green(c) / 255f; val b = Color.blue(c) / 255f
        val max = maxOf(r, g, b); val min = minOf(r, g, b)
        return if (max <= 0f) 0f else (max - min) / max
    }

    private fun luminance(c: Int): Float =
        (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f

    private fun mix(a: Int, b: Int, t: Float): Int {
        val it = 1f - t
        return Color.rgb(
            (Color.red(a) * it + Color.red(b) * t).toInt().coerceIn(0, 255),
            (Color.green(a) * it + Color.green(b) * t).toInt().coerceIn(0, 255),
            (Color.blue(a) * it + Color.blue(b) * t).toInt().coerceIn(0, 255),
        )
    }

    private fun lighten(c: Int, t: Float) = mix(c, Color.WHITE, t)

    private fun withAlpha(c: Int, alpha: Float) =
        Color.argb((alpha * 255).toInt().coerceIn(0, 255), Color.red(c), Color.green(c), Color.blue(c))

    // --- Text helpers ---

    private fun ellipsizedLayout(text: String, paint: TextPaint, width: Int, maxLines: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setLineSpacing(0f, 1.05f)
            .build()

    private fun formatCount(n: Int): String = String.format("%,d", n)

    private inline fun Canvas.withTranslation(dx: Float, dy: Float, block: Canvas.() -> Unit) {
        val save = save()
        translate(dx, dy)
        try { block() } finally { restoreToCount(save) }
    }
}
