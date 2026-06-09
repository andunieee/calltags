package org.fossify.phone.extensions

import android.graphics.Color
import androidx.core.graphics.ColorUtils
import kotlin.math.roundToInt

private const val DARK_GREY = 0xFF333333.toInt()
private const val WCAG_AA_NORMAL = 4.5
private const val LUMINANCE_MIDPOINT = 0.5
private const val CONTRAST_BLEND_STEP = 0.05f
private const val PERCENT = 100f
private const val OPAQUE = 255
private const val HSL_LIGHTNESS = 2
private const val HSL_COMPONENTS = 3

/** A readable foreground color for text or icons drawn on top of this color. */
fun Int.getContrastColor() = if (ColorUtils.calculateLuminance(this) > LUMINANCE_MIDPOINT) DARK_GREY else Color.WHITE

fun Int.isLightColor() = getContrastColor() == DARK_GREY

fun Int.adjustAlpha(factor: Float): Int =
    ColorUtils.setAlphaComponent(this, (Color.alpha(this) * factor).roundToInt())

fun Int.darkenColor(factor: Int = 8) = shiftLightness(-factor)

fun Int.lightenColor(factor: Int = 8) = shiftLightness(factor)

private fun Int.shiftLightness(percent: Int): Int {
    if (this == Color.WHITE || this == Color.BLACK) return this
    val hsl = FloatArray(HSL_COMPONENTS)
    ColorUtils.colorToHSL(this, hsl)
    hsl[HSL_LIGHTNESS] = (hsl[HSL_LIGHTNESS] + percent / PERCENT).coerceIn(0f, 1f)
    return ColorUtils.HSLToColor(hsl)
}

/** Blends this color towards black or white until it is readable on [background]. */
fun Int.adjustForContrast(background: Int, minContrast: Double = WCAG_AA_NORMAL): Int {
    val target = if (ColorUtils.calculateLuminance(background) < LUMINANCE_MIDPOINT) Color.WHITE else Color.BLACK
    var color = this
    var ratio = 0f
    fun contrast(c: Int) = ColorUtils.calculateContrast(ColorUtils.setAlphaComponent(c, OPAQUE), background)
    while (contrast(color) < minContrast && ratio < 1f) {
        ratio += CONTRAST_BLEND_STEP
        color = ColorUtils.blendARGB(this, target, ratio)
    }
    return color
}
