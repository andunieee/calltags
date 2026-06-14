package com.calltags.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.calltags.app.activities.CallActivity
import com.calltags.app.helpers.ACCEPT_CALL
import com.calltags.app.helpers.CallManager
import com.calltags.app.helpers.DECLINE_CALL

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACCEPT_CALL -> {
                context.startActivity(CallActivity.getStartIntent(context))
                CallManager.accept()
            }

            DECLINE_CALL -> CallManager.reject()
        }
    }
}
