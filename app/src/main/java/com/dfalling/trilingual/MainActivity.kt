package com.dfalling.trilingual

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.dfalling.trilingual.ui.MainViewModel
import com.dfalling.trilingual.ui.SettingsScreen
import com.dfalling.trilingual.ui.TranslateScreen
import com.dfalling.trilingual.ui.theme.TrilingualTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TrilingualTheme {
                // Two screens don't warrant a navigation library.
                var showSettings by rememberSaveable { mutableStateOf(false) }
                if (showSettings) {
                    BackHandler { showSettings = false }
                    SettingsScreen(viewModel, onBack = { showSettings = false })
                } else {
                    TranslateScreen(viewModel, onOpenSettings = { showSettings = true })
                }
            }
        }
    }
}
