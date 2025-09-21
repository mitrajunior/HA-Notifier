package com.example.hanotifier.notify

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.hanotifier.ui.theme.AppTheme

class AlertActivity: ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    window.addFlags(
      WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
      WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
    )
    val title = intent.getStringExtra("title") ?: "Alerta"
    val body = intent.getStringExtra("body") ?: ""
    setContent { AppTheme { AlertContent(title, body) } }
  }
}

@Composable
private fun AlertContent(title: String, body: String) {
  Surface {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Card(modifier = Modifier.padding(24.dp)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(title, style = MaterialTheme.typography.titleLarge)
          Text(body)
          Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val ctx = LocalContext.current
            Button(onClick = { /* TODO: executar ação HA se necessário */ }) { Text("Ação") }
            OutlinedButton(onClick = { (ctx as? ComponentActivity)?.finish() }) { Text("Reconhecer") }
          }
        }
      }
    }
  }
}
