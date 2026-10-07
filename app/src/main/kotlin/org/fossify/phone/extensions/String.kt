package org.fossify.phone.extensions

import android.telephony.PhoneNumberUtils
import java.util.Locale

private const val MIN_FORMATTABLE_NUMBER_LENGTH = 4

fun String.formatPhoneNumber(): String {
    return if (length >= MIN_FORMATTABLE_NUMBER_LENGTH) {
        PhoneNumberUtils.formatNumber(this, Locale.getDefault().country) ?: this
    } else {
        this
    }
}
