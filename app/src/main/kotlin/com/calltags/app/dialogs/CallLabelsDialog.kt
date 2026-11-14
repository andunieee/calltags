package com.calltags.app.dialogs

import android.content.res.ColorStateList
import android.text.format.DateUtils
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.TooltipCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.calltags.app.R
import com.calltags.app.activities.SimpleActivity
import com.calltags.app.adapters.CallHistoryAdapter
import com.calltags.app.databinding.DialogCallLabelsBinding
import com.calltags.app.databinding.ItemCallLabelBinding
import com.calltags.app.extensions.adjustAlpha
import com.calltags.app.extensions.applyColorFilter
import com.calltags.app.extensions.getLabelColor
import com.calltags.app.extensions.getNumberLabelColor
import com.calltags.app.extensions.getProperTextColor
import com.calltags.app.extensions.showCustomDialog
import com.calltags.app.extensions.showKeyboard
import com.calltags.app.helpers.CallHistoryDb
import com.calltags.app.helpers.ensureBackgroundThread
import com.calltags.app.models.CallLabel
import com.calltags.app.models.LabelProperties
import com.calltags.app.models.LoggedCall

/**
 * Shows a single call with its labels. Labels can be removed, one new label typed (as a number or call label,
 * toggled by the icon next to the input, see [CallLabel]), and the labels set to be suggested added with one tap.
 * The number labels other calls from this number got are listed faded; removing one takes it off the number.
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
    private val otherNumberLabels = call.otherNumberLabels.toMutableList()
    private var isNumberLabel = true

    // empty until loaded
    private var properties = emptyMap<String, LabelProperties>()

    // existing labels offered while typing, only for call labels; null until loaded
    private var suggestions: ArrayAdapter<String>? = null

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
            callLabelsScope.setOnClickListener {
                isNumberLabel = !isNumberLabel
                renderScope()
            }
            renderScope()
        }
        render()
        loadSuggestions()

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
            val labels = db.getAllLabels()
            val properties = db.getLabelProperties()
            activity.runOnUiThread {
                suggestions = ArrayAdapter(activity, android.R.layout.simple_dropdown_item_1line, labels)
                this.properties = properties
                renderScope()
                render()
            }
        }
    }

    private fun hasLabel(label: String) =
        labels.any { it.text.equals(label, ignoreCase = true) } || otherNumberLabels.any { it.equals(label, ignoreCase = true) }

    private fun render() {
        val textColor = activity.getProperTextColor()
        binding.callLabelsList.removeAllViews()

        labels.forEach { label ->
            val color = if (label.isNumberLabel) numberLabelColor(label.text) else activity.getLabelColor(false)
            addRow(label.text, scopeIcon(label.isNumberLabel), color, textColor) {
                labels.remove(label)
                render()
            }
        }
        otherNumberLabels.forEach { label ->
            addRow(label, scopeIcon(true), numberLabelColor(label), textColor, faded = true) {
                otherNumberLabels.remove(label)
                render()
            }
        }

        binding.callLabelsSuggestions.apply {
            removeAllViews()
            val suggested = if (call.number.isEmpty()) emptyList() else {
                properties.values.filter { it.suggest && !hasLabel(it.label) }.sortedBy { it.label.lowercase() }
            }
            suggested.forEach { suggestion ->
                val color = activity.getNumberLabelColor(suggestion)
                addView(Chip(activity).apply {
                    text = suggestion.label
                    setEnsureMinTouchTargetSize(false)
                    setTextColor(textColor)
                    chipIcon = ContextCompat.getDrawable(activity, R.drawable.ic_label_number_vector)
                    chipIconTint = ColorStateList.valueOf(color)
                    chipBackgroundColor = ColorStateList.valueOf(color.adjustAlpha(CHIP_ALPHA))
                    chipStrokeWidth = 0f
                    contentDescription = activity.getString(R.string.add_label, suggestion.label)
                    setOnClickListener {
                        labels += CallLabel(suggestion.label, isNumberLabel = true)
                        render()
                    }
                })
            }
            isVisible = suggested.isNotEmpty()
        }
    }

    private fun numberLabelColor(label: String) = activity.getNumberLabelColor(properties[label.lowercase()])

    private fun renderScope() {
        binding.callLabelsScope.apply {
            setImageResource(scopeIcon(isNumberLabel))
            applyColorFilter(activity.getLabelColor(isNumberLabel))
            contentDescription = activity.getString(if (isNumberLabel) R.string.number_label else R.string.call_label)
            TooltipCompat.setTooltipText(this, contentDescription)
        }
        binding.callLabelsInput.apply {
            if (isNumberLabel) dismissDropDown()
            setAdapter(if (isNumberLabel) null else suggestions)
        }
    }

    private fun scopeIcon(isNumberLabel: Boolean) =
        if (isNumberLabel) R.drawable.ic_label_number_vector else R.drawable.ic_label_call_vector

    private fun addRow(
        text: String,
        icon: Int,
        iconColor: Int,
        textColor: Int,
        faded: Boolean = false,
        onRemove: () -> Unit,
    ) {
        val row = ItemCallLabelBinding.inflate(activity.layoutInflater, binding.callLabelsList, false)
        row.callLabelText.text = text
        row.callLabelText.setTextColor(textColor)
        row.callLabelIcon.setImageResource(icon)
        row.callLabelIcon.applyColorFilter(iconColor)
        row.callLabelRemove.applyColorFilter(textColor)
        row.callLabelRemove.setOnClickListener { onRemove() }
        if (faded) {
            row.callLabelText.alpha = OTHER_CALL_LABEL_ALPHA
            row.callLabelIcon.alpha = OTHER_CALL_LABEL_ALPHA
        }
        binding.callLabelsList.addView(row.root)
    }

    private fun save() {
        val newLabel = binding.callLabelsInput.text.toString().trim()
        val removed = call.labels.filter { it !in labels }
        val removedFromNumber = call.otherNumberLabels.filter { it !in otherNumberLabels }
        val added = labels.filter { it !in call.labels }.toMutableList()
        if (newLabel.isNotEmpty() && !hasLabel(newLabel)) added += CallLabel(newLabel, isNumberLabel)
        if (removed.isEmpty() && removedFromNumber.isEmpty() && added.isEmpty()) return

        ensureBackgroundThread {
            removed.forEach { db.removeLabel(call.id, it.text) }
            removedFromNumber.forEach { db.removeNumberLabel(call.number, it) }
            added.forEach { db.addLabel(call.id, it.text, it.isNumberLabel) }
            activity.runOnUiThread { onChanged() }
        }
    }

    companion object {
        private const val CHIP_ALPHA = 0.22f
        private const val OTHER_CALL_LABEL_ALPHA = 0.55f
    }
}
