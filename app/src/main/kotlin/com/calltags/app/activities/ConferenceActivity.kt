package com.calltags.app.activities

import android.os.Bundle
import com.calltags.app.R
import com.calltags.app.adapters.ConferenceCallsAdapter
import com.calltags.app.databinding.ActivityConferenceBinding
import com.calltags.app.extensions.getColoredDrawableWithColor
import com.calltags.app.extensions.getProperTextColor
import com.calltags.app.extensions.viewBinding
import com.calltags.app.helpers.CallManager

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
