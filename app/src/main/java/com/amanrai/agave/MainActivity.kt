package com.amanrai.agave

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.amanrai.agave.ui.AgaveApp
import com.amanrai.agave.ui.theme.AgaveTheme

class MainActivity : ComponentActivity() {
    private val viewModel: AgaveViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AgaveTheme {
                AgaveApp(viewModel)
            }
        }
    }
}
