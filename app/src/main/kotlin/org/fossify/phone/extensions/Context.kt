package org.fossify.phone.extensions

import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Context.KEYGUARD_SERVICE
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.telecom.TelecomManager
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import org.fossify.phone.R
import org.fossify.phone.helpers.Config
import org.fossify.phone.helpers.ensureBackgroundThread

val Context.config: Config get() = Config.newInstance(applicationContext)

val Context.audioManager: AudioManager
    get() = getSystemService(Context.AUDIO_SERVICE) as AudioManager

val Context.powerManager: PowerManager
    get() = getSystemService(Context.POWER_SERVICE) as PowerManager

val Context.keyguardManager: KeyguardManager
    get() = getSystemService(KEYGUARD_SERVICE) as KeyguardManager

val Context.telecomManager: TelecomManager
    get() = getSystemService(Context.TELECOM_SERVICE) as TelecomManager

val Context.notificationManager: NotificationManager
    get() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

val Context.isRTLLayout: Boolean
    get() = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL

fun Context.toast(id: Int, length: Int = Toast.LENGTH_SHORT) = toast(getString(id), length)

fun Context.toast(msg: String, length: Int = Toast.LENGTH_SHORT) {
    val show = { Toast.makeText(applicationContext, msg, length).show() }
    if (Looper.myLooper() == Looper.getMainLooper()) {
        show()
    } else {
        Handler(Looper.getMainLooper()).post(show)
    }
}

fun Context.showErrorToast(exception: Exception) {
    toast(getString(R.string.unknown_error_occurred) + ": " + exception, Toast.LENGTH_LONG)
}

fun Context.hasPermission(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

fun Context.isDefaultDialer(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = getSystemService(RoleManager::class.java)
        roleManager.isRoleAvailable(RoleManager.ROLE_DIALER) && roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
    } else {
        telecomManager.defaultDialerPackage == packageName
    }
}

fun Context.canUseFullScreenIntent(): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || notificationManager.canUseFullScreenIntent()
}

fun Context.openNotificationSettings() {
    startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    )
}

fun Context.openFullScreenIntentSettings() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        startActivity(
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.fromParts("package", packageName, null))
        )
    }
}

fun Context.clearMissedCalls() {
    ensureBackgroundThread {
        try {
            // notification cancellation triggers MissedCallNotifier.clearMissedCalls() which, in turn,
            // should update the database and reset the cached missed call count in MissedCallNotifier.java
            // https://android.googlesource.com/platform/packages/services/Telecomm/+/master/src/com/android/server/telecom/ui/MissedCallNotifierImpl.java#170
            telecomManager.cancelMissedCallsNotification()
        } catch (ignored: Exception) {
        }
    }
}
