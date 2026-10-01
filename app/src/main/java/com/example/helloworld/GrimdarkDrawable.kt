package com.example.helloworld

import android.R
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable
import java.util.Random

enum class ConsoleSurface {
    ROOT,
    HEADER,
    FRAME,
    PANEL,
    CELL,
    SELECTED,
    INSET,
    GREEN,
    GREEN_PRESSED,
    DISABLED
}

class GrimdarkDrawable(
    context: Context,
    private val surface: ConsoleSurface
) : Drawable() {

    private val density = context.resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val detail = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var drawableAlpha = 255

    override fun draw(canvas: Canvas) {
        val rect = RectF(bounds)
        if (rect.isEmpty) return
        fill.shader = null
        fill.color = when(surface) {
            ConsoleSurface.ROOT -> Color.rgb(15,15,15)
            ConsoleSurface.HEADER -> Color.rgb(9,9,9)
            ConsoleSurface.GREEN -> Color.rgb(197,76,33)
            ConsoleSurface.GREEN_PRESSED -> Color.rgb(150,57,22)
            ConsoleSurface.SELECTED -> Color.rgb(60,32,23)
            ConsoleSurface.INSET -> Color.rgb(18,18,18)
            ConsoleSurface.DISABLED -> Color.rgb(32,32,32)
            else -> Color.rgb(27,27,27)
        }
        fill.alpha = drawableAlpha
        canvas.drawRoundRect(rect, 3*density, 3*density, fill)
        if(surface == ConsoleSurface.SELECTED || surface == ConsoleSurface.HEADER) {
            detail.color = Color.rgb(197,76,33)
            detail.alpha = drawableAlpha
            canvas.drawRect(rect.left,rect.bottom-2*density,rect.right,rect.bottom,detail)
        }
    }

    private fun colorsFor(style: ConsoleSurface): Pair<Int, Int> = when (style) {
        ConsoleSurface.ROOT -> Color.rgb(22, 19, 15) to Color.rgb(6, 6, 5)
        ConsoleSurface.HEADER -> Color.rgb(55, 49, 40) to Color.rgb(13, 12, 10)
        ConsoleSurface.FRAME -> Color.rgb(61, 54, 43) to Color.rgb(14, 12, 9)
        ConsoleSurface.PANEL -> Color.rgb(46, 40, 31) to Color.rgb(12, 11, 8)
        ConsoleSurface.CELL -> Color.rgb(41, 37, 31) to Color.rgb(13, 12, 10)
        ConsoleSurface.SELECTED -> Color.rgb(99, 76, 24) to Color.rgb(35, 27, 10)
        ConsoleSurface.INSET -> Color.rgb(23, 21, 17) to Color.rgb(5, 5, 4)
        ConsoleSurface.GREEN -> Color.rgb(42, 92, 55) to Color.rgb(8, 39, 20)
        ConsoleSurface.GREEN_PRESSED -> Color.rgb(30, 68, 41) to Color.rgb(5, 25, 13)
        ConsoleSurface.DISABLED -> Color.rgb(43, 40, 35) to Color.rgb(20, 18, 15)
    }

    private fun drawTexture(canvas: Canvas, rect: RectF) {
        val random = Random(surface.ordinal * 911L + rect.width().toLong() + rect.height().toLong())
        val count = when (surface) {
            ConsoleSurface.ROOT, ConsoleSurface.FRAME, ConsoleSurface.PANEL -> 42
            ConsoleSurface.HEADER -> 28
            else -> 14
        }
        detail.strokeWidth = 0.65f * density
        repeat(count) {
            val x = rect.left + random.nextFloat() * rect.width()
            val y = rect.top + random.nextFloat() * rect.height()
            val length = (6f + random.nextFloat() * 34f) * density
            detail.color = if (random.nextBoolean()) 0x16FFFFFF else 0x24000000
            canvas.drawLine(x, y, (x + length).coerceAtMost(rect.right), y + random.nextFloat() * 2f, detail)
        }
        detail.color = 0x16000000
        repeat(18) {
            val x = rect.left + random.nextFloat() * rect.width()
            val y = rect.top + random.nextFloat() * rect.height()
            canvas.drawCircle(x, y, (0.6f + random.nextFloat() * 1.5f) * density, detail)
        }
    }

    private fun drawBevel(canvas: Canvas, rect: RectF, cut: Float) {
        stroke.shader = null
        stroke.strokeWidth = 3f * density
        stroke.color = 0xE0050504.toInt()
        canvas.drawPath(path, stroke)

        val inner = RectF(rect).apply { inset(3.5f * density, 3.5f * density) }
        buildChamferedPath(inner, (cut - 2f * density).coerceAtLeast(0f))
        stroke.strokeWidth = when (surface) {
            ConsoleSurface.SELECTED, ConsoleSurface.GREEN, ConsoleSurface.GREEN_PRESSED -> 1.8f * density
            else -> 1.05f * density
        }
        stroke.color = when (surface) {
            ConsoleSurface.GREEN, ConsoleSurface.GREEN_PRESSED -> Color.rgb(175, 205, 142)
            ConsoleSurface.SELECTED -> Color.rgb(230, 190, 90)
            else -> Color.rgb(118, 91, 40)
        }
        canvas.drawPath(path, stroke)

        val highlight = RectF(inner).apply { inset(2f * density, 2f * density) }
        buildChamferedPath(highlight, (cut - 3f * density).coerceAtLeast(0f))
        stroke.strokeWidth = 0.7f * density
        stroke.color = 0x35FFFFFF
        canvas.drawPath(path, stroke)
    }

    private fun drawRivets(canvas: Canvas, rect: RectF) {
        val inset = 9f * density
        val radius = 4.2f * density
        val points = arrayOf(
            rect.left + inset to rect.top + inset,
            rect.right - inset to rect.top + inset,
            rect.left + inset to rect.bottom - inset,
            rect.right - inset to rect.bottom - inset
        )
        points.forEach { (x, y) ->
            detail.shader = RadialGradient(
                x - radius * 0.25f,
                y - radius * 0.25f,
                radius,
                intArrayOf(Color.rgb(180, 158, 108), Color.rgb(78, 66, 46), Color.rgb(19, 17, 14)),
                floatArrayOf(0f, 0.58f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(x, y, radius, detail)
            detail.shader = null
            detail.color = 0xB8000000.toInt()
            detail.strokeWidth = density
            canvas.drawLine(x - radius * 0.48f, y, x + radius * 0.48f, y, detail)
        }
    }

    private fun drawVent(canvas: Canvas, rect: RectF) {
        val x1 = rect.left + 24f * density
        val x2 = x1 + 36f * density
        val center = rect.centerY()
        stroke.shader = null
        stroke.strokeWidth = 2.4f * density
        stroke.color = 0xB0050504.toInt()
        for (offset in -12..12 step 6) {
            canvas.drawLine(x1, center + offset * density, x2, center + offset * density, stroke)
        }
        stroke.strokeWidth = 0.8f * density
        stroke.color = 0x607E6330
        canvas.drawRect(x1 - 7f * density, center - 19f * density, x2 + 7f * density, center + 19f * density, stroke)
    }

    private fun buildChamferedPath(rect: RectF, cut: Float) {
        path.reset()
        if (cut <= 0f) {
            path.addRect(rect, Path.Direction.CW)
            return
        }
        path.moveTo(rect.left + cut, rect.top)
        path.lineTo(rect.right - cut, rect.top)
        path.lineTo(rect.right, rect.top + cut)
        path.lineTo(rect.right, rect.bottom - cut)
        path.lineTo(rect.right - cut, rect.bottom)
        path.lineTo(rect.left + cut, rect.bottom)
        path.lineTo(rect.left, rect.bottom - cut)
        path.lineTo(rect.left, rect.top + cut)
        path.close()
    }

    override fun setAlpha(alpha: Int) {
        drawableAlpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fill.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Android")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

object GrimdarkSkins {
    fun button(context: Context, normal: ConsoleSurface, pressed: ConsoleSurface = normal): Drawable {
        return StateListDrawable().apply {
            addState(intArrayOf(-R.attr.state_enabled), GrimdarkDrawable(context, ConsoleSurface.DISABLED))
            addState(intArrayOf(R.attr.state_pressed), GrimdarkDrawable(context, pressed))
            addState(intArrayOf(), GrimdarkDrawable(context, normal))
        }
    }
}
