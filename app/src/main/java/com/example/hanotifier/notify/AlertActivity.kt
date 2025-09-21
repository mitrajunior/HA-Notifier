package com.example.hanotifier.notify

import android.os.Bundle
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.util.Patterns
import android.util.TypedValue
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationManagerCompat
import coil.compose.AsyncImage
import com.example.hanotifier.data.Action as NotificationAction
import com.example.hanotifier.ui.theme.AppTheme
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.LinkResolver
import java.util.ArrayList

class AlertActivity : ComponentActivity() {

  companion object {
    const val EXTRA_TITLE = "com.example.hanotifier.extra.TITLE"
    const val EXTRA_BODY = "com.example.hanotifier.extra.BODY"
    const val EXTRA_BODY_FORMAT = "com.example.hanotifier.extra.BODY_FORMAT"
    const val EXTRA_IMAGE = "com.example.hanotifier.extra.IMAGE"
    const val EXTRA_ACTIONS = "com.example.hanotifier.extra.ACTIONS"
    const val EXTRA_NOTIFICATION_ID = "com.example.hanotifier.extra.NOTIFICATION_ID"
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    window.addFlags(
      WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
    )

    val title = intent.getStringExtra(EXTRA_TITLE) ?: "Alerta"
    val body = intent.getStringExtra(EXTRA_BODY) ?: ""
    val bodyFormat = intent.getStringExtra(EXTRA_BODY_FORMAT)
    val image = intent.getStringExtra(EXTRA_IMAGE)
    @Suppress("UNCHECKED_CAST")
    val actions = (intent.getSerializableExtra(EXTRA_ACTIONS) as? ArrayList<NotificationAction>)?.toList().orEmpty()
    val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
    val appContext = applicationContext
    val activity = this

    setContent {
      AppTheme {
        AlertContent(
          title = title,
          body = body,
          bodyFormat = bodyFormat,
          image = image,
          actions = actions,
          onAction = { action ->
            ActionExecutor.execute(appContext, action) { result ->
              if (result.success) {
                if (notificationId != 0) {
                  NotificationManagerCompat.from(activity).cancel(notificationId)
                }
                activity.finish()
              }
            }
          },
          onLink = { url ->
            ActionExecutor.openLink(appContext, url) { result ->
              if (result.success) {
                if (notificationId != 0) {
                  NotificationManagerCompat.from(activity).cancel(notificationId)
                }
                activity.finish()
              }
            }
          },
          onAck = {
            if (notificationId != 0) {
              NotificationManagerCompat.from(activity).cancel(notificationId)
            }
            activity.finish()
          }
        )
      }
    }
  }
}

