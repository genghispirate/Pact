package com.pact.app.core

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.pact.app.R
import java.io.File

/**
 * Renders a portrait "streak card" to a PNG and hands it to the Android share
 * sheet. This is the thing that leaves the app — a clean, brand-consistent
 * image built for a story or a feed. No private data, just the numbers the
 * user is proud of. Drawn with a plain Canvas so it needs no assets.
 */
object ShareCard {

    private const val W = 1080
    private const val H = 1350

    data class Data(
        val streakDays: Int,
        val longestStreakDays: Int,
        val screenTimeMinutes: Int,
        val challengeName: String? = null,
        val rank: Int? = null,
        val partySize: Int? = null,
    )

    fun share(context: Context, data: Data) {
        val bitmap = render(context, data)
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "pact-streak.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_caption))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.share_sheet_title))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun render(context: Context, data: Data): Bitmap {
        val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Backdrop: deep violet → ink, with a soft glow behind the number.
        val bg = Paint().apply {
            shader = LinearGradient(0f, 0f, W.toFloat(), H.toFloat(),
                intArrayOf(0xFF1A1A1A.toInt(), 0xFF0F0F0F.toInt()), null, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), bg)
        val glow = Paint().apply {
            shader = android.graphics.RadialGradient(
                W / 2f, H * 0.40f, W * 0.75f,
                intArrayOf(0x44B19CD9, 0x00000000), null, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), glow)

        val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val regular = Typeface.DEFAULT

        // Wordmark chip
        val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x22FFFFFF }
        val chip = RectF(W / 2f - 150f, 120f, W / 2f + 150f, 210f)
        canvas.drawRoundRect(chip, 45f, 45f, chipPaint)
        text(canvas, context.getString(R.string.app_name).uppercase(), W / 2f, 182f, 54f, 0xFFF5F5F5.toInt(), bold, spacing = 0.15f)

        // The headline number
        text(canvas, data.streakDays.toString(), W / 2f, H * 0.42f, 460f, 0xFFCCFF00.toInt(), bold)
        text(canvas, context.getString(R.string.share_streak_unit).uppercase(), W / 2f, H * 0.42f + 130f, 70f, 0xFFF5F5F5.toInt(), bold, spacing = 0.1f)
        text(canvas, context.getString(R.string.share_streak_sub), W / 2f, H * 0.42f + 210f, 46f, 0xFF9A9A9A.toInt(), regular)

        // A pair of supporting stats
        val statsY = H * 0.72f
        stat(canvas, context, W * 0.30f, statsY,
            if (data.screenTimeMinutes > 0) "${data.screenTimeMinutes}m" else "—",
            context.getString(R.string.share_stat_screen_time), bold, regular)
        stat(canvas, context, W * 0.70f, statsY,
            data.longestStreakDays.coerceAtLeast(data.streakDays).toString(),
            context.getString(R.string.share_stat_best), bold, regular, valueColor = 0xFFB19CD9.toInt())

        // Optional challenge ribbon
        data.challengeName?.let { name ->
            val rankText = if (data.rank != null && data.partySize != null)
                context.getString(R.string.share_challenge_rank, name, data.rank, data.partySize)
            else name
            val ribbon = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33B19CD9 }
            val r = RectF(W * 0.12f, H * 0.60f - 55f, W * 0.88f, H * 0.60f + 25f)
            canvas.drawRoundRect(r, 40f, 40f, ribbon)
            text(canvas, rankText, W / 2f, H * 0.60f, 44f, 0xFFCCFF00.toInt(), bold)
        }

        // Footer tagline
        text(canvas, context.getString(R.string.share_footer), W / 2f, H - 90f, 42f, 0xFF6A6A6A.toInt(), regular)
        return bitmap
    }

    private fun stat(
        canvas: Canvas, context: Context, cx: Float, cy: Float,
        value: String, label: String, bold: Typeface, regular: Typeface,
        valueColor: Int = 0xFFCCFF00.toInt(),
    ) {
        text(canvas, value, cx, cy, 96f, valueColor, bold)
        text(canvas, label.uppercase(), cx, cy + 60f, 34f, 0xFF6A6A6A.toInt(), regular, spacing = 0.08f)
    }

    private fun text(
        canvas: Canvas, s: String, cx: Float, cy: Float, size: Float,
        color: Int, typeface: Typeface, spacing: Float = 0f,
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            this.typeface = typeface
            textAlign = Paint.Align.CENTER
            letterSpacing = spacing
        }
        canvas.drawText(s, cx, cy, paint)
    }
}
