package com.example.hanotifier.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.hanotifier.data.Prefs
import com.example.hanotifier.net.WsManager
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(padding: PaddingValues) {
  val ctx = LocalContext.current
  val prefs = remember { Prefs(ctx) }
  val scope = rememberCoroutineScope()

  val lanUrl by prefs.lanUrl.collectAsState(initial = "")
  val wanUrl by prefs.wanUrl.collectAsState(initial = "")
  val token by prefs.token.collectAsState(initial = "")
  val fullScreen by prefs.fullScreen.collectAsState(initial = true)
  val persistent by prefs.persistent.collectAsState(initial = true)
  val wsEnabled by prefs.wsEnabled.collectAsState(initial = false)
  val wsPreferLan by prefs.wsPreferLan.collectAsState(initial = true)

  // (Re)inicia WS quando configurações mudam
  LaunchedEffect(lanUrl, wanUrl, token, wsEnabled, wsPreferLan) {
    WsManager.start(ctx, lanUrl, wanUrl, token, wsEnabled, wsPreferLan)
  }

  Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text("Ligação ao Home Assistant", style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(lanUrl, { v -> scope.launch { prefs.setLanUrl(v) } }, label = { Text("URL LAN (ex: http://ha.local:8123)") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(wanUrl, { v -> scope.launch { prefs.setWanUrl(v) } }, label = { Text("URL Externa (Nabu Casa/reverse proxy)") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(token, { v -> scope.launch { prefs.setToken(v) } }, label = { Text("Long-Lived Access Token (para WebSocket/REST)") }, modifier = Modifier.fillMaxWidth())

    Divider()
    Text("Comportamento de notificações", style = MaterialTheme.typography.titleMedium)
    Row {
      Switch(checked = fullScreen, onCheckedChange = { scope.launch { prefs.setFullScreen(it) } })
      Spacer(Modifier.width(8.dp)); Text("Usar popup (full-screen) para crítico")
    }
    Row {
      Switch(checked = persistent, onCheckedChange = { scope.launch { prefs.setPersistent(it) } })
      Spacer(Modifier.width(8.dp)); Text("Tornar crítico persistente")
    }

    Divider()
    Text("WebSocket (LAN/Externo)", style = MaterialTheme.typography.titleMedium)
    Row {
      Switch(checked = wsEnabled, onCheckedChange = { scope.launch { prefs.setWsEnabled(it) } })
      Spacer(Modifier.width(8.dp)); Text("Ativar subscrição WebSocket (app_notify)")
    }
    Row {
      Switch(checked = wsPreferLan, onCheckedChange = { scope.launch { prefs.setWsPreferLan(it) } })
      Spacer(Modifier.width(8.dp)); Text("Preferir URL LAN quando disponível")
    }

    Spacer(Modifier.height(16.dp))
    Text("Dica: toca no ícone ⚙️ no topo para voltar aqui sempre que precisares.")
  }
}
