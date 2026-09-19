package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.example.ui.screen.S2SConversationScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.S2SViewModel

class MainActivity : ComponentActivity() {

  private val viewModel: S2SViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        S2SConversationScreen(
          viewModel = viewModel,
          modifier = Modifier.fillMaxSize()
        )
      }
    }
  }
}
