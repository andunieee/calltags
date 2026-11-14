package com.calltags.app.services

import android.content.Context
import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import com.calltags.app.helpers.CallHistoryDb
import com.calltags.app.helpers.minuteOfDay
import com.calltags.app.models.Screening

/**
 * Applies the label properties to incoming calls, see [CallHistoryDb.screen].
 *
 * Blocked calls are silently rejected. The call log entry is kept ([CallResponse.Builder.setSkipCallLog] is false),
 * so they still show in History as blocked, they just never ring. Silenced calls come in as usual without ringing;
 * before Android 10 screening can't do that, so [CallService] silences the ringer instead.
 */
class SimpleCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val screening = if (callDetails.isIncoming()) screenNumber(this, callDetails.handle?.schemeSpecificPart) else Screening.RING
        val blocked = screening == Screening.BLOCK

        val response = CallResponse.Builder()
            .setDisallowCall(blocked)
            .setRejectCall(blocked)
            .setSkipCallLog(false)
            .setSkipNotification(blocked)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            response.setSilenceCall(screening == Screening.SILENCE)
        }
        respondToCall(callDetails, response.build())
    }

    private fun Call.Details.isIncoming() =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || callDirection == Call.Details.DIRECTION_INCOMING

    companion object {
        fun screenNumber(context: Context, number: String?): Screening = try {
            CallHistoryDb.getInstance(context).screen(number.orEmpty(), minuteOfDay())
        } catch (_: Exception) {
            // never block or silence everything because of a database problem
            Screening.RING
        }
    }
}
