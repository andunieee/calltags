package org.fossify.phone.extensions

import android.content.Context
import android.content.res.Configuration
import androidx.core.content.ContextCompat
import org.fossify.commons.extensions.baseConfig
import org.fossify.phone.R

/**
 * Pins the app to the CallTags palette, following the system light/dark setting.
 * Commons would otherwise use wallpaper-based Material You colors on Android 12+.
 */
fun Context.applyBrandTheme() {
    val isNight = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    fun color(id: Int) = ContextCompat.getColor(this, id)

    baseConfig.apply {
        isSystemThemeEnabled = false
        isGlobalThemeEnabled = false
        backgroundColor = color(if (isNight) R.color.brand_night else R.color.brand_paper)
        textColor = color(if (isNight) R.color.brand_night_text else R.color.brand_ink)
        primaryColor = color(if (isNight) R.color.brand_amber else R.color.brand_amber_deep)
        accentColor = primaryColor
        appIconColor = color(R.color.brand_amber)
    }
}
