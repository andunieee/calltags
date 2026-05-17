package org.fossify.phone.adapters

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
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.formatPhoneNumber
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.phone.R
import org.fossify.phone.databinding.ItemCallBinding
import org.fossify.phone.extensions.config
import org.fossify.phone.models.LoggedCall

class CallHistoryAdapter(
    private val onCallClick: (LoggedCall) -> Unit,
    private val onDialClick: (LoggedCall) -> Unit,
    private val onLabelClick: (String) -> Unit,
) : ListAdapter<LoggedCall, CallHistoryAdapter.ViewHolder>(DIFF) {

    /** Labels containing this text get highlighted. */
    var highlight: String = ""

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

            val number = when {
                call.number.isEmpty() -> context.getString(R.string.unknown_caller)
                context.config.formatPhoneNumbers -> call.number.formatPhoneNumber()
                else -> call.number
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
                if (call.duration > 0) details += formatDuration(call.duration)
                if (call.name.isNotEmpty()) details += number
                itemCallDetails.text = details.joinToString(" • ")
                itemCallDetails.setTextColor(textColor)

                itemCallType.setImageResource(
                    when (call.type) {
                        Calls.OUTGOING_TYPE -> R.drawable.ic_call_made_vector
                        Calls.INCOMING_TYPE, Calls.ANSWERED_EXTERNALLY_TYPE -> R.drawable.ic_call_received_vector
                        else -> R.drawable.ic_call_missed_vector
                    }
                )
                itemCallType.applyColorFilter(if (isMissed) missedColor else textColor)
                itemCallType.alpha = if (isMissed) 1f else MUTED_ALPHA
                itemCallDial.applyColorFilter(primaryColor)
                itemCallDial.isVisible = call.number.isNotEmpty()

                itemCallLabels.removeAllViews()
                itemCallLabels.isVisible = call.labels.isNotEmpty()
                val query = highlight.trim()
                call.labels.forEach { label ->
                    val matches = query.isNotEmpty() && label.contains(query, ignoreCase = true)
                    itemCallLabels.addView(Chip(context).apply {
                        text = label
                        setEnsureMinTouchTargetSize(false)
                        setTextColor(if (matches) primaryColor.getContrastColor() else textColor)
                        chipBackgroundColor = ColorStateList.valueOf(
                            if (matches) primaryColor else primaryColor.adjustAlpha(CHIP_ALPHA)
                        )
                        chipStrokeWidth = 0f
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
