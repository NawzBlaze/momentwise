package com.nawzblaze.momentwise.fixture

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

/** Separate package so a usage-event test can open something that is not Momentwise. */
class FixtureActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { text = "Fixture target" })
    }
}
