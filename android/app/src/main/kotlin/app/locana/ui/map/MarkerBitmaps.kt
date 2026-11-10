package app.locana.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.max

/*
 * Map markers are drawn straight onto a bitmap at the screen's real pixel density instead of being
 * rendered from composables. That keeps every edge sharp (no blur from scaling a low-resolution
 * snapshot) and lets the bitmap say exactly which pixel is the person's position: the small dot at
 * the bottom of the pin, which is the marker's anchor.
 */

/** What a member marker shows. Colours are ARGB ints so the drawing code does not depend on Compose. */
data class PinSpec(
    val initials: String,
    val avatarColor: Int,
    val avatarTextColor: Int,
    val accent: Int,
    /** Amber ring for people with a frame cosmetic; white otherwise. */
    val framed: Boolean,
    val frameColor: Int,
    /** Old position: avatar is greyed and the dot turns grey. */
    val stale: Boolean,
    /** Changes with the member's photo so a new photo gets a new bitmap. */
    val photoKey: Long? = null,
    /** Small grey caption and bold value in the bubble, e.g. "Here for" and "3 hrs, 52 mins". */
    val bubbleLabel: String?,
    val bubbleValue: String?,
    /** Emoji shown in the bubble and on the avatar's badge: a house for staying, a shoe for walking, a car for driving. */
    val emoji: String?,
    /** The person whose card is open: a violet ring instead of white, so the pin stands out from the rest. */
    val highlighted: Boolean = false,
)

class RenderedPin(val bitmap: Bitmap, /** 0..1 from the top: where the person's position is. */ val anchorY: Float)

private const val AVATAR = 66f
private const val RADIUS = 24f
private const val RING = 4f
private const val TAIL = 10f
private const val DOT = 14f
private const val BUBBLE_H = 46f

private fun paint(color: Int = Color.BLACK, style: Paint.Style = Paint.Style.FILL) = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
    this.color = color
    this.style = style
}

/**
 * A member on the map, as drawn in the mockup: a rounded-square avatar with a white ring and a solid
 * edge underneath, an optional place or activity badge, a white bubble ("Here for 3 hrs, 52 mins"), and
 * a dot below that marks the exact position (the marker's anchor).
 */
