// MainActivity.kt
package com.example.hanotifier

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.example.hanotifier.ui.theme.AppTheme
import com.example.hanotifier.data.Prefs
import com.example.hanotifier.net.WsManager
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // 🚀 Auto-start WS usando prefs guardadas
    val prefs = Prefs(this)
    lifecycleScope.launch {
      combine(
        prefs.lanUrl,
        prefs.wanUrl,
        prefs.token,
        prefs.wsEnabled,
        prefs.wsPreferLan
      ) { lan, wan, token, enabled, preferLan ->
        WsManager.start(this@MainActivity, lan, wan, token, enabled, preferLan)
      }.collect { /* no-op */ }
    }

    setContent { AppTheme { AppNav() } }
  }
}
