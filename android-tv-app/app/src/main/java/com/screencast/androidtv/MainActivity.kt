package com.screencast.androidtv

import android.os.Bundle
import androidx.fragment.app.FragmentActivity

/**
 * MainActivity – entry point for the Android TV app.
 *
 * Hosts [MainFragment] which shows the device-discovery UI and
 * manages the WebRTC receiver pipeline.
 */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.main_fragment_container, MainFragment())
                .commitNow()
        }
    }
}
