package org.fossify.phone.activities

import android.os.Bundle
import org.fossify.phone.R
import org.fossify.phone.adapters.ConferenceCallsAdapter
import org.fossify.phone.databinding.ActivityConferenceBinding
import org.fossify.phone.extensions.getColoredDrawableWithColor
import org.fossify.phone.extensions.getProperTextColor
import org.fossify.phone.extensions.viewBinding
import org.fossify.phone.helpers.CallManager

class ConferenceActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityConferenceBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        binding.apply {
            setupEdgeToEdge(
                padTopSystem = listOf(conferenceAppbar),
                padBottomSystem = listOf(conferenceList)
            )
            conferenceToolbar.navigationIcon =
                resources.getColoredDrawableWithColor(R.drawable.ic_arrow_left_vector, getProperTextColor())
            conferenceToolbar.setNavigationContentDescription(R.string.back)
            conferenceToolbar.setNavigationOnClickListener { finish() }
            val calls = ArrayList(CallManager.getConferenceCalls())
            conferenceList.adapter = ConferenceCallsAdapter(this@ConferenceActivity, calls)
        }
    }
}
