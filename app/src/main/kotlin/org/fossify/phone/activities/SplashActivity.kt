package org.fossify.phone.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle

// The launcher entry point. Kept as its own component so existing home screen shortcuts keep working.
class SplashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
