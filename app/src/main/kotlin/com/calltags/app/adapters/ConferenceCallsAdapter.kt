package com.calltags.app.adapters

import android.app.Activity
import android.telecom.Call
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.calltags.app.R
import com.calltags.app.databinding.ItemConferenceCallBinding
import com.calltags.app.extensions.applyColorFilter
import com.calltags.app.extensions.getProperTextColor
import com.calltags.app.extensions.hasCapability
import com.calltags.app.extensions.toast
import com.calltags.app.helpers.LOWER_ALPHA
import com.calltags.app.helpers.getCallContact

class ConferenceCallsAdapter(
    private val activity: Activity,
    private val data: ArrayList<Call>,
) : RecyclerView.Adapter<ConferenceCallsAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemConferenceCallBinding) : RecyclerView.ViewHolder(binding.root)

    override fun getItemCount(): Int = data.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemConferenceCallBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val call = data[position]
        val textColor = activity.getProperTextColor()
        holder.binding.apply {
            itemConferenceCallName.text = ""
            getCallContact(activity, call) { callContact ->
                root.post {
                    itemConferenceCallName.text =
                        callContact.name.ifEmpty { activity.getString(R.string.unknown_caller) }
                }
            }
            itemConferenceCallImage.applyColorFilter(textColor)

            val canSeparate = call.hasCapability(Call.Details.CAPABILITY_SEPARATE_FROM_CONFERENCE)
            itemConferenceCallSplit.isEnabled = canSeparate
            itemConferenceCallSplit.alpha = if (canSeparate) 1.0f else LOWER_ALPHA
            itemConferenceCallSplit.setColorFilter(textColor)
            itemConferenceCallSplit.setOnClickListener {
                call.splitFromConference()
                removeCall(holder)
            }
            itemConferenceCallSplit.setOnLongClickListener(::showDescription)

            val canDisconnect = call.hasCapability(Call.Details.CAPABILITY_DISCONNECT_FROM_CONFERENCE)
            itemConferenceCallEnd.isEnabled = canDisconnect
            itemConferenceCallEnd.alpha = if (canDisconnect) 1.0f else LOWER_ALPHA
            itemConferenceCallEnd.setColorFilter(textColor)
            itemConferenceCallEnd.setOnClickListener {
                call.disconnect()
                removeCall(holder)
            }
            itemConferenceCallEnd.setOnLongClickListener(::showDescription)
        }
    }

    private fun removeCall(holder: ViewHolder) {
        val position = holder.bindingAdapterPosition
        if (position == RecyclerView.NO_POSITION) return
        data.removeAt(position)
        notifyItemRemoved(position)
        if (data.size == 1) {
            activity.finish()
        }
    }

    private fun showDescription(view: View): Boolean {
        if (!view.contentDescription.isNullOrEmpty()) {
            activity.toast(view.contentDescription.toString())
        }
        return true
    }
}
