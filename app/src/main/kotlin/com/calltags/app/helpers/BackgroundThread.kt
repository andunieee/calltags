package com.calltags.app.helpers

import android.os.Looper

fun ensureBackgroundThread(callback: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
        Thread { callback() }.start()
    } else {
        callback()
    }
}