@Composable
private fun AlertContent(
  title: String,
  body: String,
  bodyFormat: String?,
  image: String?,
  actions: List<NotificationAction>,
  onAction: (NotificationAction) -> Unit,
  onLink: (String) -> Unit,
  onAck: () -> Unit,
) {
  val linkColor = MaterialTheme.colorScheme.primary
  val isMarkdown = bodyFormat?.equals("markdown", ignoreCase = true) == true
  val annotatedBody = if (!isMarkdown) {
    remember(body, linkColor) {
      val matcher = Patterns.WEB_URL.matcher(body)
      if (!matcher.find()) {
        AnnotatedString(body)
      } else {
        matcher.reset()
        buildAnnotatedString {
          var lastIndex = 0
          while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()
            append(body.substring(lastIndex, start))
            val url = matcher.group()
            val normalized = if (url.startsWith("http", true)) url else "https://$url"
            pushStringAnnotation(tag = "link", annotation = normalized)
            withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
              append(url)
            }
            pop()
            lastIndex = end
          }
          append(body.substring(lastIndex))
        }
      }
    }
  } else {
    null
  }

  val textColor = MaterialTheme.colorScheme.onSurface

  Surface(color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Card(
        modifier = Modifier
          .padding(24.dp)
          .fillMaxWidth(0.92f)
          .widthIn(max = 480.dp),
        shape = RoundedCornerShape(20.dp)
      ) {
        val scrollState = rememberScrollState()
        Column(
          modifier = Modifier
            .padding(24.dp)
            .verticalScroll(scrollState),
          verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          Text(title, style = MaterialTheme.typography.headlineSmall)
          if (body.isNotBlank()) {
            if (isMarkdown) {
              MarkdownMessage(
                markdown = body,
                textColor = textColor,
                linkColor = linkColor,
                onLink = onLink
              )
            } else {
              androidx.compose.foundation.text.ClickableText(
                text = annotatedBody ?: AnnotatedString(body),
                style = MaterialTheme.typography.bodyLarge.copy(color = textColor),
                onClick = { offset ->
                  annotatedBody
                    ?.getStringAnnotations("link", offset, offset)
                    ?.firstOrNull()
                    ?.let { onLink(it.item) }
                }
              )
            }
          }
          image?.takeIf { it.isNotBlank() }?.let { model ->
            AsyncImage(
              model = model,
              contentDescription = null,
              contentScale = ContentScale.Crop,
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 240.dp)
                .clip(RoundedCornerShape(16.dp))
            )
          }
          if (actions.isNotEmpty()) {
            Divider()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              actions.forEachIndexed { index, action ->
                val buttonModifier = Modifier.fillMaxWidth()
                if (index == 0) {
                  Button(modifier = buttonModifier, onClick = { onAction(action) }) {
                    Text(action.title)
                  }
                } else {
                  OutlinedButton(modifier = buttonModifier, onClick = { onAction(action) }) {
                    Text(action.title)
                  }
                }
              }
            }
          }
          Spacer(modifier = Modifier.heightIn(min = 4.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
          ) {
            OutlinedButton(onClick = onAck) { Text("Reconhecer") }
          }
        }
      }
    }
  }
}

@Composable
private fun MarkdownMessage(
  markdown: String,
  textColor: Color,
  linkColor: Color,
  modifier: Modifier = Modifier.fillMaxWidth(),
  onLink: (String) -> Unit,
) {
  val context = LocalContext.current
  val density = LocalDensity.current
  val onLinkState = rememberUpdatedState(onLink)
  val textColorInt = textColor.toArgb()
  val linkColorInt = linkColor.toArgb()
  val markwon = remember(context, linkColorInt, textColorInt) {
    Markwon.builder(context)
      .usePlugin(object : AbstractMarkwonPlugin() {
        override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
          builder.linkResolver(object : LinkResolver {
            override fun resolve(view: View, link: String) {
              onLinkState.value(link)
            }
          })
        }

        override fun configureTheme(builder: MarkwonTheme.Builder) {
          builder.linkColor(linkColorInt)
        }
      })
      .build()
  }
  val parsed = remember(markwon, markdown) { markwon.toMarkdown(markdown) }
  val bodyStyle = MaterialTheme.typography.bodyLarge

  AndroidView(
    modifier = modifier,
    factory = { ctx ->
      TextView(ctx).apply {
        setPadding(0, 0, 0, 0)
        highlightColor = 0
        movementMethod = LinkMovementMethod.getInstance()
        setTextColor(textColorInt)
        linkTextColor = linkColorInt
        applyTextStyle(this, bodyStyle, density)
        markwon.setParsedMarkdown(this, parsed)
      }
    },
    update = { view ->
      view.setTextColor(textColorInt)
      view.linkTextColor = linkColorInt
      applyTextStyle(view, bodyStyle, density)
      if (!TextUtils.equals(view.text, parsed)) {
        markwon.setParsedMarkdown(view, parsed)
      }
    }
  )
}

private fun applyTextStyle(view: TextView, style: TextStyle, density: Density) {
  if (style.fontSize.isSpecified) {
    view.setTextSize(TypedValue.COMPLEX_UNIT_SP, style.fontSize.value)
  }
  if (style.letterSpacing.isSpecified) {
    view.letterSpacing = style.letterSpacing.value
  }
  if (style.lineHeight.isSpecified) {
    val lineHeightPx = with(density) { style.lineHeight.toPx() }
    val fontMetrics = view.paint.fontMetrics
    val fontHeight = fontMetrics.descent - fontMetrics.ascent
    val spacingAdd = (lineHeightPx - fontHeight).coerceAtLeast(0f)
    view.setLineSpacing(spacingAdd, 1f)
  } else {
    view.setLineSpacing(0f, 1f)
  }
}
