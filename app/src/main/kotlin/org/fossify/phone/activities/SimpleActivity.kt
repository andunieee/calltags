package org.fossify.phone.activities

import android.os.Bundle
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.phone.R
import org.fossify.phone.extensions.applyBrandTheme

open class SimpleActivity : BaseSimpleActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // must run before commons reads the colors in super.onCreate
        applyBrandTheme()
        super.onCreate(savedInstanceState)
    }

    // commons indexes this list by the chosen icon color; there is only one icon
    override fun getAppIconIDs() = ArrayList(List(APP_ICON_COLOR_COUNT) { R.mipmap.ic_launcher })

    override fun getAppLauncherName() = getString(R.string.app_launcher_name)

    override fun getRepositoryName() = "calltags"

    companion object {
        private const val APP_ICON_COLOR_COUNT = 19
    }
}
