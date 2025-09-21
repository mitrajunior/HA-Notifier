package com.example.hanotifier

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.painterResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.hanotifier.ui.screens.HomeScreen
import com.example.hanotifier.ui.screens.SettingsScreen
import com.example.hanotifier.ui.screens.TemplatesScreen

sealed class Route(val route: String) {
  data object Home: Route("home")
  data object Settings: Route("settings")
  data object Templates: Route("templates")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNav() {
  val nav = rememberNavController()
  val backStack by nav.currentBackStackEntryAsState()

  Scaffold(
    topBar = {
      CenterAlignedTopAppBar(
        title = { Text("HA Notifier") },
        actions = {
          IconButton(onClick = { nav.navigate(Route.Templates.route) }) {
            Icon(
              painterResource(android.R.drawable.ic_menu_add),
              contentDescription = "Templates"
            )
          }
          IconButton(onClick = { nav.navigate(Route.Settings.route) }) {
            Icon(
              painterResource(android.R.drawable.ic_menu_manage),
              contentDescription = "Definições"
            )
          }
        }
      )
    }
  ) { padding ->
    NavHost(navController = nav, startDestination = Route.Home.route) {
      composable(Route.Home.route) { HomeScreen(padding) }
      composable(Route.Settings.route) { SettingsScreen(padding) }
      composable(Route.Templates.route) { TemplatesScreen(padding) }
    }
  }
}
