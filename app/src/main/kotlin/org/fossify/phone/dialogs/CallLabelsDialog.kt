package org.fossify.phone.dialogs

import android.content.res.ColorStateList
import android.text.format.DateUtils
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import com.google.android.material.chip.Chip
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.extensions.addBlockedNumber
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.deleteBlockedNumber
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.isNumberBlocked
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.showKeyboard
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.phone.R
import org.fossify.phone.activities.SimpleActivity
import org.fossify.phone.adapters.CallHistoryAdapter
import org.fossify.phone.databinding.DialogCallLabelsBinding
import org.fossify.phone.helpers.CallHistoryDb
import org.fossify.phone.models.LoggedCall

/**
 * Shows a single call and lets the user add or remove its labels.
 * [onChanged] fires after the dialog closes if anything was modified.
 */
class CallLabelsDialog(
    private val activity: SimpleActivity,
    private val call: LoggedCall,
    private val onChanged: () -> Unit,
) {
    private val db = CallHistoryDb.getInstance(activity)
    private val binding = DialogCallLabelsBinding.inflate(activity.layoutInflater)
    private val labels = call.labels.toMutableList()
    private var changed = false

    init {
        val textColor = activity.getProperTextColor()
        binding.apply {
            val details = arrayListOf(
                DateUtils.formatDateTime(
                    activity,
                    call.date,
                    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_WEEKDAY
                )
            )
            if (call.duration > 0) details += CallHistoryAdapter.formatDuration(call.duration)
            if (call.name.isNotEmpty()) details += call.number
            callLabelsDetails.text = details.joinToString(" • ")

            callLabelsAdd.applyColorFilter(activity.getProperPrimaryColor())
            callLabelsAdd.setOnClickListener { addTypedLabel() }
            callLabelsInput.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    addTypedLabel()
                    true
                } else {
                    false
                }
            }
            callLabelsInput.setTextColor(textColor)
        }
        renderChips()

        ensureBackgroundThread {
            val suggestions = db.getAllLabels()
            activity.runOnUiThread {
                binding.callLabelsInput.setAdapter(
                    ArrayAdapter(activity, android.R.layout.simple_dropdown_item_1line, suggestions)
                )
            }
        }

        val builder = activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.delete, null)
            .setNeutralButton(R.string.block_number, null)
            .setOnDismissListener {
                if (changed) onChanged()
            }

        activity.setupDialogStuff(binding.root, builder, titleText = call.name.ifEmpty { call.number }) { dialog ->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                addTypedLabel()
                dialog.dismiss()
            }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                ConfirmationDialog(activity, activity.getString(R.string.delete_call_confirmation)) {
                    db.deleteCall(call.id)
                    changed = true
                    dialog.dismiss()
                }
            }
            setupBlockButton(dialog)
            activity.showKeyboard(binding.callLabelsInput)
        }
    }

    private fun addTypedLabel() {
        val label = binding.callLabelsInput.text.toString().trim()
        binding.callLabelsInput.setText("")
        if (label.isEmpty() || labels.any { it.equals(label, ignoreCase = true) }) return

        labels += label
        changed = true
        renderChips()
        db.addLabel(call.id, label)
    }

    private fun removeLabel(label: String) {
        labels.remove(label)
        changed = true
        renderChips()
        db.removeLabel(call.id, label)
    }

    private fun setupBlockButton(dialog: AlertDialog) {
        val blockButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
        if (call.number.isEmpty()) {
            blockButton.beGone()
            return
        }

        var blocked = false
        ensureBackgroundThread {
            blocked = try {
                activity.isNumberBlocked(call.number)
            } catch (_: Exception) {
                false
            }
            activity.runOnUiThread {
                blockButton.text = activity.getString(if (blocked) R.string.unblock_number else R.string.block_number)
            }
        }
        blockButton.setOnClickListener {
            if (blocked) {
                ensureBackgroundThread {
                    activity.deleteBlockedNumber(call.number)
                    activity.runOnUiThread {
                        blocked = false
                        blockButton.text = activity.getString(R.string.block_number)
                        activity.toast(R.string.number_unblocked)
                    }
                }
            } else {
                ConfirmationDialog(activity, activity.getString(R.string.block_number_confirmation, call.number)) {
                    ensureBackgroundThread {
                        if (activity.addBlockedNumber(call.number)) {
                            activity.runOnUiThread {
                                blocked = true
                                blockButton.text = activity.getString(R.string.unblock_number)
                                activity.toast(R.string.number_blocked)
                            }
                        } else {
                            activity.runOnUiThread {
                                activity.toast(R.string.unknown_error_occurred)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun renderChips() {
        val textColor = activity.getProperTextColor()
        binding.callLabelsChips.removeAllViews()
        labels.forEach { label ->
            binding.callLabelsChips.addView(Chip(activity).apply {
                text = label
                setTextColor(textColor)
                chipBackgroundColor = ColorStateList.valueOf(activity.getProperPrimaryColor().adjustAlpha(0.22f))
                chipStrokeWidth = 0f
                isCloseIconVisible = true
                closeIconTint = ColorStateList.valueOf(textColor)
                setOnCloseIconClickListener { removeLabel(label) }
            })
        }
        binding.callLabelsEmpty.isVisible = labels.isEmpty()
    }
}
