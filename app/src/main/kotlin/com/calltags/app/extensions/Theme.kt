package com.calltags.app.extensions

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.calltags.app.R

// The CallTags palette lives in values/colors.xml and values-night/colors.xml,
// so these follow the system light/dark setting automatically.
fun Context.getProperTextColor() = ContextCompat.getColor(this, R.color.color_text)

fun Context.getProperBackgroundColor() = ContextCompat.getColor(this, R.color.color_background)

fun Context.getProperPrimaryColor() = ContextCompat.getColor(this, R.color.color_primary)

fun Context.isSystemInDarkMode() =
    resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

fun ImageView.applyColorFilter(color: Int) = setColorFilter(color, PorterDuff.Mode.SRC_IN)

fun Drawable.applyColorFilter(color: Int) {
    mutate().colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
}

fun Resources.getColoredDrawableWithColor(drawableId: Int, color: Int): Drawable {
    val drawable = DrawableCompat.wrap(getDrawable(drawableId, null).mutate())
    DrawableCompat.setTint(drawable, color)
    return drawable
}
