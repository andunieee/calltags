package com.calltags.app.extensions

import android.graphics.Rect
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewTreeObserver

val View.boundingBox
    get() = Rect().also { getGlobalVisibleRect(it) }

fun View.performHapticFeedback() = performHapticFeedback(
    HapticFeedbackConstants.VIRTUAL_KEY,
    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
)

/** Runs [callback] once, after the next layout pass. */
fun View.onGlobalLayout(callback: () -> Unit) {
    viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
        override fun onGlobalLayout() {
            viewTreeObserver.removeOnGlobalLayoutListener(this)
            callback()
        }
    })
}
