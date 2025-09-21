package com.example.hanotifier.net

import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.*
import okio.ByteString

class HaWebSocket(
  private val url: String,
  private val token: String?,
  private val onEvent: (String) -> Unit,
  private val onDisconnect: (Throwable?) -> Unit
) : WebSocketListener() {
  private var ws: WebSocket? = null
  private var msgId = 1
  private val closed = AtomicBoolean(false)

  fun connect(client: OkHttpClient = OkHttpClient()): WebSocket {
    val req = Request.Builder().url(url).build()
    val socket = client.newWebSocket(req, this)
    ws = socket
    return socket
  }

  fun close() {
    if (closed.compareAndSet(false, true)) {
      ws?.close(1000, "bye")
    } else {
      ws?.cancel()
    }
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

  override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
    if (closed.compareAndSet(false, true)) {
      onDisconnect(null)
    }
  }

  override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
    if (closed.compareAndSet(false, true)) {
      onDisconnect(t)
    }
  }
}
