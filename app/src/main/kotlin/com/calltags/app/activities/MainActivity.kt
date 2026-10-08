package com.calltags.app.activities

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.provider.Settings
import android.telephony.TelephonyManager
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.inputmethod.EditorInfo
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.calltags.app.R
import com.calltags.app.adapters.CallHistoryAdapter
import com.calltags.app.databinding.ActivityMainBinding
import com.calltags.app.dialogs.CallLabelsDialog
import com.calltags.app.extensions.adjustAlpha
import com.calltags.app.extensions.applyColorFilter
import com.calltags.app.extensions.darkenColor
import com.calltags.app.extensions.getColoredDrawableWithColor
import com.calltags.app.extensions.getContrastColor
import com.calltags.app.extensions.getProperBackgroundColor
import com.calltags.app.extensions.getProperPrimaryColor
import com.calltags.app.extensions.getProperTextColor
import com.calltags.app.extensions.hideKeyboard
import com.calltags.app.extensions.isDefaultDialer
import com.calltags.app.extensions.onTextChangeListener
import com.calltags.app.extensions.performHapticFeedback
import com.calltags.app.extensions.toast
import com.calltags.app.extensions.value
import com.calltags.app.extensions.viewBinding
import com.calltags.app.helpers.LOWER_ALPHA_INT
import com.calltags.app.helpers.REQUEST_CODE_SET_DEFAULT_DIALER
import com.calltags.app.helpers.ensureBackgroundThread
import com.calltags.app.extensions.addCharacter
import com.calltags.app.extensions.areMultipleSIMsAvailable
import com.calltags.app.extensions.boundingBox
import com.calltags.app.extensions.clearMissedCalls
import com.calltags.app.extensions.config
import com.calltags.app.extensions.disableKeyboard
import com.calltags.app.extensions.getKeyEvent
import com.calltags.app.extensions.handleFullScreenNotificationsPermission
import com.calltags.app.extensions.startCallIntent
import com.calltags.app.helpers.CallHistoryDb
import com.calltags.app.helpers.DIALPAD_TONE_LENGTH_MS
import com.calltags.app.helpers.TAB_DIALPAD
import com.calltags.app.helpers.TAB_HISTORY
import com.calltags.app.helpers.T9
import com.calltags.app.helpers.ToneGeneratorHelper
import com.calltags.app.models.Events
import com.calltags.app.models.LoggedCall
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import kotlin.math.roundToInt

class MainActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityMainBinding::inflate)
    private val db by lazy { CallHistoryDb.getInstance(this) }
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var historyAdapter: CallHistoryAdapter
    private var historyQuery = ""
    private var historyQueryGeneration = 0
    private lateinit var dialpadResultsAdapter: CallHistoryAdapter
    private var dialpadSearchGeneration = 0

    private var toneGeneratorHelper: ToneGeneratorHelper? = null
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
    private val pressedKeys = mutableSetOf<Char>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupEdgeToEdge(
            padTopSystem = listOf(binding.mainContent),
            padBottomImeAndSystem = listOf(binding.mainTabs)
        )

        EventBus.getDefault().register(this)
        toneGeneratorHelper = ToneGeneratorHelper(this, DIALPAD_TONE_LENGTH_MS)

        setupTabs()
        setupDialpad()
        setupHistory()

        if (isDefaultDialer()) {
            onBecameDefaultDialer()
        } else {
            launchSetDefaultDialerIntent()
        }

        if (savedInstanceState == null && !handleIntent(intent)) {
            selectTab(config.lastTab)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        applyColors()
        clearMissedCalls()
        syncAndRefreshHistory()
    }

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)
        mainHandler.removeCallbacksAndMessages(null)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == REQUEST_CODE_SET_DEFAULT_DIALER) {
            if (isDefaultDialer()) {
                onBecameDefaultDialer()
                syncAndRefreshHistory()
            } else {
                toast(R.string.default_phone_app_prompt)
            }
        }
    }

    override fun onBackPressedCompat(): Boolean {
        val search = binding.historySearch
        return if (binding.historyView.isVisible && search.value.isNotEmpty()) {
            search.setText("")
            true
        } else {
            false
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun refreshCallLog(@Suppress("UNUSED_PARAMETER") event: Events.RefreshCallLog) {
        // the system writes the call log entry shortly after the call is removed
        mainHandler.postDelayed({ syncAndRefreshHistory() }, CALL_LOG_WRITE_DELAY_MS)
    }

    private fun onBecameDefaultDialer() {
        if (!config.wasOverlaySnackbarConfirmed && !Settings.canDrawOverlays(this)) {
            Snackbar.make(binding.mainHolder, R.string.allow_displaying_over_other_apps, Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.ok) {
                    config.wasOverlaySnackbarConfirmed = true
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                }
                .setBackgroundTint(getProperBackgroundColor().darkenColor())
                .setTextColor(getProperTextColor())
                .setActionTextColor(getProperTextColor())
                .show()
        }

        handleFullScreenNotificationsPermission { granted ->
            if (!granted) {
                toast(R.string.notifications_disabled)
            }
        }
    }

    /** Returns true if the intent decided which tab to show. */
    private fun handleIntent(intent: Intent?): Boolean {
        intent ?: return false
        val action = intent.action
        return when {
            (action == Intent.ACTION_DIAL || action == Intent.ACTION_VIEW) && intent.scheme == "tel" -> {
                val number = Uri.decode(intent.dataString.orEmpty()).substringAfter("tel:")
                selectTab(TAB_DIALPAD)
                binding.dialpadInput.setText(number)
                binding.dialpadInput.setSelection(number.length)
                true
            }

            action == Intent.ACTION_DIAL -> {
                selectTab(TAB_DIALPAD)
                true
            }

            action == Intent.ACTION_VIEW && intent.type == CallLog.Calls.CONTENT_TYPE -> {
                selectTab(TAB_HISTORY)
                true
            }

            else -> false
        }
    }

    // region tabs

    private fun setupTabs() {
        binding.mainTabs.apply {
            addTab(newTab().setIcon(R.drawable.ic_dialpad_vector).setText(R.string.dialpad).setTag(TAB_DIALPAD))
            addTab(newTab().setIcon(R.drawable.ic_clock_vector).setText(R.string.history).setTag(TAB_HISTORY))
            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) = showTab(tab.tag as Int)
                override fun onTabUnselected(tab: TabLayout.Tab) {}
                override fun onTabReselected(tab: TabLayout.Tab) = showTab(tab.tag as Int)
            })
        }
    }

    private fun selectTab(tab: Int) {
        val index = if (tab == TAB_HISTORY) 1 else 0
        val tabView = binding.mainTabs.getTabAt(index) ?: return
        if (tabView.isSelected) showTab(tab) else tabView.select()
    }

    private fun showTab(tab: Int) {
        config.lastTab = tab
        binding.dialpadView.isVisible = tab == TAB_DIALPAD
        binding.historyView.isVisible = tab == TAB_HISTORY
        if (tab == TAB_DIALPAD) {
            hideKeyboard()
            binding.dialpadInput.requestFocus()
        }
    }

    // endregion

    // region dialpad

    private fun setupDialpad() {
        binding.dialpadWrapper.apply {
            if (config.hideDialpadNumbers) {
                arrayOf(
                    dialpad1Holder, dialpad2Holder, dialpad3Holder, dialpad4Holder, dialpad5Holder,
                    dialpad6Holder, dialpad7Holder, dialpad8Holder, dialpad9Holder
                ).forEach { it.isVisible = false }
                dialpadPlusHolder.isVisible = true
                dialpad0Holder.visibility = View.INVISIBLE
            }

            arrayOf(
                dialpad0Holder, dialpad1Holder, dialpad2Holder, dialpad3Holder, dialpad4Holder,
                dialpad5Holder, dialpad6Holder, dialpad7Holder, dialpad8Holder, dialpad9Holder,
                dialpadPlusHolder, dialpadAsteriskHolder, dialpadHashtagHolder
            ).forEach {
                it.background = ResourcesCompat.getDrawable(resources, R.drawable.pill_background, theme)
                it.background?.alpha = LOWER_ALPHA_INT
            }

            setupCharClick(dialpad1Holder, '1')
            setupCharClick(dialpad2Holder, '2')
            setupCharClick(dialpad3Holder, '3')
            setupCharClick(dialpad4Holder, '4')
            setupCharClick(dialpad5Holder, '5')
            setupCharClick(dialpad6Holder, '6')
            setupCharClick(dialpad7Holder, '7')
            setupCharClick(dialpad8Holder, '8')
            setupCharClick(dialpad9Holder, '9')
            setupCharClick(dialpad0Holder, '0')
            setupCharClick(dialpadPlusHolder, '+', longClickable = false)
            setupCharClick(dialpadAsteriskHolder, '*', longClickable = false)
            setupCharClick(dialpadHashtagHolder, '#', longClickable = false)
        }

        dialpadResultsAdapter = CallHistoryAdapter(
            onCallClick = { call ->
                binding.dialpadInput.setText(call.number)
                binding.dialpadInput.setSelection(call.number.length)
            },
            onDialClick = { call -> startCallIntent(call.number) },
            onLabelClick = { label ->
                selectTab(TAB_HISTORY)
                binding.historySearch.setText(label)
            }
        )
        binding.dialpadResults.adapter = dialpadResultsAdapter

        binding.apply {
            dialpadClearChar.setOnClickListener { clearChar(it) }
            dialpadClearChar.setOnLongClickListener { dialpadInput.setText(""); true }
            dialpadCallButton.setOnClickListener { callTypedNumber() }
            dialpadCallButton.setOnLongClickListener { callTypedNumberWithSimSelector() }
            dialpadInput.onTextChangeListener { dialpadValueChanged(it) }
            dialpadInput.disableKeyboard()
            dialpadInput.requestFocus()
        }
    }

    private fun dialpadValueChanged(text: String) {
        if (text.length > 8 && text.startsWith("*#*#") && text.endsWith("#*#*")) {
            val secretCode = text.substring(4, text.length - 4)
            if (isDefaultDialer()) {
                getSystemService(TelephonyManager::class.java)?.sendDialerSpecialCode(secretCode)
            } else {
                launchSetDefaultDialerIntent()
            }
            return
        }

        searchDialpad(text)
    }

    /** Lists the known numbers whose digits, name or labels match what's being typed. */
    private fun searchDialpad(text: String) {
        val generation = ++dialpadSearchGeneration
        val digits = CallHistoryDb.digitsOf(text)
        if (digits.length < MIN_DIGITS_FOR_DIALPAD_SEARCH) {
            showDialpadResults(digits, emptyList())
            return
        }

        ensureBackgroundThread {
            val calls = db.searchDialpad(text)
            runOnUiThread {
                if (generation == dialpadSearchGeneration && !isDestroyed) {
                    showDialpadResults(digits, calls)
                }
            }
        }
    }

    private fun showDialpadResults(digits: String, calls: List<LoggedCall>) {
        dialpadResultsAdapter.highlight = { label -> T9.matches(label, digits) }
        dialpadResultsAdapter.submitList(calls) {
            // the highlighted labels change with every digit, even for rows that stay
            dialpadResultsAdapter.notifyItemRangeChanged(0, dialpadResultsAdapter.itemCount)
            binding.dialpadResults.scrollToPosition(0)
        }
        binding.dialpadResults.isVisible = calls.isNotEmpty()
    }

    private fun clearChar(view: View) {
        binding.dialpadInput.dispatchKeyEvent(binding.dialpadInput.getKeyEvent(KeyEvent.KEYCODE_DEL))
        maybePerformDialpadHapticFeedback(view)
    }

    private fun clearInputWithDelay() {
        mainHandler.postDelayed({ binding.dialpadInput.setText("") }, CLEAR_INPUT_DELAY_MS)
    }

    private fun callTypedNumber() {
        val number = binding.dialpadInput.value
        if (number.isNotEmpty()) {
            startCallIntent(number)
            clearInputWithDelay()
        } else {
            // like most dialers, an empty call press brings back the last number
            ensureBackgroundThread {
                val last = db.search("", limit = 1).firstOrNull()?.number
                if (!last.isNullOrEmpty()) {
                    runOnUiThread { binding.dialpadInput.setText(last) }
                }
            }
        }
    }

    private fun callTypedNumberWithSimSelector(): Boolean {
        val number = binding.dialpadInput.value
        return if (areMultipleSIMsAvailable() && number.isNotEmpty()) {
            startCallIntent(recipient = number, forceSimSelector = true)
            clearInputWithDelay()
            true
        } else {
            false
        }
    }

    private fun startDialpadTone(char: Char) {
        if (config.dialpadBeeps) {
            pressedKeys.add(char)
            toneGeneratorHelper?.startTone(char)
        }
    }

    private fun stopDialpadTone(char: Char) {
        if (config.dialpadBeeps) {
            if (!pressedKeys.remove(char)) return
            if (pressedKeys.isEmpty()) {
                toneGeneratorHelper?.stopTone()
            } else {
                startDialpadTone(pressedKeys.last())
            }
        }
    }

    private fun maybePerformDialpadHapticFeedback(view: View?) {
        if (config.dialpadVibration) {
            view?.performHapticFeedback()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupCharClick(view: View, char: Char, longClickable: Boolean = true) {
        // long pressing 0 types a +
        val longPress = Runnable {
            stopDialpadTone(char)
            clearChar(view)
            binding.dialpadInput.addCharacter('+')
        }

        view.isClickable = true
        view.isLongClickable = true
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    binding.dialpadInput.addCharacter(char)
                    maybePerformDialpadHapticFeedback(view)
                    startDialpadTone(char)
                    if (longClickable && char == '0') {
                        mainHandler.removeCallbacks(longPress)
                        mainHandler.postDelayed(longPress, longPressTimeout)
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopDialpadTone(char)
                    mainHandler.removeCallbacks(longPress)
                }

                MotionEvent.ACTION_MOVE -> {
                    val inside = !event.rawX.isNaN() && !event.rawY.isNaN() &&
                        view.boundingBox.contains(event.rawX.roundToInt(), event.rawY.roundToInt())
                    if (!inside) {
                        stopDialpadTone(char)
                        mainHandler.removeCallbacks(longPress)
                    }
                }
            }
            false
        }
    }

    // endregion

    // region history

    private fun setupHistory() {
        historyAdapter = CallHistoryAdapter(
            onCallClick = { call ->
                hideKeyboard()
                CallLabelsDialog(
                    activity = this,
                    call = call,
                    onChanged = { refreshHistory() }
                )
            },
            onDialClick = { call -> startCallIntent(call.number) },
            onLabelClick = { label -> binding.historySearch.setText(label) }
        )
        binding.historyList.adapter = historyAdapter

        binding.historySearch.apply {
            onTextChangeListener {
                binding.historySearchClear.isVisible = it.isNotEmpty()
                refreshHistory()
            }
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    hideKeyboard()
                    true
                } else {
                    false
                }
            }
        }
        binding.historySearchClear.setOnClickListener { binding.historySearch.setText("") }
    }

    private fun syncAndRefreshHistory() {
        handlePermission(Manifest.permission.READ_CALL_LOG) { granted ->
            if (!granted) {
                refreshHistory()
                return@handlePermission
            }
            ensureBackgroundThread {
                db.importSystemCallLog(this)
                runOnUiThread {
                    refreshHistory()
                    searchDialpad(binding.dialpadInput.value)
                }
            }
        }
    }

    private fun refreshHistory() {
        val query = binding.historySearch.value
        val generation = ++historyQueryGeneration
        ensureBackgroundThread {
            val calls = db.search(query)
            runOnUiThread {
                if (generation == historyQueryGeneration && !isDestroyed) {
                    showHistory(query, calls)
                }
            }
        }
    }

    private fun showHistory(query: String, calls: List<LoggedCall>) {
        val queryChanged = historyQuery != query
        historyQuery = query
        val needle = query.trim()
        historyAdapter.highlight = { label -> needle.isNotEmpty() && label.contains(needle, ignoreCase = true) }
        historyAdapter.submitList(calls) {
            if (queryChanged) {
                historyAdapter.notifyItemRangeChanged(0, historyAdapter.itemCount)
                binding.historyList.scrollToPosition(0)
            }
        }
        binding.historyPlaceholder.isVisible = calls.isEmpty()
        binding.historyPlaceholder.setText(if (query.isBlank()) R.string.no_previous_calls else R.string.no_matching_calls)
    }

    // endregion

    private fun applyColors() {
        val textColor = getProperTextColor()
        val primaryColor = getProperPrimaryColor()
        val backgroundColor = getProperBackgroundColor()

        binding.mainHolder.setBackgroundColor(backgroundColor)

        binding.dialpadClearChar.applyColorFilter(textColor)
        binding.dialpadWrapper.apply {
            arrayOf(
                dialpad0Holder, dialpad1Holder, dialpad2Holder, dialpad3Holder, dialpad4Holder,
                dialpad5Holder, dialpad6Holder, dialpad7Holder, dialpad8Holder, dialpad9Holder,
                dialpadPlusHolder, dialpadAsteriskHolder, dialpadHashtagHolder
            ).forEach {
                it.background?.applyColorFilter(textColor)
                it.background?.alpha = DIALPAD_KEY_ALPHA
            }
        }
        binding.dialpadCallButton.setImageDrawable(
            resources.getColoredDrawableWithColor(R.drawable.ic_phone_vector, primaryColor.getContrastColor())
        )
        binding.dialpadCallButton.background.applyColorFilter(primaryColor)

        binding.historySearchHolder.background?.applyColorFilter(textColor.adjustAlpha(0.08f))
        binding.historySearchIcon.applyColorFilter(textColor)
        binding.historySearchClear.applyColorFilter(textColor)
        binding.historySearch.setHintTextColor(textColor.adjustAlpha(0.5f))

        binding.mainTabs.apply {
            setBackgroundColor(backgroundColor.darkenColor(2))
            setSelectedTabIndicatorColor(primaryColor)
            setTabTextColors(textColor, primaryColor)
            tabIconTint = android.content.res.ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_selected), intArrayOf()),
                intArrayOf(primaryColor, textColor)
            )
        }
    }

    companion object {
        private const val CALL_LOG_WRITE_DELAY_MS = 2000L
        private const val CLEAR_INPUT_DELAY_MS = 1000L
        private const val MIN_DIGITS_FOR_DIALPAD_SEARCH = 2
        private const val DIALPAD_KEY_ALPHA = 20
    }
}
