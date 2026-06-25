package com.calltags.app.dialogs

import android.text.format.DateUtils
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.calltags.app.R
import com.calltags.app.activities.SimpleActivity
import com.calltags.app.adapters.CallHistoryAdapter
import com.calltags.app.databinding.DialogCallLabelsBinding
import com.calltags.app.databinding.ItemCallLabelBinding
import com.calltags.app.extensions.addBlockedNumber
import com.calltags.app.extensions.applyColorFilter
import com.calltags.app.extensions.deleteBlockedNumber
import com.calltags.app.extensions.getProperTextColor
import com.calltags.app.extensions.isNumberBlocked
import com.calltags.app.extensions.showCustomDialog
import com.calltags.app.extensions.showKeyboard
import com.calltags.app.extensions.toast
import com.calltags.app.helpers.CallHistoryDb
import com.calltags.app.helpers.ensureBackgroundThread
import com.calltags.app.models.LoggedCall

/**
 * Shows a single call with its labels. Labels can be removed, one new label typed,
 * and the number blocked, which shows up as a special "Blocked" label.
 * Nothing is saved until OK is pressed; [onChanged] fires afterwards if anything was modified.
 */
class CallLabelsDialog(
    private val activity: SimpleActivity,
    private val call: LoggedCall,
    private val onChanged: () -> Unit,
) {
    private val db = CallHistoryDb.getInstance(activity)
    private val binding = DialogCallLabelsBinding.inflate(activity.layoutInflater)
    private val labels = call.labels.toMutableList()

    // whether the number is on the system block list, null until loaded
    private var wasBlocked: Boolean? = null
    private var blocked = false

    init {
        binding.apply {
            val details = arrayListOf(
                DateUtils.formatDateTime(
                    activity,
                    call.date,
                    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR or
                        DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_WEEKDAY
                )
            )
            if (call.duration > 0) details += CallHistoryAdapter.formatDuration(call.duration)
            if (call.name.isNotEmpty()) details += call.number
            callLabelsDetails.text = details.joinToString(" • ")

            callLabelsInput.setTextColor(activity.getProperTextColor())
            callLabelsBlock.isVisible = false
            callLabelsBlock.setOnClickListener {
                blocked = true
                render()
            }
        }
        render()
        loadSuggestions()
        loadBlockedState()

        val builder = MaterialAlertDialogBuilder(activity)
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)

        activity.showCustomDialog(binding.root, builder, title = call.name.ifEmpty { call.number })?.let { dialog ->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                save()
                dialog.dismiss()
            }
            binding.callLabelsInput.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    save()
                    dialog.dismiss()
                    true
                } else {
                    false
                }
            }
            activity.showKeyboard(binding.callLabelsInput)
        }
    }

    private fun loadSuggestions() {
        ensureBackgroundThread {
            val suggestions = db.getAllLabels()
            activity.runOnUiThread {
                binding.callLabelsInput.setAdapter(
                    ArrayAdapter(activity, android.R.layout.simple_dropdown_item_1line, suggestions)
                )
            }
        }
    }

    private fun loadBlockedState() {
        if (call.number.isEmpty()) return
        ensureBackgroundThread {
            val isBlocked = try {
                activity.isNumberBlocked(call.number)
            } catch (_: SecurityException) {
                // only the default phone app can read the block list
                false
            }
            activity.runOnUiThread {
                wasBlocked = isBlocked
                blocked = isBlocked
                render()
            }
        }
    }

    private fun render() {
        val textColor = activity.getProperTextColor()
        val blockedColor = ContextCompat.getColor(activity, R.color.color_missed_call)
        binding.callLabelsList.removeAllViews()

        if (blocked) {
            addRow(activity.getString(R.string.blocked), blockedColor, isBlockedLabel = true) {
                blocked = false
                render()
            }
        }
        labels.forEach { label ->
            addRow(label, textColor, isBlockedLabel = false) {
                labels.remove(label)
                render()
            }
        }

        binding.callLabelsEmpty.isVisible = labels.isEmpty() && !blocked
        binding.callLabelsBlock.isVisible = wasBlocked != null && !blocked
    }

    private fun addRow(text: String, color: Int, isBlockedLabel: Boolean, onRemove: () -> Unit) {
        val row = ItemCallLabelBinding.inflate(activity.layoutInflater, binding.callLabelsList, false)
        row.callLabelText.text = text
        row.callLabelText.setTextColor(color)
        row.callLabelIcon.isVisible = isBlockedLabel
        row.callLabelIcon.applyColorFilter(color)
        row.callLabelRemove.applyColorFilter(color)
        row.callLabelRemove.setOnClickListener { onRemove() }
        binding.callLabelsList.addView(row.root)
    }

    private fun save() {
        val newLabel = binding.callLabelsInput.text.toString().trim()
        val removed = call.labels.filter { it !in labels }
        val added = newLabel.takeIf { label ->
            label.isNotEmpty() && labels.none { it.equals(label, ignoreCase = true) }
        }
        val blockChange = wasBlocked?.let { if (it != blocked) blocked else null }
        if (removed.isEmpty() && added == null && blockChange == null) return

        ensureBackgroundThread {
            removed.forEach { db.removeLabel(call.id, it) }
            added?.let { db.addLabel(call.id, it) }
            when (blockChange) {
                true -> if (activity.addBlockedNumber(call.number)) activity.toast(R.string.number_blocked)
                false -> if (activity.deleteBlockedNumber(call.number)) activity.toast(R.string.number_unblocked)
                null -> {}
            }
            activity.runOnUiThread { onChanged() }
        }
    }
}
