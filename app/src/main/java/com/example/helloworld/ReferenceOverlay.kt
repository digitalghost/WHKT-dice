package com.example.helloworld

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback

/** Minimal full-screen image viewer: image, zoom/pan, optional multi-card switcher, and close affordance. */
object ReferenceOverlay {
    fun show(
        context: Context,
        tag: String,
        description: String,
        asset: String? = null,
        resource: Int? = null
    ) {
        showCards(
            context = context,
            tag = tag,
            description = description,
            cards = listOf(OperativeCardSource(label = "卡片", asset = asset, resource = resource))
        )
    }

    fun showCards(
        context: Context,
        tag: String,
        description: String,
        cards: List<OperativeCardSource>,
        initialIndex: Int = 0
    ) {
        if (cards.isEmpty()) return
        val activity = context as? Activity ?: return
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        root.findViewWithTag<View>(tag)?.let(root::removeView)
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        var selectedIndex = initialIndex.coerceIn(0, cards.lastIndex)
        lateinit var overlay: FrameLayout
        var backCallback: OnBackPressedCallback? = null
        fun close() {
            backCallback?.remove()
            backCallback = null
            if (overlay.parent === root) root.removeView(overlay)
        }
        if (activity is ComponentActivity) {
            val cb = object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    close()
                }
            }
            activity.onBackPressedDispatcher.addCallback(activity, cb)
            backCallback = cb
        }
        overlay = FrameLayout(context).apply {
            this.tag = tag
            setBackgroundColor(Color.rgb(15, 15, 15))
            elevation = dp(24).toFloat()
            isFocusable = true
            isFocusableInTouchMode = true
            setOnKeyListener { _, key, event ->
                if (key == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    close()
                    true
                } else false
            }
            addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) = Unit
                override fun onViewDetachedFromWindow(v: View) {
                    backCallback?.remove()
                    backCallback = null
                }
            })
        }
        val cardHolder = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                if (cards.size > 1) topMargin = dp(56)
            }
        }
        fun renderActiveCard() {
            val active = cards[selectedIndex]
            cardHolder.removeAllViews()
            cardHolder.addView(
                ZoomReference(context, asset = active.asset, resource = active.resource).apply {
                    contentDescription = if (cards.size > 1) "$description（${active.label}）" else description
                },
                FrameLayout.LayoutParams(-1, -1)
            )
        }
        renderActiveCard()
        overlay.addView(cardHolder)

        if (cards.size > 1) {
            val ui = RecordUi(context)
            val tabBar = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(8), dp(12), dp(8))
            }
            val tabs = mutableListOf<TextView>()
            fun updateTabStyles() {
                tabs.forEachIndexed { idx, tv ->
                    val active = idx == selectedIndex
                    tv.setTextColor(if (active) ui.base else ui.ink)
                    tv.background = ui.bg(
                        fill = if (active) ui.accent else ui.surface,
                        stroke = if (active) ui.accent else Color.rgb(68, 68, 66)
                    )
                }
            }
            cards.forEachIndexed { idx, source ->
                val tab = ui.text(
                    "${idx + 1}. ${source.label}",
                    12f,
                    ui.ink,
                    bold = true
                ).apply {
                    gravity = Gravity.CENTER
                    contentDescription = "切换${source.label}"
                    setPadding(dp(12), dp(8), dp(12), dp(8))
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        if (idx > 0) marginStart = dp(8)
                    }
                    setOnClickListener {
                        if (selectedIndex != idx) {
                            selectedIndex = idx
                            renderActiveCard()
                            updateTabStyles()
                        }
                        overlay.requestFocus()
                    }
                }
                tabs += tab
                tabBar.addView(tab)
            }
            updateTabStyles()
            val tabScroll = HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                addView(tabBar)
            }
            overlay.addView(
                tabScroll,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.START
                ).apply {
                    topMargin = dp(6)
                    marginEnd = dp(56)
                }
            )
        }

        overlay.addView(TextView(context).apply {
            text = "×"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(238, 117, 64))
            background = RippleDrawable(ColorStateList.valueOf(0x44ee7540), null, null)
            contentDescription = "关闭$description"
            setOnClickListener { close() }
        }, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP or Gravity.END).apply {
            topMargin = dp(6)
            marginEnd = dp(6)
        })
        root.addView(overlay, ViewGroup.LayoutParams(-1, -1))
        overlay.requestFocus()
    }
}
