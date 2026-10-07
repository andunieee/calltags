package com.calltags.app.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle

// The launcher entry point; it only forwards to MainActivity.
class SplashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
