package org.androidlabs.filewall.ui

import android.os.Bundle
import androidx.fragment.app.FragmentActivity

open class SecurityFragmentActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        secureWindow()
    }
}
