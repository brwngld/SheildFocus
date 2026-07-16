package com.shieldfocus.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.shieldfocus.android.ui.ShieldFocusApp
import com.shieldfocus.android.ui.theme.ShieldFocusTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ShieldFocusTheme {
                ShieldFocusApp()
            }
        }
    }
}