fun renderPin(spec: PinSpec, density: Float, photo: Bitmap? = null): RenderedPin {
    fun px(dp: Float) = dp * density
    val labelPaint = paint(0xFF5F587F.toInt()).apply { textSize = px(11f); typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL) }
    val valuePaint = paint(0xFF1F1645.toInt()).apply { textSize = px(15f); typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    val emojiPaint = paint().apply { textSize = px(22f); textAlign = Paint.Align.CENTER }

    val hasBubble = spec.bubbleValue != null
    val textW = if (hasBubble) max(labelPaint.measureText(spec.bubbleLabel ?: ""), valuePaint.measureText(spec.bubbleValue!!)) else 0f
    val tile = if (spec.emoji != null) px(34f) + px(8f) else 0f
    val bubbleW = px(6f) + tile + textW + px(14f)

    val halfAvatar = px(AVATAR / 2 + RING + 6f)
    val bubbleStart = px(16f)
    val halfWidth = max(halfAvatar, if (hasBubble) bubbleStart + bubbleW + px(6f) else 0f)
    val width = (halfWidth * 2).toInt()

    val bubbleTop = px(6f)
    val avatarTop = px(6f + if (hasBubble) BUBBLE_H - 14f else 0f) + px(RING)
    val avatarBottom = avatarTop + px(AVATAR)
    val tailTip = avatarBottom + px(RING + TAIL)
    val dotCenter = tailTip + px(DOT / 2 + 2f)
    val height = (dotCenter + px(DOT / 2 + 8f)).toInt()

    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val cx = width / 2f

    // Bubble first, so the avatar sits on top of its corner.
    if (hasBubble) {
        val r = RectF(cx + bubbleStart, bubbleTop, cx + bubbleStart + bubbleW, bubbleTop + px(BUBBLE_H))
        val shadow = paint(Color.WHITE).apply { setShadowLayer(px(5f), 0f, px(3f), 0x33000000) }
        c.drawRoundRect(r, px(16f), px(16f), shadow)
        c.drawRoundRect(r, px(16f), px(16f), paint(Color.WHITE))
        var x = r.left + px(6f)
        if (spec.emoji != null) {
            val t = RectF(x, r.centerY() - px(17f), x + px(34f), r.centerY() + px(17f))
            c.drawRoundRect(t, px(12f), px(12f), paint(0xFFF4EFFF.toInt()))
            val fm = emojiPaint.fontMetrics
            c.drawText(spec.emoji, t.centerX(), t.centerY() - (fm.ascent + fm.descent) / 2, emojiPaint)
            x += px(34f) + px(8f)
        }
        val label = spec.bubbleLabel
        if (label != null) {
            c.drawText(label, x, r.top + px(19f), labelPaint)
            c.drawText(spec.bubbleValue!!, x, r.top + px(36f), valuePaint)
        } else {
            val fm = valuePaint.fontMetrics
            c.drawText(spec.bubbleValue!!, x, r.centerY() - (fm.ascent + fm.descent) / 2, valuePaint)
        }
    }

    // Avatar: a rounded square with a ring, a soft shadow and a small arrow pointing down at the dot.
    val ringColor = if (spec.highlighted) spec.accent else if (spec.framed) spec.frameColor else Color.WHITE
    val face = if (spec.stale) 0xFFA99BD8.toInt() else spec.avatarColor
    val inner = RectF(cx - px(AVATAR / 2), avatarTop, cx + px(AVATAR / 2), avatarBottom)
    val outer = RectF(inner).apply { inset(-px(RING), -px(RING)) }
    val tail = Path().apply {
        moveTo(cx - px(9f), outer.bottom - px(3f))
        lineTo(cx, tailTip)
        lineTo(cx + px(9f), outer.bottom - px(3f))
        close()
    }
    val lifted = paint(ringColor).apply { setShadowLayer(px(6f), 0f, px(2f), 0x40000000) }
    c.drawRoundRect(outer, px(RADIUS + RING), px(RADIUS + RING), lifted)
    c.drawPath(tail, lifted)
    c.drawRoundRect(outer, px(RADIUS + RING), px(RADIUS + RING), paint(ringColor))
    c.drawPath(tail, paint(ringColor))
    c.drawRoundRect(inner, px(RADIUS), px(RADIUS), paint(face))
    if (photo != null) {
        val scaled = Bitmap.createScaledBitmap(photo, inner.width().toInt(), inner.height().toInt(), true)
        val shader = android.graphics.BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        shader.setLocalMatrix(android.graphics.Matrix().apply { setTranslate(inner.left, inner.top) })
        c.drawRoundRect(inner, px(RADIUS), px(RADIUS), paint().apply { this.shader = shader })
    } else {
        val initials = paint(spec.avatarTextColor).apply {
            textSize = px(24f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val fm = initials.fontMetrics
        c.drawText(spec.initials, inner.centerX(), inner.centerY() - (fm.ascent + fm.descent) / 2, initials)
    }

    // Badge: a small white disc on the avatar's lower right corner.
    if (spec.emoji != null) {
        val br = px(14f)
        val bx = outer.right - px(4f)
        val by = outer.bottom - px(4f)
        c.drawCircle(bx, by, br, paint(Color.WHITE).apply { setShadowLayer(px(3f), 0f, px(1f), 0x44000000) })
        val e = paint().apply { textSize = px(16f); textAlign = Paint.Align.CENTER }
        val efm = e.fontMetrics
        c.drawText(spec.emoji, bx, by - (efm.ascent + efm.descent) / 2, e)
    }

    // The position itself: a crisp dot with a white ring. This is the marker's anchor.
    val dotColor = if (spec.stale) 0xFF9C97B6.toInt() else spec.accent
    c.drawCircle(cx, dotCenter, px(DOT / 2), paint(Color.WHITE).apply { setShadowLayer(px(2f), 0f, px(1f), 0x44000000) })
    c.drawCircle(cx, dotCenter, px(DOT / 2 - 3f), paint(dotColor))

    return RenderedPin(bmp, dotCenter / height)
}

/** A soft disc under the focused person's position: reads as a spotlight on the map. */
fun renderHalo(accent: Int, density: Float): Bitmap {
    val size = (132 * density).toInt()
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val m = size / 2f
    c.drawCircle(m, m, m, paint().apply { shader = RadialGradient(m, m, m, intArrayOf(withAlpha(accent, 0.34f), withAlpha(accent, 0.22f), withAlpha(accent, 0f)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP) })
    c.drawCircle(m, m, m * 0.62f, paint(withAlpha(accent, 0.55f), Paint.Style.STROKE).apply { strokeWidth = 2f * density })
    return bmp
}

/**
 * The beam that shows which way the phone is pointing: a soft wedge fading out from the dot. The
 * bitmap points up with its tip at the bottom centre; the map rotates it to the compass bearing.
 */
fun renderHeadingCone(accent: Int, density: Float): Bitmap {
    val w = (88 * density).toInt()
    val h = (96 * density).toInt()
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val cx = w / 2f
    val tip = h.toFloat()
    val path = Path().apply {
        moveTo(cx, tip)
        lineTo(0f, h * 0.12f)
        quadTo(cx, -h * 0.08f, w.toFloat(), h * 0.12f)
        close()
    }
    val p = paint().apply {
        shader = RadialGradient(cx, tip, h.toFloat(), intArrayOf(withAlpha(accent, 0.62f), withAlpha(accent, 0.30f), withAlpha(accent, 0f)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
    }
    c.drawPath(path, p)
    return bmp
}

/** A small marker for places a trip started or ended, used on timeline maps. */
fun renderDot(color: Int, density: Float, flag: Boolean = false): Bitmap {
    val size = (22 * density).toInt()
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val m = size / 2f
    c.drawCircle(m, m, m - density, paint(Color.WHITE).apply { setShadowLayer(density * 2, 0f, density, 0x44000000) })
    c.drawCircle(m, m, m - 4 * density, paint(color))
    if (flag) c.drawCircle(m, m, 2.5f * density, paint(Color.WHITE))
    return bmp
}

private fun withAlpha(color: Int, a: Float) = (color and 0x00FFFFFF) or ((a * 255).toInt() shl 24)

private fun blend(a: Int, b: Int, t: Float): Int {
    fun ch(shift: Int) = (((a shr shift and 0xFF) * (1 - t)) + ((b shr shift and 0xFF) * t)).toInt()
    return Color.rgb(ch(16), ch(8), ch(0))
}

/** A round white marker with an emoji, for places found around you. Drawn at screen density like the pins. */
fun renderEmojiDisc(emoji: String, density: Float, selected: Boolean = false): Bitmap {
    val size = ((if (selected) 44 else 36) * density).toInt()
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val m = size / 2f
    c.drawCircle(m, m, m - 3 * density, paint(Color.WHITE).apply { setShadowLayer(2.5f * density, 0f, density, 0x55000000) })
    if (selected) c.drawCircle(m, m, m - 3 * density, paint(0xFF5B2DD0.toInt(), Paint.Style.STROKE).apply { strokeWidth = 2.5f * density })
    val e = paint().apply { textSize = size * 0.46f; textAlign = Paint.Align.CENTER }
    val fm = e.fontMetrics
    c.drawText(emoji, m, m - (fm.ascent + fm.descent) / 2, e)
    return bmp
}
