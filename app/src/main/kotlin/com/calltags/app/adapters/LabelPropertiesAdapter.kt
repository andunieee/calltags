package com.calltags.app.adapters

import android.app.TimePickerDialog
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.widget.TooltipCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.calltags.app.R
import com.calltags.app.databinding.ItemLabelHoursBinding
import com.calltags.app.databinding.ItemLabelPropertiesBinding
import com.calltags.app.extensions.applyColorFilter
import com.calltags.app.extensions.getContrastColor
import com.calltags.app.extensions.getNumberLabelColor
import com.calltags.app.extensions.getProperPrimaryColor
import com.calltags.app.helpers.formatMinuteOfDay
import com.calltags.app.helpers.hourRange
import com.calltags.app.models.HourRange
import com.calltags.app.models.LabelProperties
import com.calltags.app.models.LabelUsage

/** One card per label with all its properties; every change is handed to [onChange] right away. */
class LabelPropertiesAdapter(
    private val onChange: (LabelProperties) -> Unit,
) : ListAdapter<LabelUsage, LabelPropertiesAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemLabelPropertiesBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemLabelPropertiesBinding) : RecyclerView.ViewHolder(binding.root) {
        private val context = binding.root.context

        fun bind(usage: LabelUsage) {
            val properties = usage.properties
            val color = context.getNumberLabelColor(properties)

            binding.apply {
                labelName.text = properties.label
                labelIcon.applyColorFilter(color)
                labelUsage.text = if (usage.numbers == 0) {
                    context.getString(R.string.label_not_used)
                } else {
                    val numbers = context.resources.getQuantityString(R.plurals.label_numbers, usage.numbers, usage.numbers)
                    val ago = DateUtils.getRelativeTimeSpanString(
                        usage.lastUsed, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
                    )
                    "$numbers · $ago"
                }

                bindColors(properties)
                labelSuggest.bind(properties.suggest) { onChange(properties.copy(suggest = it)) }
                labelBlock.bind(properties.block) { onChange(properties.copy(block = it)) }
                bindHours(labelBlockHours, properties.block, properties.blockHours, NIGHT) {
                    onChange(properties.copy(blockHours = it))
                }
                labelRingOnly.bind(properties.ringOnly) { onChange(properties.copy(ringOnly = it)) }
                bindHours(labelRingOnlyHours, properties.ringOnly, properties.ringOnlyHours, WORKING_DAY) {
                    onChange(properties.copy(ringOnlyHours = it))
                }
                labelHighlight.bind(properties.highlight) { onChange(properties.copy(highlight = it)) }
                labelHide.bind(properties.hide) { onChange(properties.copy(hide = it)) }
            }
        }

        private fun CompoundButton.bind(checked: Boolean, onChecked: (Boolean) -> Unit) {
            setOnCheckedChangeListener(null)
            isChecked = checked
            setOnCheckedChangeListener { _, isChecked -> onChecked(isChecked) }
        }

        // a row of swatches, the first one for the default color
        private fun bindColors(properties: LabelProperties) {
            val size = context.resources.getDimensionPixelSize(R.dimen.color_swatch_size)
            val margin = context.resources.getDimensionPixelSize(R.dimen.medium_margin)
            val padding = context.resources.getDimensionPixelSize(R.dimen.small_margin)
            val palette = context.resources.getIntArray(R.array.label_colors).toList()

            binding.labelColors.removeAllViews()
            (listOf(null) + palette).forEachIndexed { index, swatch ->
                val swatchColor = swatch ?: context.getProperPrimaryColor()
                val selected = properties.color == swatch
                binding.labelColors.addView(ImageView(context).apply {
                    background = ContextCompat.getDrawable(context, R.drawable.circle_background)?.mutate()
                    background.applyColorFilter(swatchColor)
                    setPadding(padding)
                    if (selected) {
                        setImageResource(R.drawable.ic_check_vector)
                        applyColorFilter(swatchColor.getContrastColor())
                    }
                    contentDescription = if (swatch == null) {
                        context.getString(R.string.default_color)
                    } else {
                        context.getString(R.string.label_color, index)
                    }
                    TooltipCompat.setTooltipText(this, contentDescription)
                    isSelected = selected
                    setOnClickListener { if (!selected) onChange(properties.copy(color = swatch)) }
                })
                binding.labelColors.getChildAt(index).updateLayoutParams<LinearLayout.LayoutParams> {
                    width = size
                    height = size
                    marginEnd = margin
                }
            }
        }

        // shown only while [enabled]; unchecked means all day, and the times show [default] faded until changed
        private fun bindHours(
            hours: ItemLabelHoursBinding,
            enabled: Boolean,
            range: HourRange?,
            default: HourRange,
            onChange: (HourRange?) -> Unit,
        ) {
            hours.root.isVisible = enabled
            val shown = range ?: default
            hours.labelHoursEnabled.bind(range != null) { onChange(if (it) shown else null) }
            hours.labelHoursFrom.text = context.formatMinuteOfDay(shown.from)
            hours.labelHoursTo.text = context.formatMinuteOfDay(shown.to)
            listOf(hours.labelHoursFrom, hours.labelHoursTo).forEach {
                it.alpha = if (range != null) 1f else DISABLED_ALPHA
                it.setTextColor(context.getProperPrimaryColor())
            }
            hours.labelHoursFrom.setOnClickListener {
                pickTime(shown.from) { onChange(shown.copy(from = it)) }
            }
            hours.labelHoursTo.setOnClickListener {
                pickTime(shown.to) { onChange(shown.copy(to = it)) }
            }
        }

        private fun pickTime(minute: Int, onPicked: (Int) -> Unit) {
            TimePickerDialog(
                context,
                { _, hour, pickedMinute -> onPicked(hour * MINUTES_PER_HOUR + pickedMinute) },
                minute / MINUTES_PER_HOUR,
                minute % MINUTES_PER_HOUR,
                DateFormat.is24HourFormat(context)
            ).show()
        }
    }

    companion object {
        private const val MINUTES_PER_HOUR = 60
        private const val DISABLED_ALPHA = 0.4f

        // what the hours start at when first limited: blocking overnight, ringing during the working day
        private val NIGHT = hourRange(22, 7)
        private val WORKING_DAY = hourRange(9, 18)

        private val DIFF = object : DiffUtil.ItemCallback<LabelUsage>() {
            override fun areItemsTheSame(oldItem: LabelUsage, newItem: LabelUsage) =
                oldItem.properties.label.equals(newItem.properties.label, ignoreCase = true)

            override fun areContentsTheSame(oldItem: LabelUsage, newItem: LabelUsage) = oldItem == newItem
        }
    }
}
