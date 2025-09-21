// MainActivity.kt
package com.example.hanotifier

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.hanotifier.net.WsService
import com.example.hanotifier.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    WsService.start(this)
    setContent { AppTheme { AppNav() } }
  }
}
