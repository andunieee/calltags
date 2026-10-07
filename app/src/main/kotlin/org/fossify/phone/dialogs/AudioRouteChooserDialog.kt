package org.fossify.phone.dialogs

import android.app.Activity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.fossify.phone.R
import org.fossify.phone.databinding.ItemSimpleListBinding
import org.fossify.phone.databinding.LayoutSimpleRecyclerViewBinding
import org.fossify.phone.extensions.applyColorFilter
import org.fossify.phone.extensions.getProperPrimaryColor
import org.fossify.phone.extensions.getProperTextColor
import org.fossify.phone.models.SimpleListItem

/** A bottom sheet list of choices whose items can be updated while it is open. */
class AudioRouteChooserDialog(
    activity: Activity,
    title: Int,
    items: List<SimpleListItem>,
    private val onItemClick: (SimpleListItem) -> Unit,
) {
    private val dialog = BottomSheetDialog(activity)
    private val adapter = Adapter(activity)

    val isShowing: Boolean get() = dialog.isShowing

    init {
        val margin = activity.resources.getDimensionPixelSize(R.dimen.activity_margin)
        val list = LayoutSimpleRecyclerViewBinding.inflate(activity.layoutInflater).root
        list.adapter = adapter
        adapter.items = items

        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            updatePadding(top = margin, bottom = margin)
            addView(TextView(activity).apply {
                setText(title)
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleLarge)
                setTextColor(activity.getProperTextColor())
                updatePadding(left = margin, right = margin, bottom = margin)
            })
            addView(list)
        }
        dialog.setContentView(content)
        dialog.show()
    }

    fun updateItems(newItems: List<SimpleListItem>) {
        adapter.items = newItems
    }

    fun dismiss() = dialog.dismiss()

    private inner class Adapter(private val activity: Activity) : RecyclerView.Adapter<Adapter.ViewHolder>() {
        var items: List<SimpleListItem> = emptyList()
            set(value) {
                field = value
                @Suppress("NotifyDataSetChanged")
                notifyDataSetChanged()
            }

        inner class ViewHolder(val binding: ItemSimpleListBinding) : RecyclerView.ViewHolder(binding.root)

        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ViewHolder(ItemSimpleListBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            val color = if (item.selected) activity.getProperPrimaryColor() else activity.getProperTextColor()
            holder.binding.apply {
                bottomSheetItemTitle.setText(item.textRes)
                bottomSheetItemTitle.setTextColor(color)
                bottomSheetItemIcon.isVisible = item.imageRes != null
                item.imageRes?.let { bottomSheetItemIcon.setImageResource(it) }
                bottomSheetItemIcon.applyColorFilter(color)
                bottomSheetSelectedIcon.isVisible = item.selected
                bottomSheetSelectedIcon.applyColorFilter(color)
                root.setOnClickListener {
                    onItemClick(item)
                    dialog.dismiss()
                }
            }
        }
    }
}
