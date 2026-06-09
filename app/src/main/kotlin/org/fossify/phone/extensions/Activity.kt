package org.fossify.phone.extensions

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.viewbinding.ViewBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.fossify.phone.R

/** `private val binding by viewBinding(ActivityMainBinding::inflate)` */
inline fun <T : ViewBinding> Activity.viewBinding(crossinline inflate: (LayoutInflater) -> T) =
    lazy(LazyThreadSafetyMode.NONE) { inflate(layoutInflater) }

fun Activity.hideKeyboard() {
    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow((currentFocus ?: View(this)).windowToken, 0)
    window.decorView.clearFocus()
}

fun Activity.showKeyboard(editText: EditText) {
    editText.requestFocus()
    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
}

fun Activity.launchActivityIntent(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        toast(R.string.no_app_found)
    } catch (e: SecurityException) {
        showErrorToast(e)
    }
}

/** Shows [message] with Yes/No buttons and calls [onConfirmed] on Yes. */
fun Activity.showConfirmationDialog(message: String, onConfirmed: () -> Unit) {
    if (isFinishing || isDestroyed) return
    MaterialAlertDialogBuilder(this)
        .setMessage(message)
        .setPositiveButton(R.string.yes) { _, _ -> onConfirmed() }
        .setNegativeButton(R.string.no, null)
        .show()
}

/** Explains why a permission is needed and offers to open the matching settings screen. */
fun Activity.showPermissionRequiredDialog(textId: Int, onGrant: () -> Unit, onCancel: () -> Unit) {
    if (isFinishing || isDestroyed) return
    MaterialAlertDialogBuilder(this)
        .setTitle(R.string.permission_required)
        .setMessage(textId)
        .setPositiveButton(R.string.grant_permission) { _, _ -> onGrant() }
        .setNegativeButton(R.string.cancel) { _, _ -> onCancel() }
        .setCancelable(false)
        .show()
}

/** Shows a dialog with a custom [view], returning null if the activity is going away. */
fun Activity.showCustomDialog(
    view: View,
    builder: MaterialAlertDialogBuilder,
    title: String? = null,
): AlertDialog? {
    if (isFinishing || isDestroyed) return null
    if (title != null) builder.setTitle(title)
    return builder.setView(view).show()
}
