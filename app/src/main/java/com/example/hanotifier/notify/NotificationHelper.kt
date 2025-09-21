package com.example.hanotifier.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.hanotifier.R
import com.example.hanotifier.data.Action
import com.example.hanotifier.data.DbProvider
import com.example.hanotifier.data.Payload
import com.example.hanotifier.data.Template
import io.noties.markwon.Markwon
import java.util.ArrayList
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

object NotificationHelper {
  const val CH_INFO = "info"
  const val CH_WARN = "warning"
  const val CH_CRIT = "critical"

  private const val TAG = "NotificationHelper"

  private val imageClient by lazy { OkHttpClient() }
  @Volatile private var markwonInstance: Markwon? = null

  private fun markwon(ctx: Context): Markwon {
    val cached = markwonInstance
    if (cached != null) return cached
    return synchronized(this) {
      markwonInstance ?: Markwon.builder(ctx.applicationContext).build().also { markwonInstance = it }
    }
  }

  fun canPostNotifications(ctx: Context): Boolean {
    val notificationsEnabled = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
      return notificationsEnabled
    }
    val granted = ContextCompat.checkSelfPermission(
      ctx,
      Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
    return granted && notificationsEnabled
  }

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
    val pr = (payload.priority ?: tpl?.priority ?: "info").lowercase(Locale.ROOT)
    val persist = payload.persistent ?: tpl?.persistent ?: false
    val popup = payload.popup ?: tpl?.popup ?: false
    return Triple(pr, persist, popup)
  }

  fun show(ctx: Context, payload: Payload) {
    if (!canPostNotifications(ctx)) return
    ensureChannels(ctx)

    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
      val tpl = resolveTemplate(ctx, payload)
      val (priority, persistent, popup) = merged(payload, tpl)
      val actions = payload.actions ?: emptyList()
      val notificationId = payload.collapseKey.hashCode()
      val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or (
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
      )

      val alertIntent = Intent(ctx, AlertActivity::class.java).apply {
        putExtra(AlertActivity.EXTRA_TITLE, payload.title)
        putExtra(AlertActivity.EXTRA_BODY, payload.body)
        putExtra(AlertActivity.EXTRA_BODY_FORMAT, payload.bodyFormat)
        putExtra(AlertActivity.EXTRA_IMAGE, payload.image)
        putExtra(AlertActivity.EXTRA_ACTIONS, ArrayList(actions))
        putExtra(AlertActivity.EXTRA_NOTIFICATION_ID, notificationId)
      }
      if (popup) {
        alertIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
      }
      val alertPI = PendingIntent.getActivity(ctx, notificationId, alertIntent, pendingFlags)

      val useMarkdown = payload.bodyFormat?.equals("markdown", ignoreCase = true) == true
      val bodyCharSequence: CharSequence? = when {
        payload.body.isBlank() -> null
        useMarkdown -> markwon(ctx).toMarkdown(payload.body)
        else -> payload.body
      }
      val summaryText = bodyCharSequence?.toString()?.lineSequence()?.firstOrNull()?.take(160)

      val imageBitmap = payload.image?.let { loadBitmap(ctx, it) }

      val channel = payload.channel ?: when (priority) {
        "critical" -> CH_CRIT
        "warning" -> CH_WARN
        else -> CH_INFO
      }

      val builder = NotificationCompat.Builder(ctx, channel)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(payload.title.ifBlank { ctx.getString(R.string.app_name) })
        .setPriority(
          when (priority) {
            "critical" -> NotificationCompat.PRIORITY_MAX
            "warning" -> NotificationCompat.PRIORITY_HIGH
            else -> NotificationCompat.PRIORITY_DEFAULT
          }
        )
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setOngoing(persistent)
        .setAutoCancel(!persistent)
        .setContentIntent(alertPI)
        .setCategory(if (priority == "critical") Notification.CATEGORY_ALARM else Notification.CATEGORY_MESSAGE)

      bodyCharSequence?.let { text ->
        builder.setContentText(summaryText ?: text)
        if (imageBitmap == null) {
          builder.setStyle(NotificationCompat.BigTextStyle().bigText(text))
        }
      }

      imageBitmap?.let { bitmap ->
        builder.setLargeIcon(bitmap)
        builder.setStyle(
          NotificationCompat.BigPictureStyle()
            .bigPicture(bitmap)
            .setSummaryText(bodyCharSequence ?: summaryText)
        )
      }

      payload.group?.let { builder.setGroup(it) }
      payload.timeout_sec?.takeIf { it > 0 }?.let { builder.setTimeoutAfter(it * 1_000L) }

      actions.forEachIndexed { index, action ->
        val actionIntent = Intent(ctx, ActionReceiver::class.java).apply {
          putExtra(ActionReceiver.EXTRA_ACTION, action)
          putExtra(ActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val actionPI = PendingIntent.getBroadcast(
          ctx,
          notificationId + index + 1,
          actionIntent,
          pendingFlags
        )
        builder.addAction(actionIcon(action), action.title, actionPI)
      }

      if (popup && priority == "critical") {
        builder.setFullScreenIntent(alertPI, true)
        builder.setOngoing(true)
      }

      NotificationManagerCompat.from(ctx).notify(notificationId, builder.build())

      if (popup) {
        withContext(Dispatchers.Main) {
          try {
            ctx.startActivity(alertIntent)
          } catch (t: Throwable) {
            Log.w(TAG, "Failed to launch alert activity", t)
          }
        }
      }
    }
  }

  private fun actionIcon(action: Action): Int {
    return when (action.type?.lowercase(Locale.ROOT)) {
      "url" -> android.R.drawable.ic_menu_view
      "ha_service" -> android.R.drawable.ic_media_play
      else -> android.R.drawable.ic_menu_send
    }
  }

  private suspend fun loadBitmap(ctx: Context, ref: String): Bitmap? = withContext(Dispatchers.IO) {
    try {
      val uri = runCatching { Uri.parse(ref) }.getOrNull()
      when (uri?.scheme?.lowercase(Locale.ROOT)) {
        "http", "https" -> fetchRemoteBitmap(uri.toString())
        "data" -> decodeDataUri(ref)
        "file" -> uri.path?.let { BitmapFactory.decodeFile(it) }
        "content" -> ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        else -> if (ref.startsWith('/')) BitmapFactory.decodeFile(ref) else null
      }
    } catch (t: Throwable) {
      Log.w(TAG, "Failed to load notification image", t)
      null
    }
  }

  private fun decodeDataUri(data: String): Bitmap? {
    val comma = data.indexOf(',')
    if (comma <= 0) return null
    return try {
      val base64 = data.substring(comma + 1)
      val bytes = Base64.decode(base64, Base64.DEFAULT)
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (t: Throwable) {
      Log.w(TAG, "Failed to decode inline image", t)
      null
    }
  }

  private fun fetchRemoteBitmap(url: String): Bitmap? {
    return try {
      val request = Request.Builder().url(url).build()
      imageClient.newCall(request).execute().use { resp ->
        if (!resp.isSuccessful) return null
        val bytes = resp.body?.bytes() ?: return null
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
      }
    } catch (t: Throwable) {
      Log.w(TAG, "Failed to download notification image", t)
      null
    }
  }
}
