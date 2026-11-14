package com.calltags.app.activities

import android.os.Bundle
import androidx.core.view.isVisible
import com.calltags.app.R
import com.calltags.app.adapters.LabelPropertiesAdapter
import com.calltags.app.databinding.ActivityLabelPropertiesBinding
import com.calltags.app.extensions.getColoredDrawableWithColor
import com.calltags.app.extensions.getProperTextColor
import com.calltags.app.extensions.viewBinding
import com.calltags.app.helpers.CallHistoryDb
import com.calltags.app.helpers.ensureBackgroundThread
import com.calltags.app.models.LabelProperties
import java.util.concurrent.Executors

/** Lists every number label, most used first, with what each one does. See [LabelProperties]. */
class LabelPropertiesActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityLabelPropertiesBinding::inflate)
    private val db by lazy { CallHistoryDb.getInstance(this) }
    private val adapter = LabelPropertiesAdapter(onChange = ::save)

    // one at a time, so quick changes to a label are saved in the order they were made
    private val saver = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        binding.apply {
            setupEdgeToEdge(
                padTopSystem = listOf(labelPropertiesAppbar),
                padBottomSystem = listOf(labelPropertiesList)
            )
            labelPropertiesToolbar.navigationIcon =
                resources.getColoredDrawableWithColor(R.drawable.ic_arrow_left_vector, getProperTextColor())
            labelPropertiesToolbar.setNavigationContentDescription(R.string.back)
            labelPropertiesToolbar.setNavigationOnClickListener { finish() }
            labelPropertiesList.adapter = adapter
        }
        load()
    }

    override fun onDestroy() {
        super.onDestroy()
        // lets queued saves finish
        saver.shutdown()
    }

    private fun load() {
        ensureBackgroundThread {
            val labels = db.getLabelUsage()
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                adapter.submitList(labels)
                binding.labelPropertiesPlaceholder.isVisible = labels.isEmpty()
            }
        }
    }

    // the order stays as it was while the screen is open, so cards don't jump around under the finger
    private fun save(properties: LabelProperties) {
        adapter.submitList(adapter.currentList.map {
            if (it.properties.label.equals(properties.label, ignoreCase = true)) it.copy(properties = properties) else it
        })
        saver.execute { db.setLabelProperties(properties) }
    }
}
