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
    /** Small grey caption and bold value in the bubble, e.g. "Here for" and "3 hrs, 52 mins". */
    val bubbleLabel: String?,
    val bubbleValue: String?,
    /** Emoji shown in the bubble and on the avatar's badge: a house for staying, a shoe for walking, a car for driving. */
    val emoji: String?,
)

class RenderedPin(val bitmap: Bitmap, /** 0..1 from the top: where the person's position is. */ val anchorY: Float)

private const val AVATAR = 56f
private const val RADIUS = 20f
private const val RING = 3f
private const val TAIL = 9f
private const val DOT = 10f
private const val BUBBLE_H = 42f
private const val BUBBLE_GAP = 6f

private fun paint(color: Int = Color.BLACK, style: Paint.Style = Paint.Style.FILL) = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
    this.color = color
    this.style = style
}

fun renderPin(spec: PinSpec, density: Float): RenderedPin {
    fun px(dp: Float) = dp * density
    val labelPaint = paint(0xFF6B6578.toInt()).apply { textSize = px(11f); typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL) }
    val valuePaint = paint(0xFF1B1530.toInt()).apply { textSize = px(14f); typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    val emojiPaint = paint().apply { textSize = px(20f) }

    val hasBubble = spec.bubbleValue != null
    val textW = if (hasBubble) max(labelPaint.measureText(spec.bubbleLabel ?: ""), valuePaint.measureText(spec.bubbleValue!!)) else 0f
    val emojiW = if (spec.emoji != null) px(30f) else 0f
    val bubbleW = textW + emojiW + px(24f)

    val halfAvatar = px(AVATAR / 2 + RING + 6f)
    val bubbleStart = px(14f)
    val halfWidth = max(halfAvatar, if (hasBubble) bubbleStart + bubbleW + px(6f) else 0f)
    val width = (halfWidth * 2).toInt()

    val bubbleTop = px(4f)
    val avatarTop = px(4f + if (hasBubble) BUBBLE_H - 12f else 0f) + px(RING)
    val avatarBottom = avatarTop + px(AVATAR)
    val tailTip = avatarBottom + px(TAIL)
    val dotCenter = tailTip + px(DOT / 2 + 1f)
    val height = (dotCenter + px(DOT / 2 + 6f)).toInt()

    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val cx = width / 2f

    // Bubble first, so the avatar sits on top of its corner like in the reference.
    if (hasBubble) {
        val r = RectF(cx + bubbleStart - px(0f), bubbleTop, cx + bubbleStart + bubbleW, bubbleTop + px(BUBBLE_H))
        val shadow = paint(0x00000000).apply { setShadowLayer(px(5f), 0f, px(2f), 0x33000000) }
        shadow.color = Color.WHITE
        c.drawRoundRect(r, px(14f), px(14f), shadow)
        c.drawRoundRect(r, px(14f), px(14f), paint(0xFFFFFFFF.toInt()))
        var x = r.left + px(12f)
        if (spec.emoji != null) {
            val fm = emojiPaint.fontMetrics
            c.drawText(spec.emoji, x, r.centerY() - (fm.ascent + fm.descent) / 2, emojiPaint)
            x += emojiW
        }
        val label = spec.bubbleLabel
        if (label != null) {
            c.drawText(label, x, r.top + px(17f), labelPaint)
            c.drawText(spec.bubbleValue!!, x, r.top + px(34f), valuePaint)
        } else {
            val fm = valuePaint.fontMetrics
            c.drawText(spec.bubbleValue!!, x, r.centerY() - (fm.ascent + fm.descent) / 2, valuePaint)
        }
    }

    // Avatar: rounded square with a ring, a soft shadow and a small tail pointing at the dot.
    val ringColor = if (spec.framed) spec.frameColor else Color.WHITE
    val outer = RectF(cx - px(AVATAR / 2 + RING), avatarTop - px(RING), cx + px(AVATAR / 2 + RING), avatarBottom + px(RING))
    val tail = Path().apply {
        moveTo(cx - px(8f), avatarBottom + px(RING) - px(2f))
        lineTo(cx, tailTip)
        lineTo(cx + px(8f), avatarBottom + px(RING) - px(2f))
        close()
    }
    val shadowPaint = paint(ringColor).apply { setShadowLayer(px(6f), 0f, px(2f), 0x40000000) }
    c.drawRoundRect(outer, px(RADIUS + RING), px(RADIUS + RING), shadowPaint)
    c.drawPath(tail, shadowPaint)
    c.drawRoundRect(outer, px(RADIUS + RING), px(RADIUS + RING), paint(ringColor))
    c.drawPath(tail, paint(ringColor))

    val inner = RectF(cx - px(AVATAR / 2), avatarTop, cx + px(AVATAR / 2), avatarBottom)
    val bg = if (spec.stale) blend(spec.avatarColor, 0xFF8A8A94.toInt(), 0.55f) else spec.avatarColor
    c.drawRoundRect(inner, px(RADIUS), px(RADIUS), paint(bg))
    if (spec.stale) {
        val dash = paint(0xFFFFFFFF.toInt(), Paint.Style.STROKE).apply {
            strokeWidth = px(2f)
            pathEffect = DashPathEffect(floatArrayOf(px(5f), px(4f)), 0f)
        }
        val d = RectF(inner).apply { inset(px(4f), px(4f)) }
        c.drawRoundRect(d, px(RADIUS - 4f), px(RADIUS - 4f), dash)
    }
    val initials = paint(spec.avatarTextColor).apply {
        textSize = px(20f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    val fm = initials.fontMetrics
    c.drawText(spec.initials, inner.centerX(), inner.centerY() - (fm.ascent + fm.descent) / 2, initials)

    // Badge: a small white disc with the emoji, on the avatar's lower right corner.
    if (spec.emoji != null) {
        val br = px(13f)
        val bx = inner.right - px(2f)
        val by = inner.bottom - px(2f)
        val badge = paint(Color.WHITE).apply { setShadowLayer(px(3f), 0f, px(1f), 0x44000000) }
        c.drawCircle(bx, by, br, badge)
        val e = paint().apply { textSize = px(15f); textAlign = Paint.Align.CENTER }
        val efm = e.fontMetrics
        c.drawText(spec.emoji, bx, by - (efm.ascent + efm.descent) / 2, e)
    }

    // The position itself: a crisp dot with a white ring. This is the marker's anchor.
    val dotColor = if (spec.stale) 0xFF8A8A94.toInt() else spec.accent
    c.drawCircle(cx, dotCenter, px(DOT / 2 + 2f), paint(Color.WHITE).apply { setShadowLayer(px(2f), 0f, px(1f), 0x44000000) })
    c.drawCircle(cx, dotCenter, px(DOT / 2), paint(dotColor))

    return RenderedPin(bmp, dotCenter / height)
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
