package org.fossify.phone.activities

import android.content.Intent
import android.os.Bundle
import org.fossify.commons.activities.BaseSplashActivity
import org.fossify.commons.extensions.baseConfig
import org.fossify.commons.helpers.SIDELOADING_FALSE

class SplashActivity : BaseSplashActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Fossify Commons flags the app as "corrupt" and links to fossify.org when a drawable it
        // probes for has been stripped by resource shrinking, which happens in our release builds.
        // Mark the check as passed so BaseSplashActivity never runs it or shows that dialog.
        baseConfig.appSideloadingStatus = SIDELOADING_FALSE
        super.onCreate(savedInstanceState)
    }

    override fun initActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
