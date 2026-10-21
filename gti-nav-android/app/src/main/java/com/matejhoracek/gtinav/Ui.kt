package com.matejhoracek.gtinav

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout

// Small helpers for building the screens in code.

fun Context.dp(value: Number): Int = (value.toFloat() * resources.displayMetrics.density).toInt()

// Dark see-through rounded card, the Android stand-in for the iPhone's Liquid Glass.
fun Context.cardBackground(radiusDp: Number = 28, color: Int = GtiLook.CARD) = GradientDrawable().apply {
    setColor(color)
    cornerRadius = dp(radiusDp).toFloat()
    setStroke(dp(1), 0x1FFFFFFF)
}

fun frameParams(width: Int, height: Int, gravity: Int) =
    FrameLayout.LayoutParams(width, height, gravity)

fun linearParams(width: Int, height: Int, weight: Float = 0f) =
    LinearLayout.LayoutParams(width, height, weight)

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

fun View.setMargins(left: Int = 0, top: Int = 0, right: Int = 0, bottom: Int = 0) {
    (layoutParams as? ViewGroup.MarginLayoutParams)?.setMargins(left, top, right, bottom)
}
