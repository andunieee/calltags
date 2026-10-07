package com.calltags.app.services

import android.telecom.Call
import android.telecom.CallScreeningService
import com.calltags.app.extensions.isNumberBlocked

/**
 * Silently rejects calls from numbers on the system block list.
 *
 * The call log entry is kept ([CallResponse.Builder.setSkipCallLog] is false),
 * so blocked calls still show in History as blocked, they just never ring.
 */
class SimpleCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart
        val blocked = try {
            !number.isNullOrEmpty() && isNumberBlocked(number)
        } catch (_: Exception) {
            // never block everything because of a missing permission or a provider error
            false
        }

        val response = CallResponse.Builder()
            .setDisallowCall(blocked)
            .setRejectCall(blocked)
            .setSkipCallLog(false)
            .setSkipNotification(blocked)
            .build()
        respondToCall(callDetails, response)
    }
}
