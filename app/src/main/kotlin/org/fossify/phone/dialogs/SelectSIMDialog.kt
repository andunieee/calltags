package org.fossify.phone.dialogs

import android.annotation.SuppressLint
import android.app.Activity
import android.telecom.PhoneAccountHandle
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.fossify.phone.R
import org.fossify.phone.databinding.DialogSelectSimBinding
import org.fossify.phone.extensions.config
import org.fossify.phone.extensions.getAvailableSIMCardLabels
import org.fossify.phone.extensions.showCustomDialog

@SuppressLint("MissingPermission", "SetTextI18n")
class SelectSIMDialog(
    val activity: Activity,
    val phoneNumber: String,
    onDismiss: () -> Unit = {},
    val callback: (handle: PhoneAccountHandle?) -> Unit
) {
    private var dialog: AlertDialog? = null
    private val binding = DialogSelectSimBinding.inflate(activity.layoutInflater)

    init {
        binding.selectSimRememberHolder.setOnClickListener {
            binding.selectSimRemember.toggle()
        }

        activity.getAvailableSIMCardLabels().forEachIndexed { index, SIMAccount ->
            val radioButton = (activity.layoutInflater.inflate(R.layout.radio_button, null) as RadioButton).apply {
                text = "${index + 1} - ${SIMAccount.label}"
                id = index
                setOnClickListener { selectedSIM(SIMAccount.handle) }
            }
            binding.selectSimRadioGroup.addView(radioButton, RadioGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        dialog = activity.showCustomDialog(binding.root, MaterialAlertDialogBuilder(activity))
        dialog?.setOnDismissListener {
            onDismiss()
        }
    }

    private fun selectedSIM(handle: PhoneAccountHandle) {
        if (binding.selectSimRemember.isChecked) {
            activity.config.saveCustomSIM(phoneNumber, handle)
        }

        callback(handle)
        dialog?.dismiss()
    }
}
