package com.calltags.app.adapters

import android.content.res.ColorStateList
import android.provider.CallLog.Calls
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.calltags.app.R
import com.calltags.app.databinding.ItemCallBinding
import com.calltags.app.extensions.adjustAlpha
import com.calltags.app.extensions.applyColorFilter
import com.calltags.app.extensions.formatPhoneNumber
import com.calltags.app.extensions.getContrastColor
import com.calltags.app.extensions.getLabelColor
import com.calltags.app.extensions.getNumberLabelColor
import com.calltags.app.extensions.getProperPrimaryColor
import com.calltags.app.extensions.getProperTextColor
import com.calltags.app.models.LabelProperties
import com.calltags.app.models.LoggedCall

class CallHistoryAdapter(
    private val onCallClick: (LoggedCall) -> Unit,
    private val onDialClick: (LoggedCall) -> Unit,
    private val onLabelClick: (String) -> Unit,
) : ListAdapter<LoggedCall, CallHistoryAdapter.ViewHolder>(DIFF) {

    /** Labels for which this returns true get highlighted. */
    var highlight: (String) -> Boolean = { false }

    /** Label properties by lowercase label, for the number label colors. */
    var properties: Map<String, LabelProperties> = emptyMap()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemCallBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemCallBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(call: LoggedCall) {
            val context = binding.root.context
            val textColor = context.getProperTextColor()
            val primaryColor = context.getProperPrimaryColor()
            val missedColor = ContextCompat.getColor(context, R.color.color_missed_call)
            val isMissed = call.type == Calls.MISSED_TYPE || call.type == Calls.REJECTED_TYPE || call.type == Calls.BLOCKED_TYPE
            val isPlaceholder = call.type == LoggedCall.PLACEHOLDER_TYPE

            val number = when {
                call.number.isEmpty() -> context.getString(R.string.unknown_caller)
                else -> call.number.formatPhoneNumber()
            }

            binding.apply {
                itemCallTitle.text = call.name.ifEmpty { number }
                itemCallTitle.setTextColor(if (isMissed) missedColor else textColor)

                val details = arrayListOf(
                    DateUtils.formatDateTime(
                        context,
                        call.date,
                        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH
                    )
                )
                if (call.type == Calls.BLOCKED_TYPE) details += context.getString(R.string.blocked)
                if (isPlaceholder) details[0] = context.getString(R.string.no_calls_yet)
                if (call.duration > 0) details += formatDuration(call.duration)
                if (call.name.isNotEmpty()) details += number
                itemCallDetails.text = details.joinToString(" • ")
                itemCallDetails.setTextColor(textColor)

                itemCallType.setImageResource(
                    when (call.type) {
                        Calls.OUTGOING_TYPE -> R.drawable.ic_call_made_vector
                        Calls.INCOMING_TYPE, Calls.ANSWERED_EXTERNALLY_TYPE -> R.drawable.ic_call_received_vector
                        LoggedCall.PLACEHOLDER_TYPE -> R.drawable.ic_label_number_vector
                        else -> R.drawable.ic_call_missed_vector
                    }
                )
                itemCallType.applyColorFilter(if (isMissed) missedColor else textColor)
                itemCallType.alpha = if (isMissed) 1f else MUTED_ALPHA
                itemCallDial.applyColorFilter(primaryColor)
                itemCallDial.isVisible = call.number.isNotEmpty()

                itemCallLabels.removeAllViews()
                itemCallLabels.isVisible = call.labels.isNotEmpty() || call.otherNumberLabels.isNotEmpty()
                // this call's own labels, then faded the ones its number got on other calls
                val chips = call.labels.map { Triple(it.text, it.isNumberLabel, false) } +
                    call.otherNumberLabels.map { Triple(it, true, true) }
                chips.forEach { (label, isNumberLabel, fromOtherCall) ->
                    val matches = highlight(label)
                    val color = if (isNumberLabel) {
                        context.getNumberLabelColor(properties[label.lowercase()])
                    } else {
                        context.getLabelColor(false)
                    }
                    itemCallLabels.addView(Chip(context).apply {
                        text = label
                        setEnsureMinTouchTargetSize(false)
                        setTextColor(if (matches) color.getContrastColor() else textColor)
                        chipBackgroundColor = ColorStateList.valueOf(
                            if (matches) color else color.adjustAlpha(CHIP_ALPHA)
                        )
                        chipStrokeWidth = 0f
                        if (fromOtherCall) alpha = OTHER_CALL_LABEL_ALPHA
                        setOnClickListener { onLabelClick(label) }
                    })
                }

                root.setOnClickListener { onCallClick(call) }
                itemCallDial.setOnClickListener { onDialClick(call) }
            }
        }
    }

    companion object {
        private const val MUTED_ALPHA = 0.5f
        private const val CHIP_ALPHA = 0.22f
        private const val OTHER_CALL_LABEL_ALPHA = 0.45f

        fun formatDuration(seconds: Int): String {
            val h = seconds / 3600
            val m = seconds % 3600 / 60
            val s = seconds % 60
            return when {
                h > 0 -> "${h}h ${m}m"
                m > 0 -> "${m}m ${s}s"
                else -> "${s}s"
            }
        }

        private val DIFF = object : DiffUtil.ItemCallback<LoggedCall>() {
            override fun areItemsTheSame(oldItem: LoggedCall, newItem: LoggedCall) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: LoggedCall, newItem: LoggedCall) = oldItem == newItem
        }
    }
}
