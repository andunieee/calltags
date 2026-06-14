package com.calltags.app.helpers

import android.content.Context
import android.net.Uri
import android.telecom.Call
import com.calltags.app.R
import com.calltags.app.extensions.formatPhoneNumber
import com.calltags.app.extensions.isConference
import com.calltags.app.models.CallContact

fun getCallContact(context: Context, call: Call?, callback: (CallContact) -> Unit) {
    if (call.isConference()) {
        callback(CallContact(context.getString(R.string.conference), "", "", ""))
        return
    }

    ensureBackgroundThread {
        val callContact = CallContact("", "", "", "")
        val handle = try {
            call?.details?.handle?.toString()
        } catch (e: NullPointerException) {
            null
        }

        if (handle == null) {
            callback(callContact)
            return@ensureBackgroundThread
        }

        val uri = Uri.decode(handle)
        if (uri.startsWith("tel:")) {
            val number = uri.substringAfter("tel:")
            callContact.number = number.formatPhoneNumber()
            callContact.name = callContact.number
            callback(callContact)
        }
    }
}
