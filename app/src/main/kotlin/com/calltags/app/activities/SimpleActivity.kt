package com.calltags.app.activities

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import android.view.View
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat.Type
import androidx.core.view.updatePadding
import com.calltags.app.R
import com.calltags.app.extensions.getProperBackgroundColor
import com.calltags.app.extensions.hasPermission
import com.calltags.app.extensions.isLightColor
import com.calltags.app.extensions.showErrorToast
import com.calltags.app.extensions.toast
import com.calltags.app.helpers.REQUEST_CODE_SET_DEFAULT_DIALER

open class SimpleActivity : AppCompatActivity() {
    private var onPermissionResult: ((granted: Boolean) -> Unit)? = null
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            onPermissionResult?.invoke(granted)
            onPermissionResult = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.enableEdgeToEdge(window)
        onBackPressedDispatcher.addCallback(this) {
            if (!onBackPressedCompat()) {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val lightBars = getProperBackgroundColor().isLightColor()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = lightBars
            isAppearanceLightNavigationBars = lightBars
        }
    }

    /** Return true if the back press was consumed. */
    protected open fun onBackPressedCompat(): Boolean = false

    /** Pads the given views so their content stays clear of the system bars, keyboard and display cutout. */
    fun setupEdgeToEdge(
        padTopSystem: List<View> = emptyList(),
        padBottomSystem: List<View> = emptyList(),
        padBottomImeAndSystem: List<View> = emptyList(),
    ) {
        val contentRoot = findViewById<View>(android.R.id.content)
        val allViews = padTopSystem + padBottomSystem + padBottomImeAndSystem + contentRoot
        val basePadding = allViews.associateWith {
            intArrayOf(it.paddingLeft, it.paddingTop, it.paddingRight, it.paddingBottom)
        }

        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { view, insets ->
            val system = insets.getInsetsIgnoringVisibility(Type.systemBars())
            val imeAndSystem = insets.getInsets(Type.ime() or Type.systemBars())
            val cutout = insets.getInsets(Type.displayCutout())

            padTopSystem.forEach { it.updatePadding(top = basePadding.getValue(it)[1] + system.top) }
            padBottomSystem.forEach { it.updatePadding(bottom = basePadding.getValue(it)[3] + system.bottom) }
            padBottomImeAndSystem.forEach {
                it.updatePadding(bottom = basePadding.getValue(it)[3] + imeAndSystem.bottom)
            }
            basePadding.getValue(contentRoot).let { base ->
                contentRoot.updatePadding(
                    left = base[0] + maxOf(system.left, cutout.left),
                    right = base[2] + maxOf(system.right, cutout.right)
                )
            }
            ViewCompat.onApplyWindowInsets(view, insets)
        }
    }

    fun handlePermission(permission: String, callback: (granted: Boolean) -> Unit) {
        if (hasPermission(permission)) {
            callback(true)
        } else {
            onPermissionResult = callback
            permissionLauncher.launch(permission)
        }
    }

    fun handleNotificationPermission(callback: (granted: Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            callback(true)
        } else {
            handlePermission(android.Manifest.permission.POST_NOTIFICATIONS, callback)
        }
    }

    /** The result arrives in onActivityResult with [REQUEST_CODE_SET_DEFAULT_DIALER]. */
    @Suppress("DEPRECATION")
    protected fun launchSetDefaultDialerIntent() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            val canRequest = roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)
            if (canRequest && !roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                startActivityForResult(intent, REQUEST_CODE_SET_DEFAULT_DIALER)
            }
        } else {
            val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
            try {
                startActivityForResult(intent, REQUEST_CODE_SET_DEFAULT_DIALER)
            } catch (_: ActivityNotFoundException) {
                toast(R.string.no_app_found)
            } catch (e: SecurityException) {
                showErrorToast(e)
            }
        }
    }
}
