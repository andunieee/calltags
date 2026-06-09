package org.fossify.phone.helpers

import android.os.Looper

fun ensureBackgroundThread(callback: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
        Thread { callback() }.start()
    } else {
        callback()
    }
}
