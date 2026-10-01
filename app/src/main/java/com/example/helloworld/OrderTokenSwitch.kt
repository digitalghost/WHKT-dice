package com.example.helloworld

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.appcompat.content.res.AppCompatResources

/**
 * One compact, two-position control for an operative's physical order token.
 *
 * Left is Conceal and right is Engage. Tapping the inactive side slides the
 * token to that order; tapping the occupied side flips its ready/spent face.
 */
class OrderTokenSwitch @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val density=resources.displayMetrics.density
    private fun dp(value: Float)=value*density

    private val fillPaint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style=Paint.Style.STROKE
        strokeWidth=dp(1f)
    }
    private val labelPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color=Color.rgb(166,166,164)
        textAlign=Paint.Align.CENTER
        textSize=11f*resources.displayMetrics.scaledDensity
        typeface=android.graphics.Typeface.DEFAULT_BOLD
    }

    private var order=BattleOperativeState.ORDER_CONCEAL
    private var ready=true
    private var pressedInside=false

    var onOrderSelected: ((String)->Unit)?=null
    var onFaceFlipped: (() -> Unit)?=null

    init {
        isClickable=true
        isFocusable=true
        minimumWidth=dp(92f).toInt()
        minimumHeight=dp(48f).toInt()
    }

    fun bind(order: String,ready: Boolean) {
        this.order=order
        this.ready=ready
        updateDescription()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int,heightMeasureSpec: Int) {
        setMeasuredDimension(
            resolveSize(dp(92f).toInt(),widthMeasureSpec),
            resolveSize(dp(48f).toInt(),heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val edge=dp(2f)
        val track=RectF(edge,dp(7f),width-edge,height-dp(7f))
        val radius=dp(9f)

        fillPaint.color=if(pressedInside) Color.rgb(45,42,39) else Color.rgb(31,31,30)
        canvas.drawRoundRect(track,radius,radius,fillPaint)
        strokePaint.color=Color.rgb(67,65,62)
        canvas.drawRoundRect(track,radius,radius,strokePaint)

        val half=track.width()/2f
        val selectedLeft=order==BattleOperativeState.ORDER_CONCEAL
        val selectedStart=if(selectedLeft) track.left else track.left+half
        fillPaint.color=if(ready) Color.rgb(58,48,43) else Color.rgb(39,37,35)
        canvas.drawRoundRect(
            RectF(selectedStart+dp(2f),track.top+dp(2f),selectedStart+half-dp(2f),track.bottom-dp(2f)),
            dp(7f),dp(7f),fillPaint
        )

        strokePaint.color=Color.rgb(54,52,50)
        canvas.drawLine(width/2f,track.top+dp(6f),width/2f,track.bottom-dp(6f),strokePaint)

        val baseline=height/2f-(labelPaint.ascent()+labelPaint.descent())/2f
        if(!selectedLeft) canvas.drawText("隐匿",track.left+half/2f,baseline,labelPaint)
        if(selectedLeft) canvas.drawText("交战",track.left+half+half/2f,baseline,labelPaint)

        val tokenSize=dp(40f).toInt()
        val centerX=if(selectedLeft) track.left+half/2f else track.left+half+half/2f
        val left=(centerX-tokenSize/2f).toInt()
        val top=((height-tokenSize)/2f).toInt()
        AppCompatResources.getDrawable(context,OrderTokens.resource(order,ready))?.apply {
            setBounds(left,top,left+tokenSize,top+tokenSize)
            draw(canvas)
        }

        if(!isEnabled) {
            fillPaint.color=0x660f0f0f
            canvas.drawRoundRect(track,radius,radius,fillPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if(!isEnabled) return false
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedInside=true
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val nowInside=event.x in 0f..width.toFloat() && event.y in 0f..height.toFloat()
                if(nowInside!=pressedInside) {
                    pressedInside=nowInside
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val wasInside=pressedInside
                pressedInside=false
                invalidate()
                if(wasInside) {
                    val target=OrderTokens.orderForSide(event.x<width/2f)
                    if(target==order) {
                        performClick()
                    } else {
                        playSoundEffect(android.view.SoundEffectConstants.CLICK)
                        onOrderSelected?.invoke(target)
                    }
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedInside=false
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        onFaceFlipped?.invoke()
        return true
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className="android.widget.Switch"
        info.isCheckable=true
        info.isChecked=order==BattleOperativeState.ORDER_ENGAGE
        info.addAction(AccessibilityNodeInfo.AccessibilityAction(ACTION_SWITCH_ORDER,"切换隐匿／交战命令"))
    }

    override fun performAccessibilityAction(action: Int,args: Bundle?): Boolean {
        if(action==ACTION_SWITCH_ORDER && isEnabled) {
            onOrderSelected?.invoke(OrderTokens.oppositeOrder(order))
            return true
        }
        return super.performAccessibilityAction(action,args)
    }

    private fun updateDescription() {
        contentDescription="${order}命令，${OrderTokens.faceLabel(ready)}。点当前侧翻面，点另一侧切换命令"
    }

    private companion object {
        const val ACTION_SWITCH_ORDER=0x01020001
    }
}
