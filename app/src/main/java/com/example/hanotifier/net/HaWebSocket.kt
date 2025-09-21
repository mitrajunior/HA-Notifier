package com.example.hanotifier.net

import okhttp3.*
import okio.ByteString

class HaWebSocket(private val url: String, private val token: String?, private val onEvent: (String) -> Unit): WebSocketListener() {
  private var ws: WebSocket? = null
  private var msgId = 1

  fun connect(client: OkHttpClient = OkHttpClient()): WebSocket {
    val req = Request.Builder().url(url).build() // ex: wss://HA/api/websocket
    val socket = client.newWebSocket(req, this)
    ws = socket
    return socket
  }

  override fun onOpen(webSocket: WebSocket, response: Response) {
    token?.let { webSocket.send("{\"type\":\"auth\",\"access_token\":\"$it\"}") }
  }

  override fun onMessage(webSocket: WebSocket, text: String) {
    if (text.contains("auth_ok")) {
      val id = msgId++
      webSocket.send("{\"id\":$id,\"type\":\"subscribe_events\",\"event_type\":\"app_notify\"}")
    } else if (text.contains("event")) {
      onEvent(text)
    }
  }

  override fun onMessage(webSocket: WebSocket, bytes: ByteString) { }
  override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { }
}
