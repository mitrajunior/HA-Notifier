package com.example.hanotifier

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.hanotifier.ui.theme.AppTheme
import com.example.hanotifier.AppNav

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent { AppTheme { AppNav() } }
  }
}
