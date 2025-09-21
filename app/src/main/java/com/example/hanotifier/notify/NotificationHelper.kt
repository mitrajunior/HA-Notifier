package com.example.hanotifier.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.hanotifier.R
import com.example.hanotifier.data.*
import kotlinx.coroutines.*

object NotificationHelper {
  const val CH_INFO = "info"
  const val CH_WARN = "warning"
  const val CH_CRIT = "critical"

  fun ensureChannels(ctx: Context) {
    val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun ch(id: String, name: String, imp: Int) = NotificationChannel(id, name, imp).apply {
      enableLights(true); enableVibration(true)
    }
    if (nm.getNotificationChannel(CH_INFO) == null)
      nm.createNotificationChannel(ch(CH_INFO, "Info", NotificationManager.IMPORTANCE_DEFAULT))
    if (nm.getNotificationChannel(CH_WARN) == null)
      nm.createNotificationChannel(ch(CH_WARN, "Atenção", NotificationManager.IMPORTANCE_HIGH))
    if (nm.getNotificationChannel(CH_CRIT) == null)
      nm.createNotificationChannel(ch(CH_CRIT, "Crítico", NotificationManager.IMPORTANCE_HIGH))
  }

  private suspend fun resolveTemplate(ctx: Context, payload: Payload): Template? {
    val dao = DbProvider.get(ctx).templates()
    payload.templateId?.let { return dao.get(it) }
    payload.templateName?.let { name -> return dao.byName(name) }
    return null
  }

  private fun merged(payload: Payload, tpl: Template?): Triple<String, Boolean, Boolean> {
    val pr = (payload.priority ?: tpl?.priority ?: "info").lowercase()
    val persist = payload.persistent ?: tpl?.persistent ?: false
    val popup = payload.popup ?: tpl?.popup ?: false
    return Triple(pr, persist, popup)
  }

  fun show(ctx: Context, payload: Payload) {
    ensureChannels(ctx)

    // Launch merging and notify
    CoroutineScope(Dispatchers.Default).launch {
      val tpl = resolveTemplate(ctx, payload)
      val (priority, persistent, popup) = merged(payload, tpl)

      val channel = when(priority) { "critical" -> CH_CRIT; "warning" -> CH_WARN; else -> CH_INFO }

      val fullIntent = Intent(ctx, AlertActivity::class.java).putExtra("title", payload.title).putExtra("body", payload.body)
      val fullPI = PendingIntent.getActivity(ctx, 0, fullIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

      val b = NotificationCompat.Builder(ctx, channel)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(payload.title)
        .setContentText(payload.body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(payload.body))
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setOngoing(persistent)
        .setAutoCancel(!persistent)

      if (priority == "critical" && (popup)) {
        b.setCategory(Notification.CATEGORY_ALARM)
        b.setFullScreenIntent(fullPI, true)
        b.setOngoing(true) // fallback persistente
      }

      NotificationManagerCompat.from(ctx).notify(payload.collapseKey.hashCode(), b.build())
    }
  }
}
