package com.calltags.app.extensions

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import com.calltags.app.R
import com.calltags.app.activities.DialerActivity
import com.calltags.app.activities.SimpleActivity
import com.calltags.app.dialogs.SelectSIMDialog

fun SimpleActivity.startCallIntent(
    recipient: String,
    forceSimSelector: Boolean = false
) {
    if (isDefaultDialer()) {
        getHandleToUse(
            intent = null,
            phoneNumber = recipient,
            forceSimSelector = forceSimSelector
        ) { handle ->
            launchAppCallIntent(recipient, handle)
        }
    } else {
        launchAppCallIntent(recipient, null)
    }
}

private fun SimpleActivity.launchAppCallIntent(recipient: String, handle: PhoneAccountHandle? = null) {
    if (isDefaultDialer()) {
        handlePermission(Manifest.permission.CALL_PHONE) { hasPermission ->
            val action = if (hasPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL
            val intent = Intent(action).apply {
                data = Uri.fromParts("tel", recipient, null)

                if (handle != null) {
                    putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
                }

                if (hasPermission) {
                    setClass(this@launchAppCallIntent, DialerActivity::class.java)
                }
            }

            launchActivityIntent(intent)
        }
        return
    }

    val intent = Intent(Intent.ACTION_DIAL).apply {
        data = Uri.fromParts("tel", recipient, null)
    }

    launchActivityIntent(intent)
}

// used at devices with multiple SIM cards
@SuppressLint("MissingPermission")
fun SimpleActivity.getHandleToUse(
    intent: Intent?,
    phoneNumber: String,
    forceSimSelector: Boolean = false,
    callback: (handle: PhoneAccountHandle?) -> Unit
) {
    handlePermission(Manifest.permission.READ_PHONE_STATE) {
        if (it) {
            val defaultHandle =
                telecomManager.getDefaultOutgoingPhoneAccount(PhoneAccount.SCHEME_TEL)
            when {
                forceSimSelector -> showSelectSimDialog(phoneNumber, callback)
                intent?.hasExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE) == true -> {
                    callback(intent.getParcelableExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE)!!)
                }

                config.getCustomSIM(phoneNumber) != null -> {
                    callback(config.getCustomSIM(phoneNumber))
                }

                defaultHandle != null -> callback(defaultHandle)
                else -> showSelectSimDialog(phoneNumber, callback)
            }
        }
    }
}

fun SimpleActivity.showSelectSimDialog(
    phoneNumber: String,
    callback: (handle: PhoneAccountHandle?) -> Unit
) = SelectSIMDialog(
    activity = this,
    phoneNumber = phoneNumber,
    onDismiss = {
        if (this is DialerActivity) {
            finish()
        }
    }
) { handle ->
    callback(handle)
}

fun SimpleActivity.handleFullScreenNotificationsPermission(callback: (granted: Boolean) -> Unit) {
    handleNotificationPermission { granted ->
        if (granted) {
            if (canUseFullScreenIntent()) {
                callback(true)
            } else {
                showPermissionRequiredDialog(
                    textId = R.string.allow_full_screen_notifications_incoming_calls,
                    onGrant = { openFullScreenIntentSettings() },
                    onCancel = { callback(false) }
                )
            }
        } else {
            showPermissionRequiredDialog(
                textId = R.string.allow_notifications_incoming_calls,
                onGrant = { openNotificationSettings() },
                onCancel = { callback(false) }
            )
        }
    }
}
