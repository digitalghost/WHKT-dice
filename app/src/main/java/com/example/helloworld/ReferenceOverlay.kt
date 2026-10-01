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
import android.widget.TextView

/** Minimal full-screen image viewer: image, zoom/pan, and one close affordance. */
object ReferenceOverlay {
    fun show(
        context: Context,
        tag: String,
        description: String,
        asset: String? = null,
        resource: Int? = null
    ) {
        val activity = context as? Activity ?: return
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        root.findViewWithTag<View>(tag)?.let(root::removeView)
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        lateinit var overlay: FrameLayout
        fun close() {
            if (overlay.parent === root) root.removeView(overlay)
        }
        overlay = FrameLayout(context).apply {
            this.tag = tag
            setBackgroundColor(Color.rgb(15, 15, 15))
            elevation = dp(24).toFloat()
            isFocusableInTouchMode = true
            setOnKeyListener { _, key, event ->
                if (key == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    close()
                    true
                } else false
            }
        }
        overlay.addView(
            ZoomReference(context, asset = asset, resource = resource).apply {
                contentDescription = description
            },
            FrameLayout.LayoutParams(-1, -1)
        )
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
