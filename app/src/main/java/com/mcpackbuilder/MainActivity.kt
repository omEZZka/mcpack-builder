package com.mcpackbuilder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.mcpackbuilder.ui.AppRoot
import com.mcpackbuilder.ui.theme.MCPackTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as App).container
        setContent {
            MCPackTheme(container.theme) {
                AppRoot(container)
            }
        }
    }
}
