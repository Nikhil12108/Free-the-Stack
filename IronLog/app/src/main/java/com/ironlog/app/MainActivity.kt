package com.ironlog.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironlog.app.theme.IronLogTheme
import com.ironlog.app.theme.isDark
import com.ironlog.app.ui.MainNavigation
import com.ironlog.app.ui.components.LocalContainer
import com.ironlog.app.ui.components.LocalSettings
import com.ironlog.app.ui.components.grain
import com.ironlog.app.workout.Notifications

class MainActivity : ComponentActivity() {
  private val container
    get() = (application as IronLogApp).container

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    handle(intent)
    enableEdgeToEdge(barStyle(dark = true), barStyle(dark = true))
    setContent {
      val settings by container.settings.settings.collectAsStateWithLifecycle()
      val dark = settings.theme.isDark()
      // The app picks its own theme, so system-bar icons must follow it rather than the phone's mode.
      DisposableEffect(dark) {
        enableEdgeToEdge(barStyle(dark), barStyle(dark))
        onDispose {}
      }
      CompositionLocalProvider(LocalContainer provides container, LocalSettings provides settings) {
        IronLogTheme(settings.theme) {
          Surface(modifier = Modifier.fillMaxSize().grain(), color = MaterialTheme.colorScheme.background) { MainNavigation() }
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    handle(intent)
  }

  private fun barStyle(dark: Boolean) =
    if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)

  private fun handle(intent: Intent?) {
    if (intent?.getBooleanExtra(Notifications.EXTRA_OPEN_WORKOUT, false) == true) container.openWorkoutRequest.value = true
  }
}
