package com.example.helloworld

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat

/** Orange/black tokens and a single-window, back-navigable detail panel. */
class RecordUi(val context: Context) {
    val ink = Color.rgb(235,235,233)
    val muted = Color.rgb(166,166,164)
    val accent = Color.rgb(238,117,64)
    val orange = Color.rgb(197,76,33)
    val base = Color.rgb(15,15,15)
    val surface = Color.rgb(25,25,25)
    private val line = Color.rgb(52,52,50)
    fun dp(n: Int) = (n * context.resources.displayMetrics.density).toInt()
    fun bg(fill: Int = surface, stroke: Int = Color.TRANSPARENT) = GradientDrawable().apply {
        setColor(fill);cornerRadius=dp(3).toFloat()
        if(stroke!=Color.TRANSPARENT) setStroke(dp(1),stroke)
    }
    fun text(value: String,size: Float=14f,color: Int=ink,bold: Boolean=false) = TextView(context).apply {
        text=value;textSize=size;setTextColor(color)
        typeface=Typeface.create("sans-serif",if(bold) Typeface.BOLD else Typeface.NORMAL)
        includeFontPadding=false;setLineSpacing(dp(2).toFloat(),1f)
    }
    fun heading(value: String,size: Float=26f) = text(value,size,ink,true).apply {
        typeface=ResourcesCompat.getFont(context,R.font.teko_variable)
        letterSpacing=.035f
    }
    fun column(padding: Int=0)=LinearLayout(context).apply {
        orientation=LinearLayout.VERTICAL;setPadding(dp(padding),dp(padding),dp(padding),dp(padding))
    }
    fun row()=LinearLayout(context).apply { gravity=Gravity.CENTER_VERTICAL }
    fun wrap(bottom: Int=8)=LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(bottom) }
    fun weight()=LinearLayout.LayoutParams(0,-2,1f).apply { marginEnd=dp(8) }
    fun card()=column(12).apply { background=bg() }
    fun divider()=View(context).apply { setBackgroundColor(line);layoutParams=LinearLayout.LayoutParams(-1,dp(1)).apply { topMargin=dp(8);bottomMargin=dp(8) } }
    // A 36dp visible control inside a 44dp touch target; compact without tiny taps.
    fun button(title: String,primary: Boolean=false,block: ()->Unit)=text(title,13f,ink,true).apply {
        gravity=Gravity.CENTER;minHeight=dp(44);setPadding(dp(10),dp(8),dp(10),dp(8))
        background=InsetDrawable(StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed),bg(if(primary) 0xff973916.toInt() else 0xff36302b.toInt()))
            addState(intArrayOf(),bg(if(primary) orange else 0xff242424.toInt()))
        },0,dp(4),0,dp(4))
        setPadding(dp(10),dp(8),dp(10),dp(8))
        setOnClickListener { block() }
    }
    fun link(title: String,block: ()->Unit)=button(title,false,block).apply {
        background=android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33ee7540),null,bg(ink))
        setTextColor(accent)
        setPadding(dp(10),dp(8),dp(10),dp(8))
    }
    fun portrait(resource: Int,size: Int=56)=ImageView(context).apply {
        setImageResource(resource);scaleType=ImageView.ScaleType.CENTER_CROP
        background=bg();clipToOutline=true;importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
        layoutParams=LinearLayout.LayoutParams(dp(size),dp(size)).apply { marginEnd=dp(12) }
    }
    fun memberPortrait(uri: String?, fallbackResource: Int, size: Int=48)=ImageView(context).apply {
        val loaded=uri?.let { value ->
            runCatching {
                setImageURI(android.net.Uri.parse(value))
                drawable != null
            }.getOrDefault(false)
        } ?: false
        if(!loaded) setImageResource(fallbackResource)
        scaleType=ImageView.ScaleType.CENTER_CROP
        background=bg(base,line);clipToOutline=true
        importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
        layoutParams=LinearLayout.LayoutParams(dp(size),dp(size)).apply { marginEnd=dp(10) }
    }
    fun teamArt(teamId: String)=TeamMarks.resource(teamId)
    fun input(value: String,hintText: String,numeric: Boolean=false)=EditText(context).apply {
        setText(value);hint=hintText;textSize=15f;setTextColor(ink);setHintTextColor(muted)
        typeface=Typeface.create("sans-serif",Typeface.NORMAL)
        background=bg(base,line);setPadding(dp(12),dp(10),dp(12),dp(10));minHeight=dp(44)
        inputType=if(numeric) android.text.InputType.TYPE_CLASS_NUMBER else android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
    }
    val isTablet get() = context.resources.configuration.screenWidthDp >= 600
    private val referenceWidth get() = minOf(
        context.resources.displayMetrics.widthPixels - dp(24),
        dp(context.resources.configuration.screenWidthDp - 24)
    )

    fun reference(title: String, closeLabel: String="关闭", onClose: (() -> Unit)?=null, build: (LinearLayout, Dialog) -> Unit) = dialog(title, true, closeLabel, onClose, build)

    /** Keep readable column widths; preserve row-wise reading and accessibility order. */
    fun <T> tiles(box: LinearLayout, items: List<T>, build: (LinearLayout, T) -> Unit) {
        val columns = if(isTablet) ((referenceWidth / context.resources.displayMetrics.density - 32) / 280).toInt().coerceIn(1, 4).coerceAtMost(items.size.coerceAtLeast(1)) else 1
        items.chunked(columns).forEach { group ->
            val row = row().apply { gravity = Gravity.TOP }
            group.forEachIndexed { index, item ->
                val tile = card()
                build(tile, item)
                row.addView(tile, LinearLayout.LayoutParams(0, -2, 1f).apply {
                    if(index < columns - 1) marginEnd = dp(12)
                })
            }
            repeat(columns - group.size) { row.addView(View(context), LinearLayout.LayoutParams(0, 0, 1f)) }
            box.addView(row, wrap(12))
        }
    }

    /** Inline, uncropped preview that lets the parent page scroll normally. */
    fun preview(
        box: LinearLayout,
        title: String,
        asset: String? = null,
        resource: Int? = null,
        expandable: Boolean = true
    ) {
        val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        fun decode() = if(resource != null) android.graphics.BitmapFactory.decodeResource(context.resources, resource, options)
            else context.assets.open(requireNotNull(asset)).use { android.graphics.BitmapFactory.decodeStream(it, null, options) }
        decode()
        val target = dp(600)
        options.inSampleSize = 1
        while(options.outWidth / options.inSampleSize > target * 2) options.inSampleSize *= 2
        options.inJustDecodeBounds = false
        val bitmap = decode()
        box.addView(ImageView(context).apply {
            setImageBitmap(bitmap)
            adjustViewBounds = true
            maxHeight = dp(540)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = if (expandable) "$title，点按放大" else title
            isClickable = expandable
            isFocusable = expandable
            if (expandable) {
                setOnClickListener {
                    reference(title) { body, _ ->
                        body.addView(text("双指缩放 · 双击复位", 12f, muted), wrap())
                        body.addView(ZoomReference(context, asset, resource).apply { contentDescription = title },
                            LinearLayout.LayoutParams(-1, (context.resources.displayMetrics.heightPixels * .72f).toInt()))
                    }
                }
            }
        }, wrap())
    }

    private data class Page(val token: Any,val view: View,val expanded: Boolean,val onClose: (() -> Unit)?)
    private val pages=mutableListOf<Page>()
    private var host: Dialog?=null
    /** One actual dialog window. Page handles dismiss their own page and descendants. */
    fun dialog(title: String,expanded: Boolean=false,closeLabel: String="关闭",onClose: (() -> Unit)?=null,build: (LinearLayout,Dialog)->Unit): Dialog {
        val token=Any()
        val handle=object: Dialog(context) {
            override fun dismiss() { dismissPage(token) }
        }
        val page=column(16).apply { background=bg(surface,line) }
        page.addView(row().apply {
            if(pages.isNotEmpty() && onClose==null) addView(link("‹ 返回") { dismissPage(token) })
            addView(text(title,18f,ink,true),weight())
            addView(link("×") { dismissPage(token) }.apply {
                contentDescription="关闭$title"
                minWidth=dp(44)
            })
        },wrap(8))
        page.addView(divider())
        val body=column()
        build(body,handle)
        page.addView(ScrollView(context).apply { isFillViewport=false;addView(body) },LinearLayout.LayoutParams(-1,0,1f))
        pages.add(Page(token,page,expanded,onClose))
        if(host==null) {
            host=Dialog(context).apply {
                window?.setBackgroundDrawableResource(android.R.color.transparent)
                setCancelable(true)
                setCanceledOnTouchOutside(true)
                setOnDismissListener {
                    val closed=pages.toList().asReversed()
                    pages.clear();host=null
                    closed.forEach { it.onClose?.invoke() }
                }
                setOnKeyListener { _,key,event ->
                    if(key==KeyEvent.KEYCODE_BACK) {
                        if(event.action==KeyEvent.ACTION_UP) pages.lastOrNull()?.let { dismissPage(it.token) }
                        true
                    } else false
                }
            }
        }
        displayPage()
        return handle
    }
    /** Re-measure the current page after inline reveal/hide content changes. */
    fun refreshDialog() {
        host?.window?.decorView?.post { if(host?.isShowing==true) displayPage() }
    }
    private fun dismissPage(token: Any) {
        val index=pages.indexOfFirst { it.token===token }
        if(index<0) return
        val closed=pages.subList(index,pages.size).toList().asReversed()
        pages.subList(index,pages.size).clear()
        closed.forEach { it.onClose?.invoke() }
        if(pages.isEmpty()) host?.dismiss() else displayPage()
    }
    private fun displayPage() {
        val page=pages.lastOrNull()?.view ?: return
        (page.parent as? ViewGroup)?.removeView(page)
        host?.apply {
            setContentView(page)
            if(!isShowing) show()
            val metrics=context.resources.displayMetrics
            val expanded=pages.last().expanded
            val width=if(expanded) referenceWidth else minOf(metrics.widthPixels-dp(24),dp(660))
            val body=((page as LinearLayout).getChildAt(2) as ScrollView).getChildAt(0)
            body.measure(View.MeasureSpec.makeMeasureSpec(width-dp(32),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
            val height=if(expanded) (metrics.heightPixels*.94f).toInt() else minOf(body.measuredHeight+dp(108),(metrics.heightPixels*.88f).toInt(),dp(760))
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            window?.setLayout(width,height)
            window?.setDimAmount(.6f)
            window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }
    fun note(message: String) { dialog("提示") { body,_->body.addView(text(message)) } }
}
