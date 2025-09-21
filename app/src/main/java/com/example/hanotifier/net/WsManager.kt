package com.example.hanotifier.net

import android.content.Context
import com.example.hanotifier.data.Payload
import com.example.hanotifier.notify.NotificationHelper
import okhttp3.OkHttpClient
import org.json.JSONObject

object WsManager {
  @Volatile private var running = false
  private var socket: HaWebSocket? = null

  fun buildWsUrl(base: String): String {
    val trimmed = base.trimEnd('/')
    return when {
      trimmed.startsWith("https://") -> trimmed.replaceFirst("https://", "wss://") + "/api/websocket"
      trimmed.startsWith("http://") -> trimmed.replaceFirst("http://", "ws://") + "/api/websocket"
      trimmed.startsWith("wss://") || trimmed.startsWith("ws://") -> trimmed
      else -> "wss://$trimmed/api/websocket"
    }
  }

  fun start(ctx: Context, lanBase: String?, wanBase: String?, token: String?, enabled: Boolean, preferLan: Boolean) {
    if (!enabled) { stop(); return }
    if (running) return
    running = true
    val base = (if (preferLan) lanBase?.takeIf { it.isNotBlank() } else wanBase?.takeIf { it.isNotBlank() })
      ?: (lanBase?.takeIf { it.isNotBlank() } ?: wanBase?.takeIf { it.isNotBlank() })
    if (base == null) { running = false; return }
    val url = buildWsUrl(base)
    val client = OkHttpClient()

    socket = HaWebSocket(url, token) { text ->
      try {
        val root = JSONObject(text)
        if (!root.optString("type").equals("event")) return@HaWebSocket
        val ev = root.getJSONObject("event")
        val data = ev.optJSONObject("data") ?: return@HaWebSocket

        val payload = Payload(
          title = data.optString("title", "Alerta"),
          body = data.optString("body", ""),
          priority = data.optString("priority", "info"),
          persistent = data.optBoolean("persistent", false),
          popup = data.optBoolean("popup", false),
          requireAck = data.optBoolean("requireAck", false),
          channel = data.optString("channel", null),
          sound = data.optString("sound", null),
          vibration = data.optJSONArray("vibration")?.let { arr ->
            MutableList(arr.length()) { i -> arr.getLong(i) }
          },
          actions = null,
          image = data.optString("image", null),
          timeout_sec = data.optInt("timeout_sec", 0),
          collapseKey = data.optString("collapse_key", (data.optString("title","")+data.optString("body","")).take(48)),
          group = data.optString("group", null)
        )
        NotificationHelper.show(ctx, payload)
      } catch (_: Throwable) {
        // ignore malformed
      }
    }
    socket?.connect(client)
  }

  fun stop() {
    running = false
    // OkHttp WebSocket will close when GC; for brevity we don't hold close handle here
    socket = null
  }
}
